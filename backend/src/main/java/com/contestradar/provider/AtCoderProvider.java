package com.contestradar.provider;

import com.contestradar.contest.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AtCoderProvider implements ContestProvider {

    private static final Logger log = LoggerFactory.getLogger(AtCoderProvider.class);
    private static final Pattern CONTEST_LINK = Pattern.compile("href=\"/contests/([a-zA-Z0-9_-]+)\"");
    private static final Pattern TIME_TAG = Pattern.compile("<time[^>]*>([^<]+)</time>");
    private static final Pattern DURATION_CELL = Pattern.compile("<td[^>]*>\\s*(\\d{2,3}:\\d{2})\\s*</td>");
    private static final Pattern TABLE_ROW = Pattern.compile("<tr>(.*?)</tr>", Pattern.DOTALL);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssZ");

    private final RestClient restClient;

    public AtCoderProvider(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl("https://atcoder.jp").build();
    }

    @Override
    public Platform platform() { return Platform.ATCODER; }

    @Override
    public List<ContestData> fetchContests() {
        log.info("Fetching contests from AtCoder website");
        try {
            String html = restClient.get()
                    .uri("/contests/?lang=en")
                    .retrieve()
                    .body(String.class);
            if (html == null) { log.warn("AtCoder returned null HTML"); return List.of(); }
            return parseUpcomingContests(html);
        } catch (Exception e) {
            log.error("Failed to fetch contests from AtCoder: {}", e.getMessage());
            return List.of();
        }
    }

    List<ContestData> parseUpcomingContests(String html) {
        List<ContestData> result = new ArrayList<>();

        int sectionStart = html.indexOf("Upcoming Contests");
        if (sectionStart == -1) {
            log.warn("Could not find 'Upcoming Contests' section");
            return result;
        }

        // The upcoming section ends at the next major section header
        int sectionEnd = findNextSection(html, sectionStart + 20);
        String section = html.substring(sectionStart, sectionEnd);

        // Extract each table row
        Matcher rowMatcher = TABLE_ROW.matcher(section);
        while (rowMatcher.find()) {
            String row = rowMatcher.group(1);
            try {
                ContestData cd = parseRow(row);
                if (cd != null) result.add(cd);
            } catch (Exception e) {
                log.debug("Skipped unparseable row: {}", e.getMessage());
            }
        }

        log.info("Fetched {} upcoming contests from AtCoder", result.size());
        return result;
    }

    private ContestData parseRow(String row) {
        // Must have a contest link
        Matcher linkMatcher = CONTEST_LINK.matcher(row);
        if (!linkMatcher.find()) return null;
        String contestId = linkMatcher.group(1);

        // Extract title: text between >...</a> after the contest link
        int linkEnd = linkMatcher.end();
        int titleStart = row.indexOf('>', linkEnd);
        int titleEnd = row.indexOf("</a>", linkEnd);
        if (titleStart == -1 || titleEnd == -1) return null;
        String title = row.substring(titleStart + 1, titleEnd).trim();
        if (title.isEmpty()) return null;

        // Extract time from <time> tag
        Matcher timeMatcher = TIME_TAG.matcher(row);
        if (!timeMatcher.find()) return null;
        String timeStr = timeMatcher.group(1).trim();

        Instant startTime;
        try {
            startTime = ZonedDateTime.parse(timeStr, DT_FMT).toInstant();
        } catch (DateTimeParseException e) {
            log.debug("Could not parse time '{}': {}", timeStr, e.getMessage());
            return null;
        }

        // Extract duration
        Matcher durMatcher = DURATION_CELL.matcher(row);
        long durationSec = 7200; // default 2h
        if (durMatcher.find()) {
            String[] parts = durMatcher.group(1).split(":");
            durationSec = Long.parseLong(parts[0]) * 3600 + Long.parseLong(parts[1]) * 60;
        }

        Instant endTime = startTime.plusSeconds(durationSec);

        return new ContestData(
                contestId, Platform.ATCODER, title,
                startTime, endTime, durationSec,
                "https://atcoder.jp/contests/" + contestId,
                "https://atcoder.jp/contests/" + contestId,
                null, null
        );
    }

    private int findNextSection(String html, int fromIndex) {
        // Look for next <h3 or <h2 or a "Recent Contests" / "Permanent Contests" header
        String[] markers = {"Recent Contests", "Permanent Contests", "Ended Contest"};
        int earliest = html.length();
        for (String marker : markers) {
            int idx = html.indexOf(marker, fromIndex);
            if (idx != -1 && idx < earliest) earliest = idx;
        }
        return earliest;
    }
}

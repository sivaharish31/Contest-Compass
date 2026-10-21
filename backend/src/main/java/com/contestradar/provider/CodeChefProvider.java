package com.contestradar.provider;

import com.contestradar.contest.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class CodeChefProvider implements ContestProvider {

    private static final Logger log = LoggerFactory.getLogger(CodeChefProvider.class);
    private static final DateTimeFormatter CC_DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy  HH:mm:ss");
    private static final ZoneId CC_ZONE = ZoneId.of("Asia/Kolkata");

    private final RestClient restClient;

    public CodeChefProvider(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl("https://www.codechef.com")
                .build();
    }

    @Override
    public Platform platform() {
        return Platform.CODECHEF;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ContestData> fetchContests() {
        log.info("Fetching contests from CodeChef API");
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/api/list/contests/all?sort_by=START&sorting_order=asc&offset=0&mode=all")
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                log.warn("CodeChef API returned null");
                return List.of();
            }

            List<ContestData> result = new ArrayList<>();

            // Process future contests
            List<Map<String, Object>> future = (List<Map<String, Object>>) response.get("future_contests");
            if (future != null) {
                for (Map<String, Object> c : future) {
                    ContestData cd = parseCodeChefContest(c);
                    if (cd != null) result.add(cd);
                }
            }

            // Process present (live) contests
            List<Map<String, Object>> present = (List<Map<String, Object>>) response.get("present_contests");
            if (present != null) {
                for (Map<String, Object> c : present) {
                    ContestData cd = parseCodeChefContest(c);
                    if (cd != null) result.add(cd);
                }
            }

            log.info("Fetched {} upcoming/live contests from CodeChef", result.size());
            return result;
        } catch (Exception e) {
            log.error("Failed to fetch contests from CodeChef: {}", e.getMessage());
            return List.of();
        }
    }

    private ContestData parseCodeChefContest(Map<String, Object> c) {
        try {
            String code = (String) c.get("contest_code");
            String name = (String) c.get("contest_name");

            if (code == null) return null;

            // Prefer ISO dates if available
            String startIso = (String) c.get("contest_start_date_iso");
            String endIso = (String) c.get("contest_end_date_iso");

            Instant start = null;
            Instant end = null;

            if (startIso != null) {
                start = OffsetDateTime.parse(startIso).toInstant();
            } else {
                String startStr = (String) c.get("contest_start_date");
                if (startStr == null) return null;
                start = parseCodeChefDate(startStr.replaceAll("\\s+", " ").trim());
            }

            if (endIso != null) {
                end = OffsetDateTime.parse(endIso).toInstant();
            } else {
                String endStr = (String) c.get("contest_end_date");
                if (endStr != null) {
                    end = parseCodeChefDate(endStr.replaceAll("\\s+", " ").trim());
                }
            }

            if (start == null) return null;

            long durationSec = 0;
            if (end != null) {
                durationSec = end.getEpochSecond() - start.getEpochSecond();
            } else {
                Object durObj = c.get("contest_duration");
                if (durObj != null) {
                    durationSec = Long.parseLong(durObj.toString()) * 60; // minutes to seconds
                    end = start.plusSeconds(durationSec);
                }
            }

            return new ContestData(
                    code,
                    Platform.CODECHEF,
                    name,
                    start, end, durationSec > 0 ? durationSec : null,
                    "https://www.codechef.com/" + code,
                    "https://www.codechef.com/" + code, // registration is on the contest page
                    null, null
            );
        } catch (Exception e) {
            log.warn("Failed to parse CodeChef contest: {}", e.getMessage());
            return null;
        }
    }

    private Instant parseCodeChefDate(String dateStr) {
        try {
            // Try parsing with standard format "d MMM yyyy HH:mm:ss"
            LocalDateTime ldt = LocalDateTime.parse(dateStr, CC_DATE_FMT);
            return ldt.atZone(CC_ZONE).toInstant();
        } catch (Exception e) {
            log.warn("Failed to parse CodeChef date '{}': {}", dateStr, e.getMessage());
            return null;
        }
    }
}

package com.contestradar.provider;

import com.contestradar.contest.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class LeetCodeProvider implements ContestProvider {

    private static final Logger log = LoggerFactory.getLogger(LeetCodeProvider.class);

    private final RestClient restClient;

    public LeetCodeProvider(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl("https://leetcode.com")
                .build();
    }

    @Override
    public Platform platform() {
        return Platform.LEETCODE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ContestData> fetchContests() {
        log.info("Fetching contests from LeetCode GraphQL API");
        try {
            Map<String, Object> body = Map.of(
                    "query", "{ allContests { title startTime duration titleSlug } }"
            );

            Map<String, Object> response = restClient.post()
                    .uri("/graphql")
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response == null || response.get("data") == null) {
                log.warn("LeetCode API returned null data");
                return List.of();
            }

            Map<String, Object> data = (Map<String, Object>) response.get("data");
            List<Map<String, Object>> contests = (List<Map<String, Object>>) data.get("allContests");
            if (contests == null) return List.of();

            Instant now = Instant.now();
            Instant cutoff = now.minusSeconds(86400); // include recently ended
            Instant maxFuture = now.plusSeconds(60L * 86400); // 60 days

            List<ContestData> result = new ArrayList<>();
            for (Map<String, Object> c : contests) {
                long startEpoch = ((Number) c.get("startTime")).longValue();
                long durationSec = ((Number) c.get("duration")).longValue();
                String title = (String) c.get("title");
                String slug = (String) c.get("titleSlug");

                Instant start = Instant.ofEpochSecond(startEpoch);
                Instant end = start.plusSeconds(durationSec);

                // Only include contests within our window
                if (end.isBefore(cutoff)) continue;
                if (start.isAfter(maxFuture)) continue;

                result.add(new ContestData(
                        slug,
                        Platform.LEETCODE,
                        title,
                        start, end, durationSec,
                        "https://leetcode.com/contest/" + slug,
                        null, // LeetCode doesn't have a separate registration page
                        null, null
                ));
            }

            log.info("Fetched {} recent/upcoming contests from LeetCode", result.size());
            return result;
        } catch (Exception e) {
            log.error("Failed to fetch contests from LeetCode: {}", e.getMessage());
            return List.of();
        }
    }
}

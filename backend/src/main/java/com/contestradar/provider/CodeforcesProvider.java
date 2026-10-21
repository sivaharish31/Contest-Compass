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
public class CodeforcesProvider implements ContestProvider {

    private static final Logger log = LoggerFactory.getLogger(CodeforcesProvider.class);
    private static final String API_URL = "https://codeforces.com/api/contest.list";

    private final RestClient restClient;

    public CodeforcesProvider(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl(API_URL)
                .build();
    }

    @Override
    public Platform platform() {
        return Platform.CODEFORCES;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ContestData> fetchContests() {
        log.info("Fetching contests from Codeforces API");
        try {
            Map<String, Object> response = restClient.get()
                    .retrieve()
                    .body(Map.class);

            if (response == null || !"OK".equals(response.get("status"))) {
                log.warn("Codeforces API returned non-OK status: {}", response);
                return List.of();
            }

            List<Map<String, Object>> contests = (List<Map<String, Object>>) response.get("result");
            if (contests == null) return List.of();

            List<ContestData> result = new ArrayList<>();
            for (Map<String, Object> c : contests) {
                String phase = (String) c.get("phase");
                if (!"BEFORE".equals(phase) && !"CODING".equals(phase)) continue;

                long startTimeSec = ((Number) c.get("startTimeSeconds")).longValue();
                long durationSec = ((Number) c.get("durationSeconds")).longValue();
                int contestId = ((Number) c.get("id")).intValue();

                Instant start = Instant.ofEpochSecond(startTimeSec);
                Instant end = start.plusSeconds(durationSec);

                result.add(new ContestData(
                        String.valueOf(contestId),
                        Platform.CODEFORCES,
                        (String) c.get("name"),
                        start, end, durationSec,
                        "https://codeforces.com/contest/" + contestId,
                        "https://codeforces.com/contestRegistration/" + contestId,
                        null, null
                ));
            }

            log.info("Fetched {} upcoming/live contests from Codeforces", result.size());
            return result;
        } catch (Exception e) {
            log.error("Failed to fetch contests from Codeforces: {}", e.getMessage());
            return List.of();
        }
    }
}

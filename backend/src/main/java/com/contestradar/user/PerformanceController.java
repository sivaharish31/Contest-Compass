package com.contestradar.user;

import com.contestradar.auth.AuthUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/performance")
public class PerformanceController {

    private final ConnectedProfileRepository profileRepository;
    private final RatingHistoryRepository historyRepository;
    private final RecommendationService recommendationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PerformanceController(ConnectedProfileRepository profileRepository,
                                  RatingHistoryRepository historyRepository,
                                  RecommendationService recommendationService) {
        this.profileRepository = profileRepository;
        this.historyRepository = historyRepository;
        this.recommendationService = recommendationService;
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        Long userId = AuthUtil.requireUserId();
        List<ConnectedProfile> profiles = profileRepository.findByUserId(userId);

        List<Map<String, Object>> platformStats = new ArrayList<>();
        long totalContests = 0;
        long contestsLast30Days = 0;
        Instant thirtyDaysAgo = Instant.now().minusSeconds(30L * 86400);

        String mostActivePlatform = null;
        long maxPlatformContests = 0;
        Instant mostRecentSync = null;

        for (ConnectedProfile p : profiles) {
            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put("platform", p.getPlatform());
            stat.put("handle", p.getHandle());
            stat.put("verified", p.getVerified());
            stat.put("rating", p.getRating());
            stat.put("maxRating", p.getMaxRating());
            stat.put("rankTitle", p.getRankTitle());
            stat.put("problemsSolved", p.getProblemsSolved());
            stat.put("easySolved", p.getEasySolved());
            stat.put("mediumSolved", p.getMediumSolved());
            stat.put("hardSolved", p.getHardSolved());
            stat.put("hasDifficultyBreakdown", p.getEasySolved() != null || p.getMediumSolved() != null || p.getHardSolved() != null);
            stat.put("fetchedAt", p.getFetchedAt());

            long contestCount = historyRepository.countByProfileId(p.getId());
            stat.put("contestCount", contestCount);
            totalContests += contestCount;

            List<RatingHistory> recentHistory = historyRepository.findByProfileIdAndContestDateAfter(p.getId(), thirtyDaysAgo);
            long recentCount = recentHistory.size();
            stat.put("contestsLast30Days", recentCount);
            contestsLast30Days += recentCount;

            if (contestCount > maxPlatformContests) {
                maxPlatformContests = contestCount;
                mostActivePlatform = p.getPlatform();
            }

            if (p.getFetchedAt() != null && (mostRecentSync == null || p.getFetchedAt().isAfter(mostRecentSync))) {
                mostRecentSync = p.getFetchedAt();
            }

            platformStats.add(stat);
        }

        int totalEasy = 0;
        int totalMedium = 0;
        int totalHard = 0;
        boolean hasAnyDifficulty = false;
        for (ConnectedProfile p : profiles) {
            if (p.getEasySolved() != null) { totalEasy += p.getEasySolved(); hasAnyDifficulty = true; }
            if (p.getMediumSolved() != null) { totalMedium += p.getMediumSolved(); hasAnyDifficulty = true; }
            if (p.getHardSolved() != null) { totalHard += p.getHardSolved(); hasAnyDifficulty = true; }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("connectedProfiles", profiles.size());
        result.put("totalContests", totalContests);
        result.put("contestsLast30Days", contestsLast30Days);
        result.put("mostActivePlatform", mostActivePlatform);
        result.put("lastSyncedAt", mostRecentSync);
        result.put("totalEasySolved", hasAnyDifficulty ? totalEasy : null);
        result.put("totalMediumSolved", hasAnyDifficulty ? totalMedium : null);
        result.put("totalHardSolved", hasAnyDifficulty ? totalHard : null);
        result.put("hasDifficultyBreakdown", hasAnyDifficulty);
        result.put("platforms", platformStats);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/history/{platform}")
    public ResponseEntity<?> getRatingHistory(
            @PathVariable String platform,
            @RequestParam(required = false) Integer days) {
        Long userId = AuthUtil.requireUserId();
        Optional<ConnectedProfile> profileOpt = profileRepository.findByUserIdAndPlatform(userId, platform.toUpperCase());

        if (profileOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ConnectedProfile profile = profileOpt.get();
        List<RatingHistory> history;

        if (days != null && days > 0) {
            Instant cutoff = Instant.now().minusSeconds(days * 86400L);
            history = historyRepository.findByProfileIdAndContestDateAfter(profile.getId(), cutoff);
        } else {
            history = historyRepository.findByProfileIdOrderByContestDateAsc(profile.getId());
        }

        List<Map<String, Object>> entries = history.stream().map(h -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("contestName", h.getContestName());
            entry.put("contestDate", h.getContestDate());
            entry.put("ratingBefore", h.getRatingBefore());
            entry.put("ratingAfter", h.getRatingAfter());
            entry.put("rankInContest", h.getRankInContest());
            entry.put("performance", h.getPerformance());
            return entry;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("platform", profile.getPlatform());
        result.put("handle", profile.getHandle());
        result.put("verified", profile.getVerified());
        result.put("currentRating", profile.getRating());
        result.put("maxRating", profile.getMaxRating());
        result.put("entries", entries);
        result.put("dateRange", days != null ? days + " days" : "all");

        return ResponseEntity.ok(result);
    }

    @GetMapping("/heatmap")
    public ResponseEntity<?> getActivityHeatmap(@RequestParam(required = false) String platform) {
        Long userId = AuthUtil.requireUserId();
        List<ConnectedProfile> profiles = profileRepository.findByUserId(userId);

        if (platform != null && !platform.isEmpty() && !"ALL".equalsIgnoreCase(platform)) {
            profiles = profiles.stream()
                    .filter(p -> p.getPlatform().equalsIgnoreCase(platform))
                    .collect(Collectors.toList());
        }

        class DateData {
            int problemsSolved = 0;
            int contestsAttended = 0;
            final Map<String, Map<String, Integer>> platforms = new LinkedHashMap<>();
            final List<Map<String, Object>> details = new ArrayList<>();

            void addContest(String platformName, String contestName, Integer rank, Integer ratingAfter) {
                contestsAttended++;
                Map<String, Integer> pStats = platforms.computeIfAbsent(platformName, k -> new LinkedHashMap<>());
                pStats.merge("contestsAttended", 1, Integer::sum);

                Map<String, Object> d = new LinkedHashMap<>();
                d.put("type", "CONTEST");
                d.put("platform", platformName);
                d.put("name", contestName != null ? contestName : "Contest");
                if (rank != null) d.put("rank", rank);
                if (ratingAfter != null) d.put("ratingAfter", ratingAfter);
                details.add(d);
            }

            void addProblems(String platformName, int count) {
                problemsSolved += count;
                Map<String, Integer> pStats = platforms.computeIfAbsent(platformName, k -> new LinkedHashMap<>());
                pStats.merge("problemsSolved", count, Integer::sum);

                Map<String, Object> d = new LinkedHashMap<>();
                d.put("type", "PROBLEMS");
                d.put("platform", platformName);
                d.put("count", count);
                details.add(d);
            }
        }

        Map<String, DateData> dateMap = new TreeMap<>();

        // 1. Contests from rating_history
        for (ConnectedProfile p : profiles) {
            List<RatingHistory> history = historyRepository.findByProfileIdOrderByContestDateAsc(p.getId());
            for (RatingHistory rh : history) {
                LocalDate date = rh.getContestDate().atZone(ZoneOffset.UTC).toLocalDate();
                dateMap.computeIfAbsent(date.toString(), k -> new DateData())
                        .addContest(p.getPlatform(), rh.getContestName(), rh.getRankInContest(), rh.getRatingAfter());
            }
        }

        // 2. Problems from profile submissionCalendar
        for (ConnectedProfile p : profiles) {
            String calJson = p.getSubmissionCalendar();
            if (calJson != null && !calJson.isBlank()) {
                try {
                    Map<String, Object> cal = objectMapper.readValue(calJson, new TypeReference<Map<String, Object>>() {});
                    for (Map.Entry<String, Object> entry : cal.entrySet()) {
                        String key = entry.getKey();
                        int cnt = ((Number) entry.getValue()).intValue();
                        if (cnt <= 0) continue;

                        String dateStr;
                        if (key.matches("^\\d+$")) {
                            long sec = Long.parseLong(key);
                            if (sec > 100000000000L) {
                                sec = sec / 1000L;
                            }
                            dateStr = Instant.ofEpochSecond(sec).atZone(ZoneOffset.UTC).toLocalDate().toString();
                        } else {
                            dateStr = key;
                        }

                        dateMap.computeIfAbsent(dateStr, k -> new DateData())
                                .addProblems(p.getPlatform(), cnt);
                    }
                } catch (Exception ignored) {}
            }
        }

        int totalProblems = 0;
        int totalContests = 0;
        List<Map<String, Object>> entries = new ArrayList<>();

        for (Map.Entry<String, DateData> e : dateMap.entrySet()) {
            DateData dd = e.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", e.getKey());
            m.put("count", dd.problemsSolved + dd.contestsAttended);
            m.put("problemsSolved", dd.problemsSolved);
            m.put("contestsAttended", dd.contestsAttended);
            m.put("platforms", dd.platforms);
            m.put("details", dd.details);
            entries.add(m);

            totalProblems += dd.problemsSolved;
            totalContests += dd.contestsAttended;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entries", entries);
        result.put("platform", platform != null ? platform : "ALL");
        result.put("dataSource", "unified_activity");
        result.put("description", platform != null
                ? "Unified activity (problems solved + contest participation) for " + platform
                : "Unified activity across all connected platforms");
        result.put("totalProblemsSolved", totalProblems);
        result.put("totalContestsAttended", totalContests);
        result.put("totalActivity", totalProblems + totalContests);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/history/all")
    public ResponseEntity<?> getAllRatingHistory(@RequestParam(required = false) Integer days) {
        Long userId = AuthUtil.requireUserId();
        List<ConnectedProfile> profiles = profileRepository.findByUserId(userId);

        List<Map<String, Object>> allEntries = new ArrayList<>();

        for (ConnectedProfile profile : profiles) {
            List<RatingHistory> history;
            if (days != null && days > 0) {
                Instant cutoff = Instant.now().minusSeconds(days * 86400L);
                history = historyRepository.findByProfileIdAndContestDateAfter(profile.getId(), cutoff);
            } else {
                history = historyRepository.findByProfileIdOrderByContestDateAsc(profile.getId());
            }

            for (RatingHistory h : history) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("platform", profile.getPlatform());
                entry.put("contestName", h.getContestName());
                entry.put("contestDate", h.getContestDate());
                entry.put("ratingBefore", h.getRatingBefore());
                entry.put("ratingAfter", h.getRatingAfter());
                entry.put("rankInContest", h.getRankInContest());
                entry.put("performance", h.getPerformance());
                allEntries.add(entry);
            }
        }

        allEntries.sort(Comparator.comparing(e -> (Instant) e.get("contestDate")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("platform", "OVERALL");
        result.put("entries", allEntries);
        result.put("dateRange", days != null ? days + " days" : "all");

        return ResponseEntity.ok(result);
    }

    @GetMapping("/recommendations")
    public ResponseEntity<?> getRecommendations() {
        Long userId = AuthUtil.requireUserId();
        return ResponseEntity.ok(recommendationService.recommend(userId));
    }
}

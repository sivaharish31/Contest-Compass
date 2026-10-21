package com.contestradar.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class ProfileVerificationService {

    private static final Logger log = LoggerFactory.getLogger(ProfileVerificationService.class);
    private static final int TOKEN_LENGTH = 8;
    private static final int MAX_ATTEMPTS = 10;
    private static final long EXPIRY_SECONDS = 1800;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String TOKEN_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final Pattern SAFE_HANDLE = Pattern.compile("^[A-Za-z0-9_-]{1,100}$");
    private static final Pattern ATCODER_RATING = Pattern.compile(
            "Rating</th>\\s*<td[^>]*>\\s*<span[^>]*>(\\d+)</span>");
    private static final Pattern ATCODER_MAX_RATING = Pattern.compile(
            "Highest Rating</th>\\s*<td[^>]*>\\s*<span[^>]*>(\\d+)</span>");

    private final ConnectedProfileRepository profileRepository;
    private final RatingHistoryRepository ratingHistoryRepository;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProfileVerificationService(ConnectedProfileRepository profileRepository,
                                      RatingHistoryRepository ratingHistoryRepository,
                                      RestClient.Builder restClientBuilder) {
        this.profileRepository = profileRepository;
        this.ratingHistoryRepository = ratingHistoryRepository;
        this.restClient = restClientBuilder.build();
    }

    @Transactional
    public String generateVerificationChallenge(ConnectedProfile profile) {
        if (Boolean.TRUE.equals(profile.getVerified())) {
            throw new IllegalStateException("Profile is already verified");
        }
        if (!SAFE_HANDLE.matcher(profile.getHandle()).matches()) {
            throw new IllegalArgumentException("Invalid handle format");
        }

        String platform = profile.getPlatform();
        String method = switch (platform) {
            case "CODEFORCES" -> "CODEFORCES_ORGANIZATION";
            case "LEETCODE" -> "LEETCODE_WEBSITE";
            case "ATCODER" -> "ATCODER_AFFILIATION";
            default -> throw new IllegalStateException("Verification not supported for " + platform);
        };

        String token = generateToken();
        profile.setVerificationToken(token);
        profile.setVerificationMethod(method);
        profile.setVerificationExpiresAt(Instant.now().plusSeconds(EXPIRY_SECONDS));
        profile.setVerificationAttempts(0);
        profileRepository.save(profile);
        return token;
    }

    @Transactional
    public VerificationResult verifyProfile(ConnectedProfile profile) {
        if (profile.getVerificationToken() == null) {
            return new VerificationResult(false, "No verification challenge active. Start verification first.", 0);
        }
        if (profile.getVerificationExpiresAt() != null && profile.getVerificationExpiresAt().isBefore(Instant.now())) {
            profile.setVerificationToken(null);
            profile.setVerificationExpiresAt(null);
            profile.setVerificationAttempts(0);
            profileRepository.save(profile);
            return new VerificationResult(false, "Verification challenge expired. Please start a new one.", 0);
        }
        if (profile.getVerificationAttempts() >= MAX_ATTEMPTS) {
            profile.setVerificationToken(null);
            profile.setVerificationExpiresAt(null);
            profile.setVerificationAttempts(0);
            profileRepository.save(profile);
            return new VerificationResult(false, "Too many attempts. Please start a new challenge.", 0);
        }

        profile.setVerificationAttempts(profile.getVerificationAttempts() + 1);
        profileRepository.save(profile);

        int remaining = MAX_ATTEMPTS - profile.getVerificationAttempts();

        String platform = profile.getPlatform();
        boolean verified = switch (platform) {
            case "CODEFORCES" -> verifyCodeforces(profile);
            case "LEETCODE" -> verifyLeetCode(profile);
            case "ATCODER" -> verifyAtCoder(profile);
            default -> false;
        };

        if (verified) {
            return new VerificationResult(true, "Profile verified successfully!", remaining);
        }
        String failMsg = switch (platform) {
            case "CODEFORCES" -> "Token not found in your Codeforces organization field.";
            case "LEETCODE" -> "Token not found in your LeetCode profile website or summary field.";
            case "ATCODER" -> "Token not found in your AtCoder affiliation field.";
            default -> "Verification not supported for this platform.";
        };
        return new VerificationResult(false, failMsg, remaining);
    }

    @SuppressWarnings("unchecked")
    private boolean verifyCodeforces(ConnectedProfile profile) {
        if (!SAFE_HANDLE.matcher(profile.getHandle()).matches()) return false;
        try {
            Map<String, Object> response = restClient.get()
                    .uri("https://codeforces.com/api/user.info?handles=" + profile.getHandle())
                    .retrieve()
                    .body(Map.class);

            if (response == null || !"OK".equals(response.get("status"))) return false;

            List<Map<String, Object>> users = (List<Map<String, Object>>) response.get("result");
            if (users == null || users.isEmpty()) return false;

            Map<String, Object> user = users.get(0);
            String org = (String) user.get("organization");

            if (org != null && org.contains(profile.getVerificationToken())) {
                updateCodeforcesProfileData(profile, user);
                markVerified(profile);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Codeforces verification failed for {}: {}", profile.getHandle(), e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean verifyLeetCode(ConnectedProfile profile) {
        if (!SAFE_HANDLE.matcher(profile.getHandle()).matches()) return false;
        String safeHandle = profile.getHandle();
        String query = "{ matchedUser(username: \"" + safeHandle + "\") { profile { aboutMe websites } } }";
        try {
            Map<String, Object> response = restClient.post()
                    .uri("https://leetcode.com/graphql")
                    .header("Content-Type", "application/json")
                    .body(Map.of("query", query))
                    .retrieve()
                    .body(Map.class);

            if (response == null || response.get("data") == null) return false;
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            Map<String, Object> user = (Map<String, Object>) data.get("matchedUser");
            if (user == null) return false;

            Map<String, Object> profileData = (Map<String, Object>) user.get("profile");
            if (profileData == null) return false;

            String token = profile.getVerificationToken();

            // Check websites field
            Object websites = profileData.get("websites");
            if (websites instanceof List<?> list) {
                for (Object w : list) {
                    if (w instanceof String s && s.contains(token)) {
                        markVerified(profile);
                        refreshLeetCodeData(profile);
                        return true;
                    }
                }
            }

            // Check aboutMe field
            String aboutMe = (String) profileData.get("aboutMe");
            if (aboutMe != null && aboutMe.contains(token)) {
                markVerified(profile);
                refreshLeetCodeData(profile);
                return true;
            }

            return false;
        } catch (Exception e) {
            log.error("LeetCode verification failed for {}: {}", profile.getHandle(), e.getMessage());
            return false;
        }
    }

    private static final Pattern ATCODER_AFFILIATION = Pattern.compile(
            "Affiliation</th>\\s*<td[^>]*>([^<]*)</td>", Pattern.DOTALL);

    private boolean verifyAtCoder(ConnectedProfile profile) {
        if (!SAFE_HANDLE.matcher(profile.getHandle()).matches()) return false;
        try {
            String html = restClient.get()
                    .uri("https://atcoder.jp/users/" + profile.getHandle())
                    .retrieve()
                    .body(String.class);

            if (html == null) return false;

            java.util.regex.Matcher m = ATCODER_AFFILIATION.matcher(html);
            if (m.find()) {
                String affiliation = m.group(1).trim();
                if (affiliation.contains(profile.getVerificationToken())) {
                    markVerified(profile);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("AtCoder verification failed for {}: {}", profile.getHandle(), e.getMessage());
            return false;
        }
    }

    private void markVerified(ConnectedProfile profile) {
        profile.setVerified(true);
        profile.setVerifiedAt(Instant.now());
        profile.setVerificationToken(null);
        profile.setVerificationExpiresAt(null);
        profile.setVerificationAttempts(0);
        profileRepository.save(profile);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public void refreshProfileData(ConnectedProfile profile) {
        if (!SAFE_HANDLE.matcher(profile.getHandle()).matches()) {
            log.warn("Skipping refresh for invalid handle: {}", profile.getHandle());
            profile.setLastSyncStatus("FAILED");
            profile.setLastSyncError("Invalid handle format");
            profileRepository.save(profile);
            return;
        }
        String platform = profile.getPlatform();
        try {
            switch (platform) {
                case "CODEFORCES" -> refreshCodeforcesData(profile);
                case "LEETCODE" -> refreshLeetCodeData(profile);
                case "ATCODER" -> refreshAtCoderData(profile);
                case "CODECHEF" -> {
                    log.debug("CodeChef has no public user API — skipping data refresh");
                    profile.setLastSyncStatus("SUCCESS");
                    profile.setLastSyncError(null);
                    profile.setFetchedAt(Instant.now());
                    profileRepository.save(profile);
                }
            }
        } catch (Exception e) {
            log.error("Failed to refresh {} profile for {}: {}", platform, profile.getHandle(), e.getMessage(), e);
            profile.setLastSyncStatus("FAILED");
            profile.setLastSyncError(e.getMessage());
            profileRepository.save(profile);
            // Don't re-throw - error status is saved, controller can check it
        }
    }

    @SuppressWarnings("unchecked")
    private void refreshCodeforcesData(ConnectedProfile profile) {
        Map<String, Object> response = restClient.get()
                .uri("https://codeforces.com/api/user.info?handles=" + profile.getHandle())
                .retrieve()
                .body(Map.class);

        if (response != null && "OK".equals(response.get("status"))) {
            List<Map<String, Object>> users = (List<Map<String, Object>>) response.get("result");
            if (users != null && !users.isEmpty()) {
                updateCodeforcesProfileData(profile, users.get(0));
            }
        }

        Map<String, Object> ratingResponse = restClient.get()
                .uri("https://codeforces.com/api/user.rating?handle=" + profile.getHandle())
                .retrieve()
                .body(Map.class);

        if (ratingResponse != null && "OK".equals(ratingResponse.get("status"))) {
            List<Map<String, Object>> history = (List<Map<String, Object>>) ratingResponse.get("result");
            if (history != null && !history.isEmpty()) {
                List<RatingHistory> existingHistory = ratingHistoryRepository.findByProfileIdOrderByContestDateAsc(profile.getId());
                Map<String, RatingHistory> existingByKey = existingHistory.stream()
                        .collect(java.util.stream.Collectors.toMap(
                                h -> h.getContestDate().getEpochSecond() + ":" + (h.getContestName() != null ? h.getContestName() : ""),
                                h -> h,
                                (h1, h2) -> h1
                        ));

                for (Map<String, Object> h : history) {
                    String contestName = (String) h.get("contestName");
                    long updateTime = ((Number) h.get("ratingUpdateTimeSeconds")).longValue();
                    Instant contestDate = Instant.ofEpochSecond(updateTime);

                    String dedupeKey = contestDate.getEpochSecond() + ":" + (contestName != null ? contestName : "");

                    if (!existingByKey.containsKey(dedupeKey)) {
                        RatingHistory rh = new RatingHistory();
                        rh.setProfileId(profile.getId());
                        rh.setContestName(contestName);
                        rh.setContestDate(contestDate);
                        rh.setRatingBefore(((Number) h.get("oldRating")).intValue());
                        rh.setRatingAfter(((Number) h.get("newRating")).intValue());
                        rh.setRankInContest(((Number) h.get("rank")).intValue());
                        ratingHistoryRepository.save(rh);
                    }
                }
            }
        }

        try {
            Map<String, Object> statusResponse = restClient.get()
                    .uri("https://codeforces.com/api/user.status?handle=" + profile.getHandle() + "&from=1&count=1000")
                    .retrieve()
                    .body(Map.class);

            if (statusResponse != null && "OK".equals(statusResponse.get("status"))) {
                List<Map<String, Object>> submissions = (List<Map<String, Object>>) statusResponse.get("result");
                if (submissions != null && !submissions.isEmpty()) {
                    // Sort chronologically ascending to credit each unique problem to its first solved date
                    List<Map<String, Object>> sortedSubmissions = new ArrayList<>(submissions);
                    sortedSubmissions.sort(Comparator.comparingLong(s -> {
                        Object t = s.get("creationTimeSeconds");
                        return t instanceof Number num ? num.longValue() : 0L;
                    }));

                    Set<String> solvedProblemKeys = new HashSet<>();
                    Map<String, Integer> dateCounts = new TreeMap<>();

                    for (Map<String, Object> sub : sortedSubmissions) {
                        String verdict = (String) sub.get("verdict");
                        if ("OK".equals(verdict)) {
                            Map<String, Object> problem = (Map<String, Object>) sub.get("problem");
                            if (problem != null) {
                                Object cId = problem.get("contestId");
                                Object idx = problem.get("index");
                                Object name = problem.get("name");
                                String pKey = (cId != null && idx != null)
                                        ? cId + ":" + idx
                                        : (name != null ? "name:" + name : String.valueOf(sub.get("id")));

                                if (solvedProblemKeys.add(pKey)) {
                                    Object timeObj = sub.get("creationTimeSeconds");
                                    if (timeObj instanceof Number num) {
                                        long sec = num.longValue();
                                        LocalDate d = Instant.ofEpochSecond(sec).atZone(ZoneOffset.UTC).toLocalDate();
                                        dateCounts.merge(d.toString(), 1, Integer::sum);
                                    }
                                }
                            }
                        }
                    }

                    if (!solvedProblemKeys.isEmpty()) {
                        profile.setProblemsSolved(solvedProblemKeys.size());
                    }
                    if (!dateCounts.isEmpty()) {
                        profile.setSubmissionCalendar(objectMapper.writeValueAsString(dateCounts));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch Codeforces submissions for {}: {}", profile.getHandle(), e.getMessage());
        }

        profile.setFetchedAt(Instant.now());
        profile.setLastSyncStatus("SUCCESS");
        profile.setLastSyncError(null);
        profileRepository.save(profile);
    }

    @SuppressWarnings("unchecked")
    private void refreshLeetCodeData(ConnectedProfile profile) {
        String safeHandle = profile.getHandle();

        String profileQuery = "{ matchedUser(username: \"" + safeHandle + "\") { " +
                "username profile { ranking } submitStats { acSubmissionNum { difficulty count } } submissionCalendar } }";

        try {
            Map<String, Object> response = restClient.post()
                    .uri("https://leetcode.com/graphql")
                    .header("Content-Type", "application/json")
                    .body(Map.of("query", profileQuery))
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.get("data") != null) {
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                Map<String, Object> user = (Map<String, Object>) data.get("matchedUser");
                if (user != null) {
                    // Note: profile.ranking is the global problem-solving rank, NOT contest rating
                    // We intentionally do NOT set profile.rating here - it will be set from contest data below
                    Map<String, Object> stats = (Map<String, Object>) user.get("submitStats");
                    if (stats != null) {
                        List<Map<String, Object>> acNums = (List<Map<String, Object>>) stats.get("acSubmissionNum");
                        if (acNums != null) {
                            for (Map<String, Object> ac : acNums) {
                                String diff = (String) ac.get("difficulty");
                                int count = ((Number) ac.get("count")).intValue();
                                if ("All".equalsIgnoreCase(diff)) {
                                    profile.setProblemsSolved(count);
                                } else if ("Easy".equalsIgnoreCase(diff)) {
                                    profile.setEasySolved(count);
                                } else if ("Medium".equalsIgnoreCase(diff)) {
                                    profile.setMediumSolved(count);
                                } else if ("Hard".equalsIgnoreCase(diff)) {
                                    profile.setHardSolved(count);
                                }
                            }
                        }
                    }

                    Object calObj = user.get("submissionCalendar");
                    if (calObj instanceof String calStr && !calStr.isBlank()) {
                        profile.setSubmissionCalendar(calStr);
                    }
                }
            }

            String contestQuery = "{ userContestRanking(username: \"" + safeHandle + "\") { " +
                    "rating } " +
                    "userContestRankingHistory(username: \"" + safeHandle + "\") { " +
                    "attended rating ranking contest { title startTime } } }";

            Map<String, Object> contestResponse = restClient.post()
                    .uri("https://leetcode.com/graphql")
                    .header("Content-Type", "application/json")
                    .body(Map.of("query", contestQuery))
                    .retrieve()
                    .body(Map.class);

            if (contestResponse != null && contestResponse.get("data") != null) {
                Map<String, Object> contestData = (Map<String, Object>) contestResponse.get("data");

                Map<String, Object> ranking = (Map<String, Object>) contestData.get("userContestRanking");
                if (ranking != null && ranking.get("rating") != null) {
                    double ratingDouble = ((Number) ranking.get("rating")).doubleValue();
                    profile.setRating((int) Math.round(ratingDouble));
                }

                List<Map<String, Object>> history = (List<Map<String, Object>>) contestData.get("userContestRankingHistory");
                if (history != null && !history.isEmpty()) {
                    List<RatingHistory> existingHistory = ratingHistoryRepository.findByProfileIdOrderByContestDateAsc(profile.getId());
                    Map<String, RatingHistory> existingByKey = existingHistory.stream()
                            .collect(java.util.stream.Collectors.toMap(
                                    h -> h.getContestDate().getEpochSecond() + ":" + (h.getContestName() != null ? h.getContestName() : ""),
                                    h -> h,
                                    (h1, h2) -> h1
                            ));

                    int previousRating = 0;
                    boolean hasMaxRating = false;
                    int calculatedMaxRating = 0;

                    for (Map<String, Object> entry : history) {
                        Boolean attended = (Boolean) entry.get("attended");
                        if (attended == null || !attended) continue;

                        Map<String, Object> contest = (Map<String, Object>) entry.get("contest");
                        if (contest == null) continue;

                        String title = (String) contest.get("title");
                        Object startTimeObj = contest.get("startTime");
                        if (startTimeObj == null) continue;

                        long startTime = ((Number) startTimeObj).longValue();
                        Instant contestDate = Instant.ofEpochSecond(startTime);

                        Object ratingObj = entry.get("rating");
                        if (ratingObj == null) continue;

                        double ratingDouble = ((Number) ratingObj).doubleValue();
                        int ratingAfter = (int) Math.round(ratingDouble);

                        Object rankingObj = entry.get("ranking");
                        Integer rank = rankingObj != null ? ((Number) rankingObj).intValue() : null;

                        String dedupeKey = contestDate.getEpochSecond() + ":" + (title != null ? title : "");

                        if (!existingByKey.containsKey(dedupeKey)) {
                            RatingHistory rh = new RatingHistory();
                            rh.setProfileId(profile.getId());
                            rh.setContestName(title);
                            rh.setContestDate(contestDate);
                            rh.setRatingBefore(previousRating > 0 ? previousRating : null);
                            rh.setRatingAfter(ratingAfter);
                            rh.setRankInContest(rank);
                            ratingHistoryRepository.save(rh);
                        }

                        previousRating = ratingAfter;
                        if (ratingAfter > calculatedMaxRating) {
                            calculatedMaxRating = ratingAfter;
                            hasMaxRating = true;
                        }
                    }

                    if (hasMaxRating) {
                        profile.setMaxRating(calculatedMaxRating);
                    }
                }
            }

            profile.setFetchedAt(Instant.now());
            profile.setLastSyncStatus("SUCCESS");
            profile.setLastSyncError(null);
            profileRepository.save(profile);
        } catch (Exception e) {
            log.error("Failed to refresh LeetCode data for {}: {}", profile.getHandle(), e.getMessage(), e);
            profile.setLastSyncStatus("FAILED");
            profile.setLastSyncError(e.getMessage());
            profile.setFetchedAt(Instant.now()); // Still update fetchedAt to track attempt
            profileRepository.save(profile);
            // Don't re-throw - error status is saved
        }
    }

    @SuppressWarnings("unchecked")
    private void refreshAtCoderData(ConnectedProfile profile) {
        try {
            String html = restClient.get()
                    .uri("https://atcoder.jp/users/" + profile.getHandle())
                    .retrieve()
                    .body(String.class);

            if (html == null) {
                log.warn("AtCoder profile page returned null for {}", profile.getHandle());
                return;
            }

            Integer currentRating = null;
            Integer maxRating = null;

            java.util.regex.Matcher ratingMatcher = ATCODER_RATING.matcher(html);
            if (ratingMatcher.find()) {
                currentRating = Integer.parseInt(ratingMatcher.group(1));
                profile.setRating(currentRating);
            }

            java.util.regex.Matcher maxMatcher = ATCODER_MAX_RATING.matcher(html);
            if (maxMatcher.find()) {
                maxRating = Integer.parseInt(maxMatcher.group(1));
                profile.setMaxRating(maxRating);
            }

            boolean historySuccess = false;
            String historyError = null;

            try {
                List<Map<String, Object>> historyJson = restClient.get()
                        .uri("https://atcoder.jp/users/" + profile.getHandle() + "/history/json")
                        .retrieve()
                        .body(List.class);

                if (historyJson != null && !historyJson.isEmpty()) {
                    List<RatingHistory> existingHistory = ratingHistoryRepository.findByProfileIdOrderByContestDateAsc(profile.getId());
                    Map<String, RatingHistory> existingByKey = existingHistory.stream()
                            .collect(java.util.stream.Collectors.toMap(
                                    h -> h.getContestDate().getEpochSecond() + ":" + (h.getContestName() != null ? h.getContestName() : ""),
                                    h -> h,
                                    (h1, h2) -> h1
                            ));

                    int calculatedMaxRating = maxRating != null ? maxRating : 0;

                    for (Map<String, Object> entry : historyJson) {
                        String contestName = (String) entry.get("ContestName");
                        Object contestTimeObj = entry.get("EndTime");
                        if (contestTimeObj == null) continue;

                        Instant contestDate;
                        if (contestTimeObj instanceof String) {
                            contestDate = Instant.parse((String) contestTimeObj);
                        } else if (contestTimeObj instanceof Number) {
                            contestDate = Instant.ofEpochSecond(((Number) contestTimeObj).longValue());
                        } else {
                            log.debug("Skipping AtCoder contest with unexpected EndTime format: {}", contestTimeObj.getClass());
                            continue;
                        }

                        Object newRatingObj = entry.get("NewRating");
                        if (newRatingObj == null) continue;
                        int ratingAfter = ((Number) newRatingObj).intValue();

                        Object oldRatingObj = entry.get("OldRating");
                        Integer ratingBefore = oldRatingObj != null ? ((Number) oldRatingObj).intValue() : null;

                        Object placeObj = entry.get("Place");
                        Integer rank = placeObj != null ? ((Number) placeObj).intValue() : null;

                        Object perfObj = entry.get("Performance");
                        Integer performance = perfObj != null ? ((Number) perfObj).intValue() : null;

                        String dedupeKey = contestDate.getEpochSecond() + ":" + (contestName != null ? contestName : "");

                        if (!existingByKey.containsKey(dedupeKey)) {
                            RatingHistory rh = new RatingHistory();
                            rh.setProfileId(profile.getId());
                            rh.setContestName(contestName);
                            rh.setContestDate(contestDate);
                            rh.setRatingBefore(ratingBefore);
                            rh.setRatingAfter(ratingAfter);
                            rh.setRankInContest(rank);
                            rh.setPerformance(performance);
                            ratingHistoryRepository.save(rh);
                        }

                        if (ratingAfter > calculatedMaxRating) {
                            calculatedMaxRating = ratingAfter;
                        }
                    }

                    if (calculatedMaxRating > 0 && (maxRating == null || calculatedMaxRating > maxRating)) {
                        profile.setMaxRating(calculatedMaxRating);
                    }
                }
                historySuccess = true;
            } catch (Exception e) {
                historyError = "Contest history sync failed: " + e.getMessage();
                log.warn("Failed to fetch AtCoder contest history for {} (profile stats were retrieved): {}",
                        profile.getHandle(), e.getMessage(), e);
            }

            profile.setFetchedAt(Instant.now());

            // Determine sync status based on what succeeded
            if (historySuccess) {
                profile.setLastSyncStatus("SUCCESS");
                profile.setLastSyncError(null);
            } else {
                profile.setLastSyncStatus("PARTIAL");
                profile.setLastSyncError(historyError);
            }

            profileRepository.save(profile);
        } catch (Exception e) {
            log.error("Failed to refresh AtCoder data for {}: {}", profile.getHandle(), e.getMessage(), e);
            profile.setLastSyncStatus("FAILED");
            profile.setLastSyncError(e.getMessage());
            profile.setFetchedAt(Instant.now());
            profileRepository.save(profile);
            // Don't re-throw - error status is saved
        }
    }

    private void updateCodeforcesProfileData(ConnectedProfile profile, Map<String, Object> user) {
        if (user.get("rating") != null) profile.setRating(((Number) user.get("rating")).intValue());
        if (user.get("maxRating") != null) profile.setMaxRating(((Number) user.get("maxRating")).intValue());
        if (user.get("rank") != null) profile.setRankTitle((String) user.get("rank"));
    }

    private String generateToken() {
        StringBuilder sb = new StringBuilder(TOKEN_LENGTH);
        for (int i = 0; i < TOKEN_LENGTH; i++) {
            sb.append(TOKEN_CHARS.charAt(RANDOM.nextInt(TOKEN_CHARS.length())));
        }
        return "CR-" + sb;
    }

    public record VerificationResult(boolean verified, String message, int attemptsRemaining) {}
}

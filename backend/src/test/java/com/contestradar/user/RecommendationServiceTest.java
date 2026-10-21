package com.contestradar.user;

import com.contestradar.contest.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RecommendationServiceTest {

    @Autowired private RecommendationService recommendationService;
    @Autowired private ContestRepository contestRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ConnectedProfileRepository profileRepository;
    @Autowired private RatingHistoryRepository historyRepository;
    @Autowired private UserPreferencesRepository preferencesRepository;

    private Long userId;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        profileRepository.deleteAll();
        contestRepository.deleteAll();

        User user = new User();
        user.setEmail("rec-" + System.nanoTime() + "@test.com");
        user.setUsername("recuser" + System.nanoTime());
        user.setPasswordHash("$2a$10$test");
        user = userRepository.save(user);
        userId = user.getId();

        UserPreferences prefs = new UserPreferences();
        prefs.setUserId(userId);
        prefs.setPreferredPlatforms("CODEFORCES,ATCODER");
        preferencesRepository.save(prefs);

        // Create upcoming contests
        Contest cf1 = makeContest("cf-1", Platform.CODEFORCES, "CF Round 1",
                Instant.now().plus(12, ChronoUnit.HOURS), 7200L, null, 1900);
        Contest cf2 = makeContest("cf-2", Platform.CODEFORCES, "CF Round 2",
                Instant.now().plus(5, ChronoUnit.DAYS), 7200L, null, null);
        Contest lc1 = makeContest("lc-1", Platform.LEETCODE, "LC Weekly",
                Instant.now().plus(2, ChronoUnit.DAYS), 5400L, null, null);

        contestRepository.saveAll(List.of(cf1, cf2, lc1));
    }

    @Test
    void beginnerGetsRecommendations() {
        List<RecommendationService.ContestRecommendation> recs = recommendationService.recommend(userId);
        assertFalse(recs.isEmpty());
        assertEquals("beginner", recs.get(0).userLevel());
    }

    @Test
    void preferredPlatformScoresHigher() {
        List<RecommendationService.ContestRecommendation> recs = recommendationService.recommend(userId);

        RecommendationService.ContestRecommendation cfRec = recs.stream()
                .filter(r -> "CODEFORCES".equals(r.contest().platform().name()))
                .findFirst().orElse(null);
        RecommendationService.ContestRecommendation lcRec = recs.stream()
                .filter(r -> "LEETCODE".equals(r.contest().platform().name()))
                .findFirst().orElse(null);

        assertNotNull(cfRec, "Should recommend CF contest");
        if (lcRec != null) {
            assertTrue(cfRec.score() >= lcRec.score(), "CF (preferred) should score >= LC (not preferred)");
        }
    }

    @Test
    void connectedProfileBoostsScore() {
        ConnectedProfile profile = new ConnectedProfile();
        profile.setUserId(userId);
        profile.setPlatform("CODEFORCES");
        profile.setHandle("testcf");
        profile.setRating(1500);
        profileRepository.save(profile);

        List<RecommendationService.ContestRecommendation> recs = recommendationService.recommend(userId);
        RecommendationService.ContestRecommendation cfRec = recs.stream()
                .filter(r -> "CODEFORCES".equals(r.contest().platform().name()))
                .findFirst().orElse(null);

        assertNotNull(cfRec);
        boolean hasRatingReason = cfRec.reasons().stream()
                .anyMatch(r -> r.contains("rating") || r.contains("range"));
        assertTrue(hasRatingReason, "Should mention rating in reasons");
    }

    @Test
    void improvingUserIsDetected() {
        ConnectedProfile profile = new ConnectedProfile();
        profile.setUserId(userId);
        profile.setPlatform("CODEFORCES");
        profile.setHandle("improver");
        profile.setRating(1500);
        profile = profileRepository.save(profile);

        // Add improving history
        for (int i = 0; i < 5; i++) {
            RatingHistory rh = new RatingHistory();
            rh.setProfileId(profile.getId());
            rh.setContestName("Round " + i);
            rh.setContestDate(Instant.now().minus(30 - i, ChronoUnit.DAYS));
            rh.setRatingBefore(1200 + i * 50);
            rh.setRatingAfter(1250 + i * 50);
            rh.setRankInContest(1000 - i * 100);
            historyRepository.save(rh);
        }

        List<RecommendationService.ContestRecommendation> recs = recommendationService.recommend(userId);
        assertFalse(recs.isEmpty());
        assertEquals("improving", recs.get(0).userLevel());
    }

    @Test
    void reasonsAreProvided() {
        List<RecommendationService.ContestRecommendation> recs = recommendationService.recommend(userId);
        for (RecommendationService.ContestRecommendation rec : recs) {
            assertFalse(rec.reasons().isEmpty(), "Each recommendation should have at least one reason");
            assertTrue(rec.score() > 0, "Score should be positive");
        }
    }

    @Test
    void contestBeyond60DaysIsExcludedFromRecommendations() {
        // A contest starting in 59 days is within the window; one at 61 days is not.
        // This exercises the bounded repository query added in MED-06.
        Contest within = makeContest("within-60", Platform.CODEFORCES, "Within Window",
                Instant.now().plus(59, ChronoUnit.DAYS), 7200L, null, null);
        Contest beyond = makeContest("beyond-60", Platform.CODEFORCES, "Beyond Window",
                Instant.now().plus(61, ChronoUnit.DAYS), 7200L, null, null);
        contestRepository.saveAll(List.of(within, beyond));

        List<RecommendationService.ContestRecommendation> recs = recommendationService.recommend(userId);

        boolean withinPresent = recs.stream()
                .anyMatch(r -> "within-60".equals(r.contest().externalId()));
        boolean beyondPresent = recs.stream()
                .anyMatch(r -> "beyond-60".equals(r.contest().externalId()));

        assertTrue(withinPresent,
                "Contest starting in 59 days should appear in recommendations");
        assertFalse(beyondPresent,
                "Contest starting in 61 days must be excluded from recommendations");
    }

    @Test
    void contestExactlyAt60DaysIsIncludedInRecommendations() {
        // The boundary itself (exactly 60 days = 60 * 86400 seconds) should be included.
        // maxTime = now + 60 * 86400s; the query uses c.startTime <= maxTime.
        Contest boundary = makeContest("at-60", Platform.CODEFORCES, "At Boundary",
                Instant.now().plusSeconds(60L * 86400 - 1), 7200L, null, null);
        contestRepository.save(boundary);

        List<RecommendationService.ContestRecommendation> recs = recommendationService.recommend(userId);

        boolean boundaryPresent = recs.stream()
                .anyMatch(r -> "at-60".equals(r.contest().externalId()));
        assertTrue(boundaryPresent,
                "Contest at the 60-day boundary should be included in recommendations");
    }

    private Contest makeContest(String externalId, Platform platform, String name,
                                Instant startTime, long durationSec,
                                Integer ratingMin, Integer ratingMax) {
        Contest c = new Contest();
        c.setExternalId(externalId);
        c.setPlatform(platform);
        c.setName(name);
        c.setStartTime(startTime);
        c.setEndTime(startTime.plusSeconds(durationSec));
        c.setDurationSeconds(durationSec);
        c.setUrl("https://example.com/contest/" + externalId);
        c.setStatus(ContestStatus.UPCOMING);
        c.setRatingMin(ratingMin);
        c.setRatingMax(ratingMax);
        return c;
    }
}

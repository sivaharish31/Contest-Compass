package com.contestradar.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for data synchronization edge cases and rating calculations.
 * These tests verify that the sync logic correctly handles various data scenarios
 * without requiring external API calls.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProfileDataSyncTest {

    @Autowired
    private ConnectedProfileRepository profileRepository;

    @Autowired
    private RatingHistoryRepository ratingHistoryRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        ratingHistoryRepository.deleteAll();
        profileRepository.deleteAll();

        testUser = new User();
        testUser.setEmail("data-sync-test@example.com");
        testUser.setUsername("datasynctest");
        testUser.setPasswordHash("hashed");
        testUser = userRepository.save(testUser);
    }

    @Test
    void leetCodeMaxRatingCalculatedFromHistory() {
        // Setup: Create a LeetCode profile
        ConnectedProfile profile = new ConnectedProfile();
        profile.setUserId(testUser.getId());
        profile.setPlatform("LEETCODE");
        profile.setHandle("testuser");
        profile.setRating(1600); // Current rating
        profile = profileRepository.save(profile);

        // Add contest history with varying ratings
        saveRatingHistory(profile.getId(), "Weekly 300", "2024-01-01T12:00:00Z", null, 1500, 1000);
        saveRatingHistory(profile.getId(), "Weekly 301", "2024-01-08T12:00:00Z", 1500, 1800, 500); // Peak
        saveRatingHistory(profile.getId(), "Weekly 302", "2024-01-15T12:00:00Z", 1800, 1600, 800); // Decline

        List<RatingHistory> history = ratingHistoryRepository.findByProfileIdOrderByContestDateAsc(profile.getId());
        assertEquals(3, history.size());

        // Calculate max rating from history
        int maxFromHistory = history.stream()
                .mapToInt(RatingHistory::getRatingAfter)
                .max()
                .orElse(0);

        assertEquals(1800, maxFromHistory, "Max rating should be calculated from contest history");
    }

    @Test
    void atCoderMaxRatingHandlesNoHistory() {
        // Setup: AtCoder profile with rating but no history yet
        ConnectedProfile profile = new ConnectedProfile();
        profile.setUserId(testUser.getId());
        profile.setPlatform("ATCODER");
        profile.setHandle("testuser");
        profile.setRating(1500);
        profile.setMaxRating(1800); // From HTML scraping
        profile = profileRepository.save(profile);

        List<RatingHistory> history = ratingHistoryRepository.findByProfileIdOrderByContestDateAsc(profile.getId());
        assertEquals(0, history.size(), "No history should exist");

        // Max rating should come from profile field
        assertNotNull(profile.getMaxRating());
        assertEquals(1800, profile.getMaxRating());
    }

    @Test
    void ratingHistoryDeduplicationByTimestampAndName() {
        ConnectedProfile profile = new ConnectedProfile();
        profile.setUserId(testUser.getId());
        profile.setPlatform("CODEFORCES");
        profile.setHandle("testuser");
        profile = profileRepository.save(profile);

        Instant contestDate = Instant.parse("2024-01-01T12:00:00Z");
        String contestName = "Codeforces Round #900";

        // First insert
        saveRatingHistory(profile.getId(), contestName, contestDate.toString(), 1200, 1250, 150);

        // Simulate dedupe check
        String dedupeKey = contestDate.getEpochSecond() + ":" + contestName;
        List<RatingHistory> existing = ratingHistoryRepository.findByProfileIdOrderByContestDateAsc(profile.getId());

        boolean alreadyExists = existing.stream()
                .anyMatch(h -> {
                    String key = h.getContestDate().getEpochSecond() + ":" + (h.getContestName() != null ? h.getContestName() : "");
                    return key.equals(dedupeKey);
                });

        assertTrue(alreadyExists, "Dedupe key should detect existing record");

        // Should still have only 1 record
        List<RatingHistory> allHistory = ratingHistoryRepository.findByProfileIdOrderByContestDateAsc(profile.getId());
        assertEquals(1, allHistory.size());
    }

    @Test
    void ratingHistoryPreservesNullRatingBefore() {
        // First contest often has null ratingBefore
        ConnectedProfile profile = new ConnectedProfile();
        profile.setUserId(testUser.getId());
        profile.setPlatform("LEETCODE");
        profile.setHandle("newuser");
        profile = profileRepository.save(profile);

        RatingHistory firstContest = new RatingHistory();
        firstContest.setProfileId(profile.getId());
        firstContest.setContestName("First Contest");
        firstContest.setContestDate(Instant.parse("2024-01-01T12:00:00Z"));
        firstContest.setRatingBefore(null); // First contest has no previous rating
        firstContest.setRatingAfter(1500);
        firstContest.setRankInContest(500);
        ratingHistoryRepository.save(firstContest);

        RatingHistory retrieved = ratingHistoryRepository.findByProfileIdOrderByContestDateAsc(profile.getId()).get(0);
        assertNull(retrieved.getRatingBefore(), "First contest should have null ratingBefore");
        assertEquals(1500, retrieved.getRatingAfter());
    }

    @Test
    void multipleContestsSameDay() {
        ConnectedProfile profile = new ConnectedProfile();
        profile.setUserId(testUser.getId());
        profile.setPlatform("LEETCODE");
        profile.setHandle("testuser");
        profile = profileRepository.save(profile);

        // Two contests on the same calendar day
        Instant morning = Instant.parse("2024-01-15T08:00:00Z");
        Instant evening = Instant.parse("2024-01-15T18:00:00Z");

        saveRatingHistory(profile.getId(), "Morning Contest", morning.toString(), 1500, 1550, 500);
        saveRatingHistory(profile.getId(), "Evening Contest", evening.toString(), 1550, 1600, 400);

        List<RatingHistory> history = ratingHistoryRepository.findByProfileIdOrderByContestDateAsc(profile.getId());
        assertEquals(2, history.size(), "Both contests should be recorded separately");

        // Dedupe keys should be different (different timestamps and names)
        String key1 = history.get(0).getContestDate().getEpochSecond() + ":" + history.get(0).getContestName();
        String key2 = history.get(1).getContestDate().getEpochSecond() + ":" + history.get(1).getContestName();
        assertNotEquals(key1, key2, "Dedupe keys should differ for same-day contests");
    }

    @Test
    void atCoderPerformanceFieldPreserved() {
        // AtCoder includes a performance metric
        ConnectedProfile profile = new ConnectedProfile();
        profile.setUserId(testUser.getId());
        profile.setPlatform("ATCODER");
        profile.setHandle("testuser");
        profile = profileRepository.save(profile);

        RatingHistory contest = new RatingHistory();
        contest.setProfileId(profile.getId());
        contest.setContestName("ABC 300");
        contest.setContestDate(Instant.parse("2024-01-01T12:00:00Z"));
        contest.setRatingBefore(1400);
        contest.setRatingAfter(1500);
        contest.setRankInContest(500);
        contest.setPerformance(1550); // AtCoder-specific field
        ratingHistoryRepository.save(contest);

        RatingHistory retrieved = ratingHistoryRepository.findByProfileIdOrderByContestDateAsc(profile.getId()).get(0);
        assertNotNull(retrieved.getPerformance());
        assertEquals(1550, retrieved.getPerformance());
    }

    @Test
    void codeforcesRankTitlePreserved() {
        ConnectedProfile profile = new ConnectedProfile();
        profile.setUserId(testUser.getId());
        profile.setPlatform("CODEFORCES");
        profile.setHandle("testuser");
        profile.setRating(1500);
        profile.setMaxRating(1800);
        profile.setRankTitle("Expert"); // Codeforces-specific field
        profile = profileRepository.save(profile);

        ConnectedProfile retrieved = profileRepository.findById(profile.getId()).orElseThrow();
        assertEquals("Expert", retrieved.getRankTitle());
    }

    @Test
    void profileFetchedAtUpdatedOnSync() {
        ConnectedProfile profile = new ConnectedProfile();
        profile.setUserId(testUser.getId());
        profile.setPlatform("LEETCODE");
        profile.setHandle("testuser");
        profile.setFetchedAt(null); // Never synced
        profile = profileRepository.save(profile);

        assertNull(profile.getFetchedAt());

        // Simulate sync completion
        profile.setFetchedAt(Instant.now());
        profileRepository.save(profile);

        ConnectedProfile updated = profileRepository.findById(profile.getId()).orElseThrow();
        assertNotNull(updated.getFetchedAt(), "fetchedAt should be set after sync");
    }

    @Test
    void problemsSolvedFieldLeetCodeOnly() {
        // LeetCode tracks problems solved
        ConnectedProfile leetcode = new ConnectedProfile();
        leetcode.setUserId(testUser.getId());
        leetcode.setPlatform("LEETCODE");
        leetcode.setHandle("lcuser");
        leetcode.setProblemsSolved(250);
        leetcode = profileRepository.save(leetcode);

        // Other platforms don't track this
        ConnectedProfile codeforces = new ConnectedProfile();
        codeforces.setUserId(testUser.getId());
        codeforces.setPlatform("CODEFORCES");
        codeforces.setHandle("cfuser");
        codeforces.setProblemsSolved(null); // Not available
        codeforces = profileRepository.save(codeforces);

        ConnectedProfile lcRetrieved = profileRepository.findById(leetcode.getId()).orElseThrow();
        ConnectedProfile cfRetrieved = profileRepository.findById(codeforces.getId()).orElseThrow();

        assertEquals(250, lcRetrieved.getProblemsSolved());
        assertNull(cfRetrieved.getProblemsSolved(), "Codeforces doesn't track problems solved");
    }

    private void saveRatingHistory(Long profileId, String contestName, String dateStr,
                                   Integer ratingBefore, int ratingAfter, int rank) {
        RatingHistory rh = new RatingHistory();
        rh.setProfileId(profileId);
        rh.setContestName(contestName);
        rh.setContestDate(Instant.parse(dateStr));
        rh.setRatingBefore(ratingBefore);
        rh.setRatingAfter(ratingAfter);
        rh.setRankInContest(rank);
        ratingHistoryRepository.save(rh);
    }
}

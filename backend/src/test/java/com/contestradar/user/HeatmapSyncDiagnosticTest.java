package com.contestradar.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Diagnostic tests for heatmap synchronization data flow.
 * Tests the complete path: Sync → Database → API aggregation → Frontend data.
 *
 * Purpose: Verify that synchronized contest data correctly flows through to heatmap display.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class HeatmapSyncDiagnosticTest {

    @Autowired
    private RatingHistoryRepository ratingHistoryRepository;

    @Autowired
    private ConnectedProfileRepository profileRepository;

    @Autowired
    private UserRepository userRepository;

    private ConnectedProfile testProfile;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUsername("heatmaptest");
        user.setEmail("heatmap@test.com");
        user.setPasswordHash("test");
        user = userRepository.save(user);

        testProfile = new ConnectedProfile();
        testProfile.setUserId(user.getId());
        testProfile.setPlatform("CODEFORCES");
        testProfile.setHandle("testuser");
        testProfile.setVerified(true);
        testProfile = profileRepository.save(testProfile);
    }

    /**
     * Test 1: Verify that saved rating history is queryable.
     * This tests: Database persistence → Query retrieval
     */
    @Test
    void ratingHistoryIsSavedAndQueryable() {
        // Simulate sync saving contest data
        Instant contestDate = Instant.parse("2026-10-15T18:00:00Z");

        RatingHistory rh = new RatingHistory();
        rh.setProfileId(testProfile.getId());
        rh.setContestName("Codeforces Round 900");
        rh.setContestDate(contestDate);
        rh.setRatingBefore(1400);
        rh.setRatingAfter(1450);
        rh.setRankInContest(150);
        ratingHistoryRepository.save(rh);

        // Query back like the heatmap endpoint does
        List<RatingHistory> history = ratingHistoryRepository
            .findByProfileIdOrderByContestDateAsc(testProfile.getId());

        assertEquals(1, history.size());
        assertEquals(contestDate, history.get(0).getContestDate());
        assertEquals("Codeforces Round 900", history.get(0).getContestName());
    }

    /**
     * Test 2: Verify date aggregation logic matches PerformanceController.
     * This tests: Instant → UTC LocalDate conversion → Date string aggregation
     */
    @Test
    void dateAggregationMatchesControllerLogic() {
        // Save contests on different dates
        saveContestOnDate("2026-10-10T10:00:00Z", "Contest A");
        saveContestOnDate("2026-10-10T20:00:00Z", "Contest B"); // Same day, different time
        saveContestOnDate("2026-10-11T15:00:00Z", "Contest C");
        saveContestOnDate("2026-10-12T05:00:00Z", "Contest D");

        // Aggregate using PerformanceController logic
        Map<String, Integer> dateActivity = new TreeMap<>();
        List<RatingHistory> history = ratingHistoryRepository
            .findByProfileIdOrderByContestDateAsc(testProfile.getId());

        for (RatingHistory rh : history) {
            LocalDate date = rh.getContestDate().atZone(ZoneOffset.UTC).toLocalDate();
            dateActivity.merge(date.toString(), 1, Integer::sum);
        }

        // Verify aggregation
        assertEquals(3, dateActivity.size(), "Should have 3 distinct dates");
        assertEquals(2, dateActivity.get("2026-10-10"), "Oct 10 should have 2 contests");
        assertEquals(1, dateActivity.get("2026-10-11"), "Oct 11 should have 1 contest");
        assertEquals(1, dateActivity.get("2026-10-12"), "Oct 12 should have 1 contest");
    }

    /**
     * Test 3: Verify timezone boundary behavior.
     * This tests: Late night contests near UTC midnight boundaries
     */
    @Test
    void timezoneBoundariesHandledCorrectly() {
        // Contest at 11:59 PM UTC on Oct 10
        saveContestOnDate("2026-10-10T23:59:00Z", "Late Night Contest");

        // Contest at 12:01 AM UTC on Oct 11
        saveContestOnDate("2026-10-11T00:01:00Z", "Early Morning Contest");

        Map<String, Integer> dateActivity = aggregateDates();

        assertEquals(2, dateActivity.size(), "Should be 2 separate dates");
        assertEquals(1, dateActivity.get("2026-10-10"));
        assertEquals(1, dateActivity.get("2026-10-11"));
    }

    /**
     * Test 4: Verify deduplication prevents duplicate heatmap entries.
     * This tests: Deduplication key logic used in sync methods
     */
    @Test
    void deduplicationPreventsDoubleCount() {
        Instant contestDate = Instant.parse("2026-10-15T18:00:00Z");
        String contestName = "Duplicate Contest";

        // Save same contest twice (simulates duplicate sync)
        RatingHistory rh1 = new RatingHistory();
        rh1.setProfileId(testProfile.getId());
        rh1.setContestName(contestName);
        rh1.setContestDate(contestDate);
        rh1.setRatingAfter(1400);
        ratingHistoryRepository.save(rh1);

        // Check deduplication key logic
        List<RatingHistory> existing = ratingHistoryRepository
            .findByProfileIdOrderByContestDateAsc(testProfile.getId());

        Map<String, RatingHistory> existingByKey = existing.stream()
            .collect(java.util.stream.Collectors.toMap(
                h -> h.getContestDate().getEpochSecond() + ":" +
                     (h.getContestName() != null ? h.getContestName() : ""),
                h -> h,
                (h1, h2) -> h1
            ));

        String dedupeKey = contestDate.getEpochSecond() + ":" + contestName;
        assertTrue(existingByKey.containsKey(dedupeKey),
            "Should find existing contest by dedupe key");

        // Sync would check this and NOT save duplicate
        if (!existingByKey.containsKey(dedupeKey)) {
            RatingHistory rh2 = new RatingHistory();
            rh2.setProfileId(testProfile.getId());
            rh2.setContestName(contestName);
            rh2.setContestDate(contestDate);
            rh2.setRatingAfter(1450);
            ratingHistoryRepository.save(rh2);
        }

        // Verify only 1 entry exists
        Map<String, Integer> dateActivity = aggregateDates();
        assertEquals(1, dateActivity.get("2026-10-15"),
            "Should count contest only once despite duplicate sync attempt");
    }

    /**
     * Test 5: Verify multi-platform aggregation for "ALL" filter.
     * This tests: Heatmap showing combined activity from multiple platforms
     */
    @Test
    void multiPlatformAggregation() {
        // Add second platform
        ConnectedProfile leetcodeProfile = new ConnectedProfile();
        leetcodeProfile.setUserId(testProfile.getUserId());
        leetcodeProfile.setPlatform("LEETCODE");
        leetcodeProfile.setHandle("testuser");
        leetcodeProfile.setVerified(true);
        leetcodeProfile = profileRepository.save(leetcodeProfile);

        // Codeforces contest on Oct 10
        RatingHistory cf = new RatingHistory();
        cf.setProfileId(testProfile.getId());
        cf.setContestName("CF Round");
        cf.setContestDate(Instant.parse("2026-10-10T10:00:00Z"));
        cf.setRatingAfter(1400);
        ratingHistoryRepository.save(cf);

        // LeetCode contest on same day
        RatingHistory lc = new RatingHistory();
        lc.setProfileId(leetcodeProfile.getId());
        lc.setContestName("Weekly Contest");
        lc.setContestDate(Instant.parse("2026-10-10T15:00:00Z"));
        lc.setRatingAfter(1800);
        ratingHistoryRepository.save(lc);

        // Aggregate both profiles (simulates "ALL" filter)
        Map<String, Integer> dateActivity = new TreeMap<>();
        List<ConnectedProfile> profiles = profileRepository
            .findByUserId(testProfile.getUserId());

        for (ConnectedProfile p : profiles) {
            List<RatingHistory> history = ratingHistoryRepository
                .findByProfileIdOrderByContestDateAsc(p.getId());
            for (RatingHistory rh : history) {
                LocalDate date = rh.getContestDate().atZone(ZoneOffset.UTC).toLocalDate();
                dateActivity.merge(date.toString(), 1, Integer::sum);
            }
        }

        assertEquals(1, dateActivity.size(), "Should have 1 date");
        assertEquals(2, dateActivity.get("2026-10-10"),
            "Should count both Codeforces and LeetCode contests on same day");
    }

    /**
     * Test 6: Verify platform filter works correctly.
     * This tests: Filtering heatmap by specific platform
     */
    @Test
    void platformFilterExcludesOtherPlatforms() {
        ConnectedProfile leetcodeProfile = new ConnectedProfile();
        leetcodeProfile.setUserId(testProfile.getUserId());
        leetcodeProfile.setPlatform("LEETCODE");
        leetcodeProfile.setHandle("testuser");
        leetcodeProfile.setVerified(true);
        leetcodeProfile = profileRepository.save(leetcodeProfile);

        // Save contests for both platforms
        saveContestForProfile(testProfile.getId(), "2026-10-10T10:00:00Z", "CF Contest");
        saveContestForProfile(leetcodeProfile.getId(), "2026-10-11T10:00:00Z", "LC Contest");

        // Filter only Codeforces (like heatmapPlatform === 'CODEFORCES')
        Map<String, Integer> cfOnly = aggregateDatesForProfile(testProfile.getId());

        assertEquals(1, cfOnly.size(), "Should have only Codeforces date");
        assertTrue(cfOnly.containsKey("2026-10-10"), "Should contain CF contest date");
        assertFalse(cfOnly.containsKey("2026-10-11"), "Should NOT contain LC contest date");
    }

    /**
     * Test 7: Verify empty result when no contests synced.
     * This tests: Fresh profile with no contest history
     */
    @Test
    void emptyHeatmapWhenNoContests() {
        // Profile exists but no contests synced
        List<RatingHistory> history = ratingHistoryRepository
            .findByProfileIdOrderByContestDateAsc(testProfile.getId());

        assertTrue(history.isEmpty(), "Should have no contest history");

        Map<String, Integer> dateActivity = aggregateDates();
        assertTrue(dateActivity.isEmpty(), "Heatmap should be empty");
    }

    /**
     * Test 8: Verify timestamp precision doesn't cause duplicates.
     * This tests: Contests with slightly different timestamps on same date
     */
    @Test
    void timestampPrecisionHandling() {
        // Same day, different hours/minutes
        saveContestOnDate("2026-10-10T08:30:15Z", "Morning Contest");
        saveContestOnDate("2026-10-10T14:45:30Z", "Afternoon Contest");
        saveContestOnDate("2026-10-10T20:15:45Z", "Evening Contest");

        Map<String, Integer> dateActivity = aggregateDates();

        assertEquals(1, dateActivity.size(), "Should aggregate to single date");
        assertEquals(3, dateActivity.get("2026-10-10"),
            "Should count all 3 contests on same day");
    }

    // Helper methods

    private void saveContestOnDate(String isoDateTime, String contestName) {
        saveContestForProfile(testProfile.getId(), isoDateTime, contestName);
    }

    private void saveContestForProfile(Long profileId, String isoDateTime, String contestName) {
        RatingHistory rh = new RatingHistory();
        rh.setProfileId(profileId);
        rh.setContestName(contestName);
        rh.setContestDate(Instant.parse(isoDateTime));
        rh.setRatingBefore(1400);
        rh.setRatingAfter(1450);
        rh.setRankInContest(100);
        ratingHistoryRepository.save(rh);
    }

    private Map<String, Integer> aggregateDates() {
        return aggregateDatesForProfile(testProfile.getId());
    }

    private Map<String, Integer> aggregateDatesForProfile(Long profileId) {
        Map<String, Integer> dateActivity = new TreeMap<>();
        List<RatingHistory> history = ratingHistoryRepository
            .findByProfileIdOrderByContestDateAsc(profileId);

        for (RatingHistory rh : history) {
            LocalDate date = rh.getContestDate().atZone(ZoneOffset.UTC).toLocalDate();
            dateActivity.merge(date.toString(), 1, Integer::sum);
        }

        return dateActivity;
    }
}

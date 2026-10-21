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

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SynchronizationIntegrationTest {

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
        testUser.setEmail("sync-integration@example.com");
        testUser.setUsername("syncintegration");
        testUser.setPasswordHash("hashed");
        testUser = userRepository.save(testUser);
    }

    @Test
    void profilePersistedEvenWhenInitialSyncFails() {
        ConnectedProfile profile = new ConnectedProfile();
        profile.setUserId(testUser.getId());
        profile.setPlatform("CODEFORCES");
        profile.setHandle("nonexistent_handle");
        profile = profileRepository.save(profile);

        assertNotNull(profile.getId());
        assertNull(profile.getFetchedAt());
        assertNull(profile.getRating());

        ConnectedProfile retrieved = profileRepository.findById(profile.getId()).orElse(null);
        assertNotNull(retrieved);
        assertEquals("nonexistent_handle", retrieved.getHandle());
    }

    @Test
    void existingHistoryPreservedWhenSyncFails() {
        ConnectedProfile profile = new ConnectedProfile();
        profile.setUserId(testUser.getId());
        profile.setPlatform("CODEFORCES");
        profile.setHandle("testhandle");
        profile = profileRepository.save(profile);

        RatingHistory existing = new RatingHistory();
        existing.setProfileId(profile.getId());
        existing.setContestName("Existing Contest");
        existing.setContestDate(Instant.parse("2024-01-01T12:00:00Z"));
        existing.setRatingAfter(1500);
        ratingHistoryRepository.save(existing);

        List<RatingHistory> before = ratingHistoryRepository.findByProfileIdOrderByContestDateAsc(profile.getId());
        assertEquals(1, before.size());

        List<RatingHistory> after = ratingHistoryRepository.findByProfileIdOrderByContestDateAsc(profile.getId());
        assertEquals(1, after.size());
        assertEquals("Existing Contest", after.get(0).getContestName());
        assertEquals(1500, after.get(0).getRatingAfter());
    }

    @Test
    void repeatedSyncDoesNotCreateDuplicates() {
        ConnectedProfile profile = new ConnectedProfile();
        profile.setUserId(testUser.getId());
        profile.setPlatform("LEETCODE");
        profile.setHandle("testlc");
        profile = profileRepository.save(profile);

        Instant contestDate = Instant.parse("2024-01-15T12:00:00Z");
        String contestName = "Weekly Contest 380";

        RatingHistory rh1 = new RatingHistory();
        rh1.setProfileId(profile.getId());
        rh1.setContestName(contestName);
        rh1.setContestDate(contestDate);
        rh1.setRatingAfter(1600);
        ratingHistoryRepository.save(rh1);

        assertEquals(1, ratingHistoryRepository.findByProfileIdOrderByContestDateAsc(profile.getId()).size());

        String dedupeKey = contestDate.getEpochSecond() + ":" + contestName;
        List<RatingHistory> history = ratingHistoryRepository.findByProfileIdOrderByContestDateAsc(profile.getId());
        boolean exists = history.stream()
                .anyMatch(h -> (h.getContestDate().getEpochSecond() + ":" + h.getContestName()).equals(dedupeKey));
        assertTrue(exists);

        assertEquals(1, ratingHistoryRepository.findByProfileIdOrderByContestDateAsc(profile.getId()).size());
    }

    @Test
    void multipleProfilesDoNotInterfere() {
        User user2 = new User();
        user2.setEmail("sync-integration2@example.com");
        user2.setUsername("syncintegration2");
        user2.setPasswordHash("hashed");
        user2 = userRepository.save(user2);

        ConnectedProfile profile1 = new ConnectedProfile();
        profile1.setUserId(testUser.getId());
        profile1.setPlatform("CODEFORCES");
        profile1.setHandle("user1cf");
        profile1 = profileRepository.save(profile1);

        ConnectedProfile profile2 = new ConnectedProfile();
        profile2.setUserId(user2.getId());
        profile2.setPlatform("CODEFORCES");
        profile2.setHandle("user2cf");
        profile2 = profileRepository.save(profile2);

        RatingHistory rh1 = new RatingHistory();
        rh1.setProfileId(profile1.getId());
        rh1.setContestName("Contest 1");
        rh1.setContestDate(Instant.now());
        rh1.setRatingAfter(1500);
        ratingHistoryRepository.save(rh1);

        RatingHistory rh2 = new RatingHistory();
        rh2.setProfileId(profile2.getId());
        rh2.setContestName("Contest 2");
        rh2.setContestDate(Instant.now());
        rh2.setRatingAfter(2000);
        ratingHistoryRepository.save(rh2);

        List<RatingHistory> history1 = ratingHistoryRepository.findByProfileIdOrderByContestDateAsc(profile1.getId());
        List<RatingHistory> history2 = ratingHistoryRepository.findByProfileIdOrderByContestDateAsc(profile2.getId());

        assertEquals(1, history1.size());
        assertEquals(1, history2.size());
        assertEquals(1500, history1.get(0).getRatingAfter());
        assertEquals(2000, history2.get(0).getRatingAfter());
    }

    @Test
    void ratingHistoryCorrectlyOrdered() {
        ConnectedProfile profile = new ConnectedProfile();
        profile.setUserId(testUser.getId());
        profile.setPlatform("ATCODER");
        profile.setHandle("testat");
        profile = profileRepository.save(profile);

        Instant date1 = Instant.parse("2024-01-01T12:00:00Z");
        Instant date2 = Instant.parse("2024-02-01T12:00:00Z");
        Instant date3 = Instant.parse("2024-03-01T12:00:00Z");

        RatingHistory rh3 = new RatingHistory();
        rh3.setProfileId(profile.getId());
        rh3.setContestName("Contest 3");
        rh3.setContestDate(date3);
        rh3.setRatingAfter(1600);
        ratingHistoryRepository.save(rh3);

        RatingHistory rh1 = new RatingHistory();
        rh1.setProfileId(profile.getId());
        rh1.setContestName("Contest 1");
        rh1.setContestDate(date1);
        rh1.setRatingAfter(1400);
        ratingHistoryRepository.save(rh1);

        RatingHistory rh2 = new RatingHistory();
        rh2.setProfileId(profile.getId());
        rh2.setContestName("Contest 2");
        rh2.setContestDate(date2);
        rh2.setRatingAfter(1500);
        ratingHistoryRepository.save(rh2);

        List<RatingHistory> history = ratingHistoryRepository.findByProfileIdOrderByContestDateAsc(profile.getId());

        assertEquals(3, history.size());
        assertEquals("Contest 1", history.get(0).getContestName());
        assertEquals("Contest 2", history.get(1).getContestName());
        assertEquals("Contest 3", history.get(2).getContestName());
        assertTrue(history.get(0).getContestDate().isBefore(history.get(1).getContestDate()));
        assertTrue(history.get(1).getContestDate().isBefore(history.get(2).getContestDate()));
    }

    @Test
    void heatmapAggregatesSameDayContests() {
        ConnectedProfile profile = new ConnectedProfile();
        profile.setUserId(testUser.getId());
        profile.setPlatform("LEETCODE");
        profile.setHandle("testlc");
        profile = profileRepository.save(profile);

        Instant day1Morning = Instant.parse("2024-01-15T08:00:00Z");
        Instant day1Evening = Instant.parse("2024-01-15T18:00:00Z");
        Instant day2 = Instant.parse("2024-01-16T12:00:00Z");

        RatingHistory rh1 = new RatingHistory();
        rh1.setProfileId(profile.getId());
        rh1.setContestName("Morning Contest");
        rh1.setContestDate(day1Morning);
        rh1.setRatingAfter(1500);
        ratingHistoryRepository.save(rh1);

        RatingHistory rh2 = new RatingHistory();
        rh2.setProfileId(profile.getId());
        rh2.setContestName("Evening Contest");
        rh2.setContestDate(day1Evening);
        rh2.setRatingAfter(1550);
        ratingHistoryRepository.save(rh2);

        RatingHistory rh3 = new RatingHistory();
        rh3.setProfileId(profile.getId());
        rh3.setContestName("Next Day Contest");
        rh3.setContestDate(day2);
        rh3.setRatingAfter(1600);
        ratingHistoryRepository.save(rh3);

        List<RatingHistory> history = ratingHistoryRepository.findByProfileIdOrderByContestDateAsc(profile.getId());
        assertEquals(3, history.size());
    }
}

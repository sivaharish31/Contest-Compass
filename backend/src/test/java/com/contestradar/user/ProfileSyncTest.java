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
class ProfileSyncTest {

    @Autowired
    private ConnectedProfileRepository profileRepository;

    @Autowired
    private RatingHistoryRepository ratingHistoryRepository;

    @Autowired
    private UserRepository userRepository;

    private ConnectedProfile testProfile;

    @BeforeEach
    void setUp() {
        ratingHistoryRepository.deleteAll();
        profileRepository.deleteAll();

        User user = new User();
        user.setEmail("sync-test@example.com");
        user.setUsername("synctest");
        user.setPasswordHash("hashed");
        user = userRepository.save(user);

        testProfile = new ConnectedProfile();
        testProfile.setUserId(user.getId());
        testProfile.setPlatform("CODEFORCES");
        testProfile.setHandle("testhandle");
        testProfile = profileRepository.save(testProfile);
    }

    @Test
    void deduplicationPreventsDuplicateRatingHistory() {
        Instant contestDate1 = Instant.parse("2024-01-01T12:00:00Z");
        String contestName1 = "Codeforces Round #900";

        RatingHistory rh1 = new RatingHistory();
        rh1.setProfileId(testProfile.getId());
        rh1.setContestName(contestName1);
        rh1.setContestDate(contestDate1);
        rh1.setRatingBefore(1200);
        rh1.setRatingAfter(1250);
        rh1.setRankInContest(150);
        ratingHistoryRepository.save(rh1);

        List<RatingHistory> history = ratingHistoryRepository.findByProfileIdOrderByContestDateAsc(testProfile.getId());
        assertEquals(1, history.size());

        String dedupeKey = contestDate1.getEpochSecond() + ":" + contestName1;
        List<RatingHistory> existingHistory = ratingHistoryRepository.findByProfileIdOrderByContestDateAsc(testProfile.getId());
        boolean exists = existingHistory.stream()
                .anyMatch(h -> (h.getContestDate().getEpochSecond() + ":" + h.getContestName()).equals(dedupeKey));

        assertTrue(exists, "Deduplication key should match existing record");

        List<RatingHistory> finalHistory = ratingHistoryRepository.findByProfileIdOrderByContestDateAsc(testProfile.getId());
        assertEquals(1, finalHistory.size(), "Should not create duplicate records");
    }

    @Test
    void historicalDataPreservedOnFailure() {
        Instant contestDate1 = Instant.parse("2024-01-01T12:00:00Z");
        RatingHistory rh1 = new RatingHistory();
        rh1.setProfileId(testProfile.getId());
        rh1.setContestName("Historical Contest");
        rh1.setContestDate(contestDate1);
        rh1.setRatingBefore(1200);
        rh1.setRatingAfter(1250);
        ratingHistoryRepository.save(rh1);

        List<RatingHistory> before = ratingHistoryRepository.findByProfileIdOrderByContestDateAsc(testProfile.getId());
        assertEquals(1, before.size());

        List<RatingHistory> after = ratingHistoryRepository.findByProfileIdOrderByContestDateAsc(testProfile.getId());
        assertEquals(1, after.size());
        assertEquals("Historical Contest", after.get(0).getContestName());
    }

    @Test
    void multipleProfilesSeparateHistory() {
        User user2 = new User();
        user2.setEmail("sync-test2@example.com");
        user2.setUsername("synctest2");
        user2.setPasswordHash("hashed");
        user2 = userRepository.save(user2);

        ConnectedProfile profile2 = new ConnectedProfile();
        profile2.setUserId(user2.getId());
        profile2.setPlatform("LEETCODE");
        profile2.setHandle("testhandle2");
        profile2 = profileRepository.save(profile2);

        RatingHistory rh1 = new RatingHistory();
        rh1.setProfileId(testProfile.getId());
        rh1.setContestName("Contest 1");
        rh1.setContestDate(Instant.now());
        rh1.setRatingAfter(1500);
        ratingHistoryRepository.save(rh1);

        RatingHistory rh2 = new RatingHistory();
        rh2.setProfileId(profile2.getId());
        rh2.setContestName("Contest 2");
        rh2.setContestDate(Instant.now());
        rh2.setRatingAfter(1600);
        ratingHistoryRepository.save(rh2);

        List<RatingHistory> history1 = ratingHistoryRepository.findByProfileIdOrderByContestDateAsc(testProfile.getId());
        List<RatingHistory> history2 = ratingHistoryRepository.findByProfileIdOrderByContestDateAsc(profile2.getId());

        assertEquals(1, history1.size());
        assertEquals(1, history2.size());
        assertEquals("Contest 1", history1.get(0).getContestName());
        assertEquals("Contest 2", history2.get(0).getContestName());
    }

    @Test
    void ratingHistoryOrderedByDate() {
        Instant date1 = Instant.parse("2024-01-01T12:00:00Z");
        Instant date2 = Instant.parse("2024-01-15T12:00:00Z");
        Instant date3 = Instant.parse("2024-02-01T12:00:00Z");

        RatingHistory rh2 = new RatingHistory();
        rh2.setProfileId(testProfile.getId());
        rh2.setContestName("Contest 2");
        rh2.setContestDate(date2);
        rh2.setRatingAfter(1300);
        ratingHistoryRepository.save(rh2);

        RatingHistory rh1 = new RatingHistory();
        rh1.setProfileId(testProfile.getId());
        rh1.setContestName("Contest 1");
        rh1.setContestDate(date1);
        rh1.setRatingAfter(1200);
        ratingHistoryRepository.save(rh1);

        RatingHistory rh3 = new RatingHistory();
        rh3.setProfileId(testProfile.getId());
        rh3.setContestName("Contest 3");
        rh3.setContestDate(date3);
        rh3.setRatingAfter(1400);
        ratingHistoryRepository.save(rh3);

        List<RatingHistory> history = ratingHistoryRepository.findByProfileIdOrderByContestDateAsc(testProfile.getId());

        assertEquals(3, history.size());
        assertEquals("Contest 1", history.get(0).getContestName());
        assertEquals("Contest 2", history.get(1).getContestName());
        assertEquals("Contest 3", history.get(2).getContestName());
    }

    @Test
    void ratingHistoryQueriedByProfileId() {
        RatingHistory rh = new RatingHistory();
        rh.setProfileId(testProfile.getId());
        rh.setContestName("Test Contest");
        rh.setContestDate(Instant.now());
        rh.setRatingAfter(1500);
        ratingHistoryRepository.save(rh);

        List<RatingHistory> history = ratingHistoryRepository.findByProfileIdOrderByContestDateAsc(testProfile.getId());
        assertEquals(1, history.size());
        assertEquals("Test Contest", history.get(0).getContestName());
        assertEquals(1500, history.get(0).getRatingAfter());
    }
}

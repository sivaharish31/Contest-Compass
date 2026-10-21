package com.contestradar.contest;

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
class ContestStatusTransitionTest {

    @Autowired
    private ContestSyncService syncService;

    @Autowired
    private ContestRepository contestRepository;

    @BeforeEach
    void cleanUp() {
        contestRepository.deleteAll();
    }

    @Test
    void liveContestTransitionsToFinishedWhenEndTimePassed() {
        Contest c = new Contest();
        c.setExternalId("transition-1");
        c.setPlatform(Platform.CODEFORCES);
        c.setName("Ended Contest");
        c.setStartTime(Instant.now().minus(3, ChronoUnit.HOURS));
        c.setEndTime(Instant.now().minus(1, ChronoUnit.HOURS));
        c.setDurationSeconds(7200L);
        c.setUrl("https://codeforces.com/contest/1");
        c.setStatus(ContestStatus.LIVE);
        contestRepository.save(c);

        syncService.updateContestStatuses();

        Contest updated = contestRepository.findByPlatformAndExternalId(Platform.CODEFORCES, "transition-1").orElseThrow();
        assertEquals(ContestStatus.FINISHED, updated.getStatus());
    }

    @Test
    void liveContestStaysLiveWhenStillRunning() {
        Contest c = new Contest();
        c.setExternalId("transition-2");
        c.setPlatform(Platform.CODEFORCES);
        c.setName("Running Contest");
        c.setStartTime(Instant.now().minus(1, ChronoUnit.HOURS));
        c.setEndTime(Instant.now().plus(1, ChronoUnit.HOURS));
        c.setDurationSeconds(7200L);
        c.setUrl("https://codeforces.com/contest/2");
        c.setStatus(ContestStatus.LIVE);
        contestRepository.save(c);

        syncService.updateContestStatuses();

        Contest updated = contestRepository.findByPlatformAndExternalId(Platform.CODEFORCES, "transition-2").orElseThrow();
        assertEquals(ContestStatus.LIVE, updated.getStatus());
    }

    @Test
    void upcomingContestTransitionsToLiveWhenStartTimePassed() {
        Contest c = new Contest();
        c.setExternalId("transition-3");
        c.setPlatform(Platform.CODEFORCES);
        c.setName("Started Contest");
        c.setStartTime(Instant.now().minus(30, ChronoUnit.MINUTES));
        c.setEndTime(Instant.now().plus(90, ChronoUnit.MINUTES));
        c.setDurationSeconds(7200L);
        c.setUrl("https://codeforces.com/contest/3");
        c.setStatus(ContestStatus.UPCOMING);
        contestRepository.save(c);

        syncService.updateContestStatuses();

        Contest updated = contestRepository.findByPlatformAndExternalId(Platform.CODEFORCES, "transition-3").orElseThrow();
        assertEquals(ContestStatus.LIVE, updated.getStatus());
    }

    @Test
    void finishedContestsExcludedFromDefaultQuery() {
        Contest finished = new Contest();
        finished.setExternalId("filter-1");
        finished.setPlatform(Platform.CODEFORCES);
        finished.setName("Old Contest");
        finished.setStartTime(Instant.now().minus(2, ChronoUnit.DAYS));
        finished.setEndTime(Instant.now().minus(2, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS));
        finished.setDurationSeconds(7200L);
        finished.setUrl("https://codeforces.com/contest/99");
        finished.setStatus(ContestStatus.FINISHED);
        contestRepository.save(finished);

        Contest upcoming = new Contest();
        upcoming.setExternalId("filter-2");
        upcoming.setPlatform(Platform.CODEFORCES);
        upcoming.setName("Future Contest");
        upcoming.setStartTime(Instant.now().plus(1, ChronoUnit.DAYS));
        upcoming.setEndTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS));
        upcoming.setDurationSeconds(7200L);
        upcoming.setUrl("https://codeforces.com/contest/100");
        upcoming.setStatus(ContestStatus.UPCOMING);
        contestRepository.save(upcoming);

        List<Contest> activeContests = contestRepository.findByStatusInOrderByStartTimeAsc(
                List.of(ContestStatus.UPCOMING, ContestStatus.LIVE));

        assertEquals(1, activeContests.size());
        assertEquals("Future Contest", activeContests.get(0).getName());
    }
}

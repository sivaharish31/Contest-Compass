package com.contestradar.contest;

import com.contestradar.provider.ContestData;
import com.contestradar.provider.ContestProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ContestSyncServiceTest {

    @Autowired
    private ContestSyncService syncService;

    @Autowired
    private ContestRepository contestRepository;

    @Autowired
    private ContestSyncStatusRepository syncStatusRepository;

    private final ContestProvider testProvider = new ContestProvider() {
        @Override
        public Platform platform() { return Platform.CODEFORCES; }

        @Override
        public List<ContestData> fetchContests() {
            Instant future = Instant.now().plus(1, ChronoUnit.DAYS);
            return List.of(
                    new ContestData("test-1", Platform.CODEFORCES, "Test Round 1",
                            future, future.plus(2, ChronoUnit.HOURS), 7200L,
                            "https://codeforces.com/contest/1", null, null, 1900),
                    new ContestData("test-1", Platform.CODEFORCES, "Test Round 1 (updated)",
                            future, future.plus(2, ChronoUnit.HOURS), 7200L,
                            "https://codeforces.com/contest/1", null, null, 2000),
                    new ContestData("test-2", Platform.CODEFORCES, "Test Round 2",
                            future.plus(3, ChronoUnit.DAYS), future.plus(3, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                            7200L, "https://codeforces.com/contest/2", null, null, null)
            );
        }
    };

    @BeforeEach
    void cleanUp() {
        contestRepository.deleteAll();
    }

    @Test
    void syncDeduplicatesByPlatformAndExternalId() {
        syncService.syncProvider(testProvider);

        List<Contest> all = contestRepository.findAll();
        assertEquals(2, all.size(), "Should deduplicate test-1 and keep test-2");

        Contest c1 = contestRepository.findByPlatformAndExternalId(Platform.CODEFORCES, "test-1").orElseThrow();
        assertEquals("Test Round 1 (updated)", c1.getName(), "Should use the latest data for duplicates");
        assertEquals(2000, c1.getRatingMax(), "Rating should be updated");
    }

    @Test
    void syncSetsUpcomingStatus() {
        syncService.syncProvider(testProvider);

        List<Contest> upcoming = contestRepository.findByStatusInOrderByStartTimeAsc(List.of(ContestStatus.UPCOMING));
        assertFalse(upcoming.isEmpty());
        for (Contest c : upcoming) {
            assertTrue(c.getStartTime().isAfter(Instant.now()));
        }
    }

    @Test
    void concurrentSyncAllCallsDoNotOverlap() throws InterruptedException {
        // Arrange: a slow provider that tracks concurrent execution.
        AtomicInteger concurrentCount = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);
        CountDownLatch firstStarted = new CountDownLatch(1);

        ContestProvider slowProvider = new ContestProvider() {
            @Override public Platform platform() { return Platform.LEETCODE; }

            @Override
            public List<ContestData> fetchContests() {
                int current = concurrentCount.incrementAndGet();
                maxConcurrent.updateAndGet(prev -> Math.max(prev, current));
                firstStarted.countDown();
                try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                concurrentCount.decrementAndGet();
                return List.of();
            }
        };

        // Call syncAll() twice concurrently from two threads.
        ExecutorService pool = Executors.newFixedThreadPool(2);
        ContestSyncService service = new ContestSyncService(
                List.of(slowProvider), contestRepository, syncStatusRepository);

        pool.submit(service::triggerSync);
        firstStarted.await(2, TimeUnit.SECONDS); // ensure the first call has started
        pool.submit(service::triggerSync);            // second call should be skipped

        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        // The guard should have prevented true concurrent execution.
        assertEquals(1, maxConcurrent.get(),
                "At most one sync run should execute at a time; concurrent = " + maxConcurrent.get());
    }

    @Test
    void syncAllFlagReleasedAfterException() {
        // If a provider throws, the flag must still be released.
        ContestProvider throwingProvider = new ContestProvider() {
            @Override public Platform platform() { return Platform.CODEFORCES; }
            @Override public List<ContestData> fetchContests() {
                throw new RuntimeException("simulated provider failure");
            }
        };
        ContestSyncService service = new ContestSyncService(
                List.of(throwingProvider), contestRepository, syncStatusRepository);

        service.triggerSync(); // must not hang or leave flag set
        assertFalse(service.isSyncInProgress(), "Flag must be released after exception");

        service.triggerSync(); // second call must succeed (flag was released)
        assertFalse(service.isSyncInProgress());
    }

    @Test
    void syncStoresAllFields() {
        syncService.syncProvider(testProvider);

        Contest c = contestRepository.findByPlatformAndExternalId(Platform.CODEFORCES, "test-2").orElseThrow();
        assertEquals("Test Round 2", c.getName());
        assertEquals(Platform.CODEFORCES, c.getPlatform());
        assertEquals(7200L, c.getDurationSeconds());
        assertNotNull(c.getUrl());
        assertNotNull(c.getLastSyncedAt());
        assertEquals(ContestStatus.UPCOMING, c.getStatus());
    }
}

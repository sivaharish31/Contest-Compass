package com.contestradar.contest;

import com.contestradar.provider.ContestData;
import com.contestradar.provider.ContestProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ContestSyncService {

    private static final Logger log = LoggerFactory.getLogger(ContestSyncService.class);

    private final List<ContestProvider> providers;
    private final ContestRepository contestRepository;
    private final ContestSyncStatusRepository syncStatusRepository;
    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);

    public ContestSyncService(List<ContestProvider> providers,
                              ContestRepository contestRepository,
                              ContestSyncStatusRepository syncStatusRepository) {
        this.providers = providers;
        this.contestRepository = contestRepository;
        this.syncStatusRepository = syncStatusRepository;
    }

    @Scheduled(fixedDelayString = "${contestradar.sync.interval-ms}",
               initialDelayString = "${contestradar.sync.initial-delay-ms:5000}")
    public void syncAllScheduled() {
        triggerSync();
    }

    /**
     * Attempts to start a sync run.
     *
     * @return {@code true} if a new sync was started, {@code false} if one was already in progress.
     */
    public boolean triggerSync() {
        if (!syncInProgress.compareAndSet(false, true)) {
            log.info("Sync already in progress — skipping overlapping invocation");
            return false;
        }
        try {
            log.info("Starting contest sync for {} providers", providers.size());
            for (ContestProvider provider : providers) {
                syncProvider(provider);
            }
            updateContestStatuses();
        } finally {
            syncInProgress.set(false);
        }
        return true;
    }

    /**
     * Returns true when a sync is currently running. Intended for tests only.
     */
    public boolean isSyncInProgress() {
        return syncInProgress.get();
    }

    @Transactional
    public void syncProvider(ContestProvider provider) {
        Platform platform = provider.platform();
        String platformName = platform != null ? platform.name() : "MOCK";
        log.info("Syncing contests from {}", platformName);

        try {
            List<ContestData> fetched = provider.fetchContests();
            int upserted = 0;

            for (ContestData data : fetched) {
                Platform contestPlatform = data.platform();
                Contest contest = contestRepository
                        .findByPlatformAndExternalId(contestPlatform, data.externalId())
                        .orElseGet(Contest::new);

                contest.setExternalId(data.externalId());
                contest.setPlatform(contestPlatform);
                contest.setName(data.name());
                contest.setStartTime(data.startTime());
                contest.setEndTime(data.endTime());
                contest.setDurationSeconds(data.durationSeconds());
                contest.setUrl(data.url());
                contest.setRegistrationUrl(data.registrationUrl());
                contest.setRatingMin(data.ratingMin());
                contest.setRatingMax(data.ratingMax());
                contest.setDataSource(platformName);
                contest.setLastSyncedAt(Instant.now());

                Instant now = Instant.now();
                if (data.endTime() != null && data.endTime().isBefore(now)) {
                    contest.setStatus(ContestStatus.FINISHED);
                } else if (data.startTime().isBefore(now)) {
                    contest.setStatus(ContestStatus.LIVE);
                } else {
                    contest.setStatus(ContestStatus.UPCOMING);
                }

                contestRepository.save(contest);
                upserted++;
            }

            if (platform != null) {
                updateSyncStatus(platform, "SUCCESS", null, upserted);
            }
            log.info("Synced {} contests from {}", upserted, platformName);
        } catch (Exception e) {
            log.error("Failed to sync from {}: {}", platformName, e.getMessage());
            if (platform != null) {
                updateSyncStatus(platform, "ERROR", e.getMessage(), 0);
            }
        }
    }

    @Transactional
    public void updateContestStatuses() {
        Instant now = Instant.now();

        // UPCOMING → LIVE or FINISHED
        List<Contest> upcomingPast = contestRepository.findByStatusAndStartTimeBefore(ContestStatus.UPCOMING, now);
        for (Contest c : upcomingPast) {
            if (c.getEndTime() != null && c.getEndTime().isBefore(now)) {
                c.setStatus(ContestStatus.FINISHED);
            } else {
                c.setStatus(ContestStatus.LIVE);
            }
            contestRepository.save(c);
        }

        // LIVE → FINISHED (end time passed)
        List<Contest> liveContests = contestRepository.findByStatusInOrderByStartTimeAsc(List.of(ContestStatus.LIVE));
        int finished = 0;
        for (Contest c : liveContests) {
            boolean ended = false;
            if (c.getEndTime() != null && c.getEndTime().isBefore(now)) {
                ended = true;
            } else if (c.getEndTime() == null && c.getDurationSeconds() != null
                       && c.getStartTime().plusSeconds(c.getDurationSeconds()).isBefore(now)) {
                ended = true;
            }
            if (ended) {
                c.setStatus(ContestStatus.FINISHED);
                contestRepository.save(c);
                finished++;
            }
        }

        int total = upcomingPast.size() + finished;
        if (total > 0) {
            log.info("Updated status for {} contests", total);
        }
    }

    private void updateSyncStatus(Platform platform, String status, String error, int count) {
        ContestSyncStatus syncStatus = syncStatusRepository.findByPlatform(platform)
                .orElseGet(() -> {
                    ContestSyncStatus s = new ContestSyncStatus();
                    s.setPlatform(platform);
                    return s;
                });
        syncStatus.setStatus(status);
        syncStatus.setErrorMessage(error);
        syncStatus.setContestsCount(count);
        syncStatus.setLastSyncedAt(Instant.now());
        syncStatusRepository.save(syncStatus);
    }
}

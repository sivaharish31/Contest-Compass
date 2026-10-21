package com.contestradar.contest;

import java.time.Instant;

public record ContestDto(
        Long id,
        String externalId,
        Platform platform,
        String name,
        Instant startTime,
        Instant endTime,
        Long durationSeconds,
        String url,
        String registrationUrl,
        ContestStatus status,
        Integer ratingMin,
        Integer ratingMax,
        String dataSource,
        String sourceConfidence,
        Instant lastSyncedAt,
        Boolean reminderSet
) {
    public static ContestDto from(Contest c, Boolean reminderSet) {
        return new ContestDto(
                c.getId(), c.getExternalId(), c.getPlatform(), c.getName(),
                c.getStartTime(), c.getEndTime(), c.getDurationSeconds(),
                c.getUrl(), c.getRegistrationUrl(), c.getStatus(),
                c.getRatingMin(), c.getRatingMax(),
                c.getDataSource(), c.getSourceConfidence(), c.getLastSyncedAt(),
                reminderSet
        );
    }

    public static ContestDto from(Contest c) {
        return from(c, null);
    }
}

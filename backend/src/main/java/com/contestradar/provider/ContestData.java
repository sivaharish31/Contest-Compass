package com.contestradar.provider;

import com.contestradar.contest.Platform;

import java.time.Instant;

public record ContestData(
        String externalId,
        Platform platform,
        String name,
        Instant startTime,
        Instant endTime,
        Long durationSeconds,
        String url,
        String registrationUrl,
        Integer ratingMin,
        Integer ratingMax
) {}

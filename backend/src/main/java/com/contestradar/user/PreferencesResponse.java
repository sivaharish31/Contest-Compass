package com.contestradar.user;

import java.time.Instant;

public record PreferencesResponse(
        Long id,
        Long userId,
        Boolean emailNotifications,
        Boolean browserNotifications,
        String preferredPlatforms,
        String preferredContestTimes,
        Instant createdAt,
        Instant updatedAt
) {
    public static PreferencesResponse from(UserPreferences preferences) {
        return new PreferencesResponse(
                preferences.getId(),
                preferences.getUserId(),
                preferences.getEmailNotifications(),
                preferences.getBrowserNotifications(),
                preferences.getPreferredPlatforms(),
                preferences.getPreferredContestTimes(),
                preferences.getCreatedAt(),
                preferences.getUpdatedAt()
        );
    }
}

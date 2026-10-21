package com.contestradar.user;

public record PreferencesRequest(
        Boolean emailNotifications,
        Boolean browserNotifications,
        String preferredPlatforms,
        String preferredContestTimes
) {}

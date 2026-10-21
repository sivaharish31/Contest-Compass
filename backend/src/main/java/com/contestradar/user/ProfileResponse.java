package com.contestradar.user;

import java.time.Instant;

public record ProfileResponse(
        Long id,
        String platform,
        String handle,
        Boolean verified,
        Integer rating,
        Integer maxRating,
        String rankTitle,
        Integer problemsSolved,
        Integer easySolved,
        Integer mediumSolved,
        Integer hardSolved,
        String profileUrl,
        String verificationMethod,
        Instant verificationExpiresAt,
        Instant verifiedAt,
        Instant fetchedAt,
        Instant createdAt,
        String lastSyncStatus,
        String lastSyncError
) {
    public static ProfileResponse from(ConnectedProfile profile) {
        return new ProfileResponse(
                profile.getId(),
                profile.getPlatform(),
                profile.getHandle(),
                profile.getVerified(),
                profile.getRating(),
                profile.getMaxRating(),
                profile.getRankTitle(),
                profile.getProblemsSolved(),
                profile.getEasySolved(),
                profile.getMediumSolved(),
                profile.getHardSolved(),
                profile.getProfileUrl(),
                profile.getVerificationMethod(),
                profile.getVerificationExpiresAt(),
                profile.getVerifiedAt(),
                profile.getFetchedAt(),
                profile.getCreatedAt(),
                profile.getLastSyncStatus(),
                profile.getLastSyncError()
        );
    }
}

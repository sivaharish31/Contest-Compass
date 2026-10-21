package com.contestradar.auth;

import com.contestradar.user.User;

public record UserResponse(
        Long id,
        String email,
        String username,
        String experienceLevel,
        String timezone,
        String avatarUrl
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getExperienceLevel(),
                user.getTimezone(),
                user.getAvatarPath() != null ? "/api/users/avatar/" + user.getId() : null
        );
    }
}

package com.contestradar.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public class AuthUtil {

    public static Optional<Long> currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal().equals("anonymousUser")) {
            return Optional.empty();
        }

        if (authentication.getPrincipal() instanceof CustomUserDetails customUserDetails) {
            return Optional.of(customUserDetails.getUserId());
        }

        return Optional.empty();
    }

    public static Long requireUserId() {
        return currentUserId()
                .orElseThrow(() -> new IllegalStateException("User not authenticated"));
    }
}

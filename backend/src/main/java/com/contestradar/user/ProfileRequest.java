package com.contestradar.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileRequest(
        @NotBlank(message = "Platform is required")
        @Size(max = 30, message = "Platform must not exceed 30 characters")
        String platform,

        @NotBlank(message = "Handle is required")
        @Size(max = 100, message = "Handle must not exceed 100 characters")
        String handle,

        @Size(max = 500, message = "Profile URL must not exceed 500 characters")
        String profileUrl
) {}

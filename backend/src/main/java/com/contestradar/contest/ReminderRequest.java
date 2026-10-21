package com.contestradar.contest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReminderRequest(
        @NotNull(message = "Contest ID is required")
        Long contestId,

        @NotNull(message = "Lead minutes is required")
        @Min(value = 1, message = "Lead minutes must be at least 1 minute")
        @Max(value = 2880, message = "Lead minutes cannot exceed 2 days (2880 minutes)")
        Integer leadMinutes
) {}

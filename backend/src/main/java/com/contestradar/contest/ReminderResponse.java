package com.contestradar.contest;

import java.time.Instant;

public record ReminderResponse(
        Long id,
        Long contestId,
        String contestName,
        Platform contestPlatform,
        Instant contestStartTime,
        Instant remindAt,
        Integer leadMinutes,
        String status,
        Instant createdAt
) {
    public static ReminderResponse from(Reminder reminder, Contest contest) {
        return new ReminderResponse(
                reminder.getId(),
                reminder.getContestId(),
                contest.getName(),
                contest.getPlatform(),
                contest.getStartTime(),
                reminder.getRemindAt(),
                reminder.getLeadMinutes(),
                reminder.getStatus(),
                reminder.getCreatedAt()
        );
    }
}

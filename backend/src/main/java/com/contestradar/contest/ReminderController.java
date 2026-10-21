package com.contestradar.contest;

import com.contestradar.auth.AuthUtil;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reminders")
public class ReminderController {

    private final ReminderRepository reminderRepository;
    private final ContestRepository contestRepository;

    public ReminderController(ReminderRepository reminderRepository,
                              ContestRepository contestRepository) {
        this.reminderRepository = reminderRepository;
        this.contestRepository = contestRepository;
    }

    @GetMapping
    public List<ReminderResponse> listReminders() {
        Long userId = AuthUtil.requireUserId();

        List<Reminder> reminders = reminderRepository.findByUserId(userId);
        List<Long> contestIds = reminders.stream()
                .map(Reminder::getContestId)
                .toList();

        if (contestIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Contest> contestMap = contestRepository.findAllById(contestIds).stream()
                .collect(Collectors.toMap(Contest::getId, c -> c));

        return reminders.stream()
                .filter(r -> contestMap.containsKey(r.getContestId()))
                .map(r -> ReminderResponse.from(r, contestMap.get(r.getContestId())))
                .collect(Collectors.toList());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createReminder(@Valid @RequestBody ReminderRequest request) {
        Long userId = AuthUtil.requireUserId();

        // Check if contest exists
        Contest contest = contestRepository.findById(request.contestId())
                .orElse(null);

        if (contest == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Contest not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        // Check if reminder already exists
        if (reminderRepository.existsByUserIdAndContestId(userId, request.contestId())) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Reminder already exists for this contest");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }

        // Calculate remind_at time
        Instant remindAt = contest.getStartTime().minusSeconds(request.leadMinutes() * 60L);

        Reminder reminder = new Reminder();
        reminder.setUserId(userId);
        reminder.setContestId(request.contestId());
        reminder.setLeadMinutes(request.leadMinutes());
        reminder.setRemindAt(remindAt);

        reminder = reminderRepository.save(reminder);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ReminderResponse.from(reminder, contest));
    }

    @DeleteMapping("/{contestId}")
    @Transactional
    public ResponseEntity<?> deleteReminder(@PathVariable Long contestId) {
        Long userId = AuthUtil.requireUserId();

        if (!reminderRepository.existsByUserIdAndContestId(userId, contestId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        reminderRepository.deleteByUserIdAndContestId(userId, contestId);
        return ResponseEntity.noContent().build();
    }
}

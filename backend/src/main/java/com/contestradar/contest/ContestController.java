package com.contestradar.contest;

import com.contestradar.auth.AuthUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/contests")
public class ContestController {

    private final ContestRepository contestRepository;
    private final ContestSyncStatusRepository syncStatusRepository;
    private final ContestSyncService syncService;
    private final ReminderRepository reminderRepository;

    public ContestController(ContestRepository contestRepository,
                             ContestSyncStatusRepository syncStatusRepository,
                             ContestSyncService syncService,
                             ReminderRepository reminderRepository) {
        this.contestRepository = contestRepository;
        this.syncStatusRepository = syncStatusRepository;
        this.syncService = syncService;
        this.reminderRepository = reminderRepository;
    }

    @GetMapping
    public Page<ContestDto> listContests(
            @RequestParam(defaultValue = "UPCOMING,LIVE") List<ContestStatus> status,
            @RequestParam(required = false) Platform platform,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Instant maxTime = Instant.now().plusSeconds(60L * 86400);
        Page<Contest> contests = contestRepository.findContestsWithinWindow(status, platform, search, maxTime, pageable);

        Optional<Long> userIdOpt = AuthUtil.currentUserId();

        if (userIdOpt.isEmpty()) {
            return contests.map(ContestDto::from);
        }

        Long userId = userIdOpt.get();
        List<Long> contestIds = contests.getContent().stream()
                .map(Contest::getId)
                .toList();

        Set<Long> reminderIds = reminderRepository.findByUserIdAndContestIdIn(userId, contestIds)
                .stream()
                .map(Reminder::getContestId)
                .collect(Collectors.toSet());

        return contests.map(c -> ContestDto.from(
                c,
                reminderIds.contains(c.getId())
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContestDto> getContest(@PathVariable Long id) {
        Optional<Contest> contestOpt = contestRepository.findById(id);
        if (contestOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Contest contest = contestOpt.get();
        Optional<Long> userIdOpt = AuthUtil.currentUserId();

        if (userIdOpt.isEmpty()) {
            return ResponseEntity.ok(ContestDto.from(contest));
        }

        Long userId = userIdOpt.get();
        boolean hasReminder = reminderRepository.existsByUserIdAndContestId(userId, id);

        return ResponseEntity.ok(ContestDto.from(contest, hasReminder));
    }

    @GetMapping("/sync-status")
    public List<ContestSyncStatus> getSyncStatus() {
        return syncStatusRepository.findAllByOrderByPlatformAsc();
    }

    @PostMapping("/sync")
    public ResponseEntity<Map<String, String>> triggerSync() {
        boolean started = syncService.triggerSync();
        if (started) {
            return ResponseEntity.accepted().body(Map.of("status", "started"));
        }
        return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
                .body(Map.of("status", "skipped", "reason", "A sync is already in progress"));
    }
}

package com.contestradar.user;

import com.contestradar.contest.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    private final ConnectedProfileRepository profileRepository;
    private final RatingHistoryRepository historyRepository;
    private final ContestRepository contestRepository;
    private final UserPreferencesRepository preferencesRepository;

    public RecommendationService(ConnectedProfileRepository profileRepository,
                                 RatingHistoryRepository historyRepository,
                                 ContestRepository contestRepository,
                                 UserPreferencesRepository preferencesRepository) {
        this.profileRepository = profileRepository;
        this.historyRepository = historyRepository;
        this.contestRepository = contestRepository;
        this.preferencesRepository = preferencesRepository;
    }

    public List<ContestRecommendation> recommend(Long userId) {
        List<ConnectedProfile> profiles = profileRepository.findByUserId(userId);
        UserPreferences prefs = preferencesRepository.findByUserId(userId).orElse(null);

        Set<String> preferredPlatforms = new HashSet<>();
        if (prefs != null && prefs.getPreferredPlatforms() != null) {
            for (String p : prefs.getPreferredPlatforms().split(",")) {
                if (!p.isBlank()) preferredPlatforms.add(p.trim());
            }
        }

        Map<String, ConnectedProfile> profileByPlatform = profiles.stream()
                .collect(Collectors.toMap(ConnectedProfile::getPlatform, p -> p, (a, b) -> a));

        // Compute user context
        UserContext ctx = buildContext(profiles);

        Instant now = Instant.now();
        Instant maxTime = now.plusSeconds(60L * 86400);
        // Bounded query: only fetch contests starting within the next 60 days.
        // This replaces the previous unbounded fetch + in-Java filter.
        List<Contest> upcoming = contestRepository
                .findByStatusInAndStartTimeBeforeOrEqualOrderByStartTimeAsc(
                        List.of(ContestStatus.UPCOMING, ContestStatus.LIVE), maxTime);

        List<ContestRecommendation> recs = new ArrayList<>();
        for (Contest c : upcoming) {

            int score = 0;
            List<String> reasons = new ArrayList<>();

            String platform = c.getPlatform().name();

            // Platform preference
            if (preferredPlatforms.contains(platform)) {
                score += 25;
                reasons.add("Your preferred platform");
            }

            ConnectedProfile profile = profileByPlatform.get(platform);
            if (profile != null) {
                score += 15;

                if (Boolean.TRUE.equals(profile.getVerified())) {
                    score += 10;
                    reasons.add("Verified profile");
                }

                // Rating-based recommendation
                if (profile.getRating() != null) {
                    if (c.getRatingMax() != null && profile.getRating() <= c.getRatingMax()) {
                        score += 15;
                        reasons.add("Matches your rating (" + profile.getRating() + ")");
                    } else if (c.getRatingMax() == null) {
                        score += 10;
                        reasons.add("Open to all ratings");
                    }
                }
            }

            // Time proximity bonus
            long hoursUntil = ChronoUnit.HOURS.between(now, c.getStartTime());
            if (hoursUntil >= 0 && hoursUntil < 24) {
                score += 15;
                reasons.add("Starting soon");
            } else if (hoursUntil >= 0 && hoursUntil < 72) {
                score += 5;
                reasons.add("Starting this week");
            }

            // Beginner recommendation
            if (ctx.isBeginner && c.getRatingMin() == null) {
                score += 10;
                reasons.add("Beginner-friendly");
            }

            // Trend-based
            if (ctx.isImproving && profile != null) {
                score += 5;
                reasons.add("Momentum builder");
            }
            if (ctx.isDeclining && profile != null && c.getRatingMin() == null) {
                score += 5;
                reasons.add("Practice opportunity");
            }

            if (score > 0) {
                if (reasons.isEmpty()) reasons.add("Matches your profile");
                recs.add(new ContestRecommendation(
                        ContestDto.from(c),
                        score,
                        reasons,
                        ctx.isBeginner ? "beginner" : ctx.isImproving ? "improving" : ctx.isDeclining ? "practice" : "active"
                ));
            }
        }

        recs.sort((a, b) -> Integer.compare(b.score(), a.score()));
        return recs.size() > 20 ? recs.subList(0, 20) : recs;
    }

    private UserContext buildContext(List<ConnectedProfile> profiles) {
        boolean isBeginner = true;
        boolean isImproving = false;
        boolean isDeclining = false;
        int totalContests = 0;

        for (ConnectedProfile p : profiles) {
            List<RatingHistory> history = historyRepository.findByProfileIdOrderByContestDateAsc(p.getId());
            totalContests += history.size();

            if (history.size() >= 3) {
                isBeginner = false;
                // Check recent trend (last 5 contests)
                List<RatingHistory> recent = history.subList(Math.max(0, history.size() - 5), history.size());
                int gains = 0, losses = 0;
                for (RatingHistory rh : recent) {
                    if (rh.getRatingBefore() != null) {
                        int change = rh.getRatingAfter() - rh.getRatingBefore();
                        if (change > 0) gains++;
                        else if (change < 0) losses++;
                    }
                }
                if (gains > losses) isImproving = true;
                if (losses > gains) isDeclining = true;
            }
        }

        if (totalContests == 0) isBeginner = true;

        return new UserContext(isBeginner, isImproving, isDeclining, totalContests);
    }

    record UserContext(boolean isBeginner, boolean isImproving, boolean isDeclining, int totalContests) {}

    public record ContestRecommendation(
            ContestDto contest,
            int score,
            List<String> reasons,
            String userLevel
    ) {}
}

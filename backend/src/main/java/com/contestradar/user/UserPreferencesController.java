package com.contestradar.user;

import com.contestradar.auth.AuthUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/preferences")
public class UserPreferencesController {

    private final UserPreferencesRepository preferencesRepository;

    public UserPreferencesController(UserPreferencesRepository preferencesRepository) {
        this.preferencesRepository = preferencesRepository;
    }

    @GetMapping
    public ResponseEntity<PreferencesResponse> getPreferences() {
        Long userId = AuthUtil.requireUserId();

        return preferencesRepository.findByUserId(userId)
                .map(prefs -> ResponseEntity.ok(PreferencesResponse.from(prefs)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping
    @Transactional
    public ResponseEntity<PreferencesResponse> updatePreferences(@RequestBody PreferencesRequest request) {
        Long userId = AuthUtil.requireUserId();

        UserPreferences preferences = preferencesRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserPreferences defaults = new UserPreferences();
                    defaults.setUserId(userId);
                    return preferencesRepository.save(defaults);
                });

        if (request.emailNotifications() != null) {
            preferences.setEmailNotifications(request.emailNotifications());
        }
        if (request.browserNotifications() != null) {
            preferences.setBrowserNotifications(request.browserNotifications());
        }
        if (request.preferredPlatforms() != null) {
            preferences.setPreferredPlatforms(request.preferredPlatforms());
        }
        if (request.preferredContestTimes() != null) {
            preferences.setPreferredContestTimes(request.preferredContestTimes());
        }

        preferences = preferencesRepository.save(preferences);
        return ResponseEntity.ok(PreferencesResponse.from(preferences));
    }
}

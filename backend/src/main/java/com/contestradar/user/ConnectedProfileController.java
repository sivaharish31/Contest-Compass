package com.contestradar.user;

import com.contestradar.auth.AuthUtil;
import com.contestradar.contest.Platform;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/profiles")
public class ConnectedProfileController {

    private final ConnectedProfileRepository profileRepository;
    private final ProfileVerificationService verificationService;

    public ConnectedProfileController(ConnectedProfileRepository profileRepository,
                                      ProfileVerificationService verificationService) {
        this.profileRepository = profileRepository;
        this.verificationService = verificationService;
    }

    @GetMapping
    public List<ProfileResponse> listProfiles() {
        Long userId = AuthUtil.requireUserId();

        return profileRepository.findByUserId(userId).stream()
                .map(ProfileResponse::from)
                .collect(Collectors.toList());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> addProfile(@Valid @RequestBody ProfileRequest request) {
        Long userId = AuthUtil.requireUserId();

        // Validate platform (optional - check against known platforms)
        String platform = request.platform().toUpperCase();
        try {
            Platform.valueOf(platform);
        } catch (IllegalArgumentException e) {
            // Not a known platform, but we'll allow it as the schema allows any string
        }

        // Check if profile already exists for this user and platform
        if (profileRepository.findByUserIdAndPlatform(userId, platform).isPresent()) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Profile already exists for this platform");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }

        // Check if this handle is already claimed by another user on this platform
        Optional<ConnectedProfile> existingClaim = profileRepository.findByPlatformAndHandle(platform, request.handle());
        if (existingClaim.isPresent()) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "This handle is already linked to another account");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }

        ConnectedProfile profile = new ConnectedProfile();
        profile.setUserId(userId);
        profile.setPlatform(platform);
        profile.setHandle(request.handle());
        profile.setProfileUrl(request.profileUrl());

        profile = profileRepository.save(profile);

        return ResponseEntity.status(HttpStatus.CREATED).body(ProfileResponse.from(profile));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> updateProfile(@PathVariable Long id, @Valid @RequestBody ProfileRequest request) {
        Long userId = AuthUtil.requireUserId();

        ConnectedProfile profile = profileRepository.findById(id).orElse(null);
        if (profile == null || !profile.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String newHandle = request.handle().trim();
        String platform = profile.getPlatform();

        // Check if handle changed and whether another account already claimed it
        if (!newHandle.equalsIgnoreCase(profile.getHandle())) {
            Optional<ConnectedProfile> existingClaim = profileRepository.findByPlatformAndHandle(platform, newHandle);
            if (existingClaim.isPresent() && !existingClaim.get().getId().equals(profile.getId())) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "This handle is already linked to another account");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
            }
            profile.setHandle(newHandle);
            profile.setVerified(false);
            profile.setVerificationToken(null);
            profile.setLastSyncStatus("PENDING");
            profile.setLastSyncError(null);
        }

        if (request.profileUrl() != null) {
            profile.setProfileUrl(request.profileUrl());
        }

        profile = profileRepository.save(profile);

        // Attempt data synchronization with the new handle if not CodeChef
        if (!"CODECHEF".equals(platform)) {
            try {
                verificationService.refreshProfileData(profile);
                profile = profileRepository.findById(id).orElse(profile);
            } catch (Exception ignored) {}
        }

        return ResponseEntity.ok(ProfileResponse.from(profile));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deleteProfile(@PathVariable Long id) {
        Long userId = AuthUtil.requireUserId();

        ConnectedProfile profile = profileRepository.findById(id)
                .orElse(null);

        if (profile == null || !profile.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        profileRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/verify/start")
    @Transactional
    public ResponseEntity<?> startVerification(@PathVariable Long id) {
        Long userId = AuthUtil.requireUserId();

        ConnectedProfile profile = profileRepository.findById(id).orElse(null);
        if (profile == null || !profile.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        if (Boolean.TRUE.equals(profile.getVerified())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Profile is already verified"));
        }

        String platform = profile.getPlatform();
        if ("CODECHEF".equals(platform)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "CodeChef does not support profile verification — no editable public profile field is available."));
        }

        try {
            String token = verificationService.generateVerificationChallenge(profile);

            String instructions = switch (platform) {
                case "CODEFORCES" -> "Add '" + token + "' to your Codeforces organization field at https://codeforces.com/settings/social, then click Check.";
                case "LEETCODE" -> "Add '" + token + "' to your LeetCode profile website field or summary at https://leetcode.com/profile/, then click Check.";
                case "ATCODER" -> "Add '" + token + "' to your AtCoder affiliation field at https://atcoder.jp/settings, then click Check.";
                default -> "Add '" + token + "' to your profile on the platform, then click Check.";
            };

            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
            result.put("method", profile.getVerificationMethod());
            result.put("instructions", instructions);
            result.put("expiresAt", profile.getVerificationExpiresAt());

            return ResponseEntity.ok(result);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/verify/check")
    @Transactional
    public ResponseEntity<?> checkVerification(@PathVariable Long id) {
        Long userId = AuthUtil.requireUserId();

        ConnectedProfile profile = profileRepository.findById(id).orElse(null);
        if (profile == null || !profile.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        ProfileVerificationService.VerificationResult result = verificationService.verifyProfile(profile);

        Map<String, Object> body = new HashMap<>();
        body.put("verified", result.verified());
        body.put("message", result.message());
        body.put("attemptsRemaining", result.attemptsRemaining());

        return ResponseEntity.ok(body);
    }

    @PostMapping("/{id}/refresh")
    @Transactional
    public ResponseEntity<?> refreshProfile(@PathVariable Long id) {
        Long userId = AuthUtil.requireUserId();

        ConnectedProfile profile = profileRepository.findById(id)
                .orElse(null);

        if (profile == null || !profile.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        verificationService.refreshProfileData(profile);

        // Refresh the profile to get updated sync status
        profile = profileRepository.findById(id).orElseThrow();
        ProfileResponse response = ProfileResponse.from(profile);

        // Check sync status and return appropriate response
        if ("FAILED".equals(profile.getLastSyncStatus())) {
            Map<String, Object> result = new HashMap<>();
            result.put("profile", response);
            result.put("syncStatus", "FAILED");
            result.put("syncError", profile.getLastSyncError());
            return ResponseEntity.ok(result); // Return 200 with error details
        } else if ("PARTIAL".equals(profile.getLastSyncStatus())) {
            Map<String, Object> result = new HashMap<>();
            result.put("profile", response);
            result.put("syncStatus", "PARTIAL");
            result.put("syncError", profile.getLastSyncError());
            return ResponseEntity.ok(result);
        }

        return ResponseEntity.ok(response);
    }
}

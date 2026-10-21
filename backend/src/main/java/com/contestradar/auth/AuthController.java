package com.contestradar.auth;

import com.contestradar.user.User;
import com.contestradar.user.UserPreferences;
import com.contestradar.user.UserPreferencesRepository;
import com.contestradar.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final UserPreferencesRepository preferencesRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthController(UserRepository userRepository,
                          UserPreferencesRepository preferencesRepository,
                          PasswordEncoder passwordEncoder,
                          AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.preferencesRepository = preferencesRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request,
                                       HttpServletRequest httpRequest) {
        if (userRepository.existsByEmail(request.email())) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Email already in use");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }

        if (userRepository.existsByUsername(request.username())) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Username already taken");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }

        User user = new User();
        user.setEmail(request.email());
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user = userRepository.save(user);

        UserPreferences preferences = new UserPreferences();
        preferences.setUserId(user.getId());
        preferencesRepository.save(preferences);

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequest request, HttpServletRequest httpRequest) {
        try {
            // Authenticate
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );

            // Create security context
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);

            // Store in session
            HttpSession session = httpRequest.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

            // Get user and return response
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            User user = userRepository.findById(userDetails.getUserId())
                    .orElseThrow(() -> new IllegalStateException("User not found"));

            return ResponseEntity.ok(UserResponse.from(user));
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid email or password");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        return AuthUtil.currentUserId()
                .flatMap(userRepository::findById)
                .map(user -> ResponseEntity.ok(UserResponse.from(user)))
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    private static final java.util.Set<String> VALID_EXPERIENCE_LEVELS =
            java.util.Set.of("BEGINNER", "INTERMEDIATE", "ADVANCED", "EXPERT");

    @PutMapping("/profile")
    @Transactional
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, String> request) {
        Long userId = AuthUtil.requireUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        String newUsername = request.get("username");
        if (newUsername != null && !newUsername.isBlank() && !newUsername.equals(user.getUsername())) {
            String trimmed = newUsername.trim();
            if (trimmed.length() < 3 || trimmed.length() > 50) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Username must be between 3 and 50 characters"));
            }
            if (userRepository.existsByUsername(trimmed)) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Username already taken"));
            }
            user.setUsername(trimmed);
        }

        String timezone = request.get("timezone");
        if (timezone != null && !timezone.isBlank()) {
            user.setTimezone(timezone.trim());
        }

        String experienceLevel = request.get("experienceLevel");
        if (experienceLevel != null && !experienceLevel.isBlank()) {
            String normalized = experienceLevel.trim().toUpperCase();
            if (!VALID_EXPERIENCE_LEVELS.contains(normalized)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error",
                                "experienceLevel must be one of: BEGINNER, INTERMEDIATE, ADVANCED, EXPERT"));
            }
            user.setExperienceLevel(normalized);
        }

        user = userRepository.save(user);
        return ResponseEntity.ok(UserResponse.from(user));
    }
}

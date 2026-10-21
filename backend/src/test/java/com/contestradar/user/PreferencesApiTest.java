package com.contestradar.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PreferencesApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserPreferencesRepository preferencesRepository;
    @Autowired private UserRepository userRepository;

    private MockHttpSession session;
    private Long userId;

    @BeforeEach
    void setUp() throws Exception {
        String email = "prefs-" + System.nanoTime() + "@test.com";
        String username = "prefsuser" + System.nanoTime();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "username", username, "password", "password123"))))
                .andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "password", "password123"))))
                .andExpect(status().isOk()).andReturn();
        session = (MockHttpSession) login.getRequest().getSession(false);

        userId = userRepository.findByEmail(email).orElseThrow().getId();
    }

    @Test
    void getPreferencesReturns200AfterRegistration() throws Exception {
        mockMvc.perform(get("/api/preferences").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailNotifications").value(true))
                .andExpect(jsonPath("$.browserNotifications").value(false));
    }

    @Test
    void updatePreferencesNormalFlow() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "emailNotifications", false,
                "browserNotifications", true,
                "preferredPlatforms", "LEETCODE,ATCODER"
        ));

        mockMvc.perform(put("/api/preferences").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailNotifications").value(false))
                .andExpect(jsonPath("$.browserNotifications").value(true))
                .andExpect(jsonPath("$.preferredPlatforms").value("LEETCODE,ATCODER"));
    }

    @Test
    void updatePreferencesPartialUpdatePreservesOtherFields() throws Exception {
        // Only update email notifications — platforms should remain default.
        String body = objectMapper.writeValueAsString(Map.of("emailNotifications", false));

        mockMvc.perform(put("/api/preferences").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailNotifications").value(false))
                .andExpect(jsonPath("$.preferredPlatforms").isNotEmpty());
    }

    @Test
    void omittingPreferredPlatformsPreservesExistingValue() throws Exception {
        // Arrange: set an initial platform preference.
        String setup = objectMapper.writeValueAsString(Map.of("preferredPlatforms", "CODEFORCES,ATCODER"));
        mockMvc.perform(put("/api/preferences").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(setup))
                .andExpect(status().isOk());

        // Act: save notification toggles WITHOUT sending preferredPlatforms (simulates
        // the frontend sending the request before prefs have finished loading).
        String notifOnly = "{\"emailNotifications\":false,\"browserNotifications\":true}";
        mockMvc.perform(put("/api/preferences").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(notifOnly))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailNotifications").value(false))
                .andExpect(jsonPath("$.browserNotifications").value(true))
                // preferredPlatforms must NOT have been erased
                .andExpect(jsonPath("$.preferredPlatforms").value("CODEFORCES,ATCODER"));
    }

    @Test
    void sendingEmptyStringPreferredPlatformsErasesValue() throws Exception {
        // Arrange: set an initial value.
        String setup = objectMapper.writeValueAsString(Map.of("preferredPlatforms", "CODEFORCES,LEETCODE"));
        mockMvc.perform(put("/api/preferences").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(setup))
                .andExpect(status().isOk());

        // Act: explicitly send an empty string (the old buggy behaviour).
        String emptyPlatforms = "{\"emailNotifications\":true,\"preferredPlatforms\":\"\"}";
        mockMvc.perform(put("/api/preferences").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emptyPlatforms))
                .andExpect(status().isOk())
                // An explicit empty string IS a valid update; it clears the preference.
                .andExpect(jsonPath("$.preferredPlatforms").value(""));
    }

    @Test
    void updatePreferencesWhenRowMissingCreatesDefaultsAndReturns200() throws Exception {
        // Simulate the missing-preferences scenario by deleting the row created at registration.
        preferencesRepository.findByUserId(userId).ifPresent(p ->
                preferencesRepository.deleteById(p.getId()));

        // After deletion, GET returns 404.
        mockMvc.perform(get("/api/preferences").session(session))
                .andExpect(status().isNotFound());

        // PUT should now auto-create defaults and return 200, not 500.
        String body = objectMapper.writeValueAsString(Map.of("emailNotifications", false));
        mockMvc.perform(put("/api/preferences").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailNotifications").value(false))
                .andExpect(jsonPath("$.userId").value(userId));

        // Subsequent GET should now find the newly-created row.
        mockMvc.perform(get("/api/preferences").session(session))
                .andExpect(status().isOk());
    }
}

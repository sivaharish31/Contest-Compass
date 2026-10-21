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

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ProfileVerificationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ConnectedProfileRepository profileRepository;
    @Autowired private RatingHistoryRepository ratingHistoryRepository;

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        ratingHistoryRepository.deleteAll();
        profileRepository.deleteAll();

        String email = "verify-" + System.nanoTime() + "@test.com";
        String username = "verifyuser" + System.nanoTime();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "username", username, "password", "password123"))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "password", "password123"))))
                .andExpect(status().isOk()).andReturn();
        session = (MockHttpSession) loginResult.getRequest().getSession(false);
    }

    @Test
    void verifyStartRejectsNonCodeforcesProfile() throws Exception {
        MvcResult addResult = mockMvc.perform(post("/api/profiles")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "platform", "CODECHEF", "handle", "testhandle"))))
                .andExpect(status().isCreated()).andReturn();

        Long profileId = objectMapper.readTree(addResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/profiles/" + profileId + "/verify/start")
                        .session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CodeChef does not support profile verification — no editable public profile field is available."));
    }

    @Test
    void verifyStartGeneratesToken() throws Exception {
        MvcResult addResult = mockMvc.perform(post("/api/profiles")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "platform", "CODEFORCES", "handle", "testuser1"))))
                .andExpect(status().isCreated()).andReturn();

        Long profileId = objectMapper.readTree(addResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/profiles/" + profileId + "/verify/start")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.method").value("CODEFORCES_ORGANIZATION"))
                .andExpect(jsonPath("$.instructions").exists());

        ConnectedProfile profile = profileRepository.findById(profileId).orElseThrow();
        assertNotNull(profile.getVerificationToken());
        assertNotNull(profile.getVerificationExpiresAt());
        assertTrue(profile.getVerificationExpiresAt().isAfter(Instant.now()));
        assertFalse(profile.getVerified());
    }

    @Test
    void verifyCheckFailsWithoutStarting() throws Exception {
        MvcResult addResult = mockMvc.perform(post("/api/profiles")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "platform", "CODEFORCES", "handle", "testuser2"))))
                .andExpect(status().isCreated()).andReturn();

        Long profileId = objectMapper.readTree(addResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/profiles/" + profileId + "/verify/check")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void verifyCheckFailsWithExpiredToken() throws Exception {
        MvcResult addResult = mockMvc.perform(post("/api/profiles")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "platform", "CODEFORCES", "handle", "testuser3"))))
                .andExpect(status().isCreated()).andReturn();

        Long profileId = objectMapper.readTree(addResult.getResponse().getContentAsString()).get("id").asLong();

        // Manually set expired token
        ConnectedProfile profile = profileRepository.findById(profileId).orElseThrow();
        profile.setVerificationToken("CR-expired1");
        profile.setVerificationMethod("CODEFORCES_ORGANIZATION");
        profile.setVerificationExpiresAt(Instant.now().minusSeconds(60));
        profileRepository.save(profile);

        mockMvc.perform(post("/api/profiles/" + profileId + "/verify/check")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false));

        // Token should be cleared
        ConnectedProfile updated = profileRepository.findById(profileId).orElseThrow();
        assertNull(updated.getVerificationToken());
    }

    @Test
    void unauthenticatedCannotAccessVerification() throws Exception {
        mockMvc.perform(post("/api/profiles/1/verify/start"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/profiles/1/verify/check"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cannotAccessOtherUsersProfile() throws Exception {
        // Create a profile
        MvcResult addResult = mockMvc.perform(post("/api/profiles")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "platform", "CODEFORCES", "handle", "testuser4"))))
                .andExpect(status().isCreated()).andReturn();
        Long profileId = objectMapper.readTree(addResult.getResponse().getContentAsString()).get("id").asLong();

        // Register a different user
        String email2 = "other-" + System.nanoTime() + "@test.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email2, "username", "other" + System.nanoTime(), "password", "password123"))))
                .andExpect(status().isCreated());
        MvcResult login2 = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email2, "password", "password123"))))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session2 = (MockHttpSession) login2.getRequest().getSession(false);

        // Other user cannot verify this profile
        mockMvc.perform(post("/api/profiles/" + profileId + "/verify/start")
                        .session(session2))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateHandleClaimRejected() throws Exception {
        // First user adds a profile
        mockMvc.perform(post("/api/profiles")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "platform", "CODEFORCES", "handle", "sharedhandle"))))
                .andExpect(status().isCreated());

        // Second user tries to claim the same handle
        String email2 = "dup-" + System.nanoTime() + "@test.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email2, "username", "dup" + System.nanoTime(), "password", "password123"))))
                .andExpect(status().isCreated());
        MvcResult login2 = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email2, "password", "password123"))))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session2 = (MockHttpSession) login2.getRequest().getSession(false);

        mockMvc.perform(post("/api/profiles")
                        .session(session2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "platform", "CODEFORCES", "handle", "sharedhandle"))))
                .andExpect(status().isConflict());
    }

    @Test
    void addProfileSucceedsWithoutExternalSync() throws Exception {
        // addProfile should return 201 immediately, without calling any external API.
        // The handle is deliberately nonexistent — any API call would fail.
        MvcResult result = mockMvc.perform(post("/api/profiles")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "platform", "CODEFORCES", "handle", "nonexistent_xyz_test"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.handle").value("nonexistent_xyz_test"))
                .andReturn();

        Long profileId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        // Profile persisted in DB
        ConnectedProfile profile = profileRepository.findById(profileId).orElseThrow();
        assertEquals("nonexistent_xyz_test", profile.getHandle());

        // No external data was fetched — fetchedAt and rating are null
        assertNull(profile.getFetchedAt());
        assertNull(profile.getRating());
    }

    @Test
    void profileSurvivesRefreshFailure() throws Exception {
        MvcResult addResult = mockMvc.perform(post("/api/profiles")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "platform", "CODEFORCES", "handle", "another_fake_handle"))))
                .andExpect(status().isCreated()).andReturn();

        Long profileId = objectMapper.readTree(addResult.getResponse().getContentAsString()).get("id").asLong();

        // Simulate what the frontend does: call refresh, which will fail for a fake handle
        mockMvc.perform(post("/api/profiles/" + profileId + "/refresh")
                        .session(session))
                .andExpect(status().isOk()); // refresh catches API errors internally

        // Profile must still exist
        assertTrue(profileRepository.findById(profileId).isPresent());
    }

    @Test
    void alreadyVerifiedProfileCannotRestart() throws Exception {
        MvcResult addResult = mockMvc.perform(post("/api/profiles")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "platform", "CODEFORCES", "handle", "testuser5"))))
                .andExpect(status().isCreated()).andReturn();

        Long profileId = objectMapper.readTree(addResult.getResponse().getContentAsString()).get("id").asLong();

        // Manually mark as verified
        ConnectedProfile profile = profileRepository.findById(profileId).orElseThrow();
        profile.setVerified(true);
        profile.setVerifiedAt(Instant.now());
        profileRepository.save(profile);

        // Attempt to start verification should return 409 Conflict
        mockMvc.perform(post("/api/profiles/" + profileId + "/verify/start")
                        .session(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Profile is already verified"));
    }
}

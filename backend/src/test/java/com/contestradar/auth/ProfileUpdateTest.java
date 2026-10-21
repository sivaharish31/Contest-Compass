package com.contestradar.auth;

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

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ProfileUpdateTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        String email = "pu-" + System.nanoTime() + "@test.com";
        String username = "puuser" + System.nanoTime();
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
    }

    private void putProfile(Map<String, String> body, int expectedStatus) throws Exception {
        mockMvc.perform(put("/api/auth/profile")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus));
    }

    // ── MED-01: experienceLevel validation ──────────────────────────────────

    @Test
    void updateExperienceLevelBeginner() throws Exception {
        mockMvc.perform(put("/api/auth/profile").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"experienceLevel\":\"BEGINNER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.experienceLevel").value("BEGINNER"));
    }

    @Test
    void updateExperienceLevelIntermediate() throws Exception {
        mockMvc.perform(put("/api/auth/profile").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"experienceLevel\":\"INTERMEDIATE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.experienceLevel").value("INTERMEDIATE"));
    }

    @Test
    void updateExperienceLevelAdvanced() throws Exception {
        mockMvc.perform(put("/api/auth/profile").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"experienceLevel\":\"ADVANCED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.experienceLevel").value("ADVANCED"));
    }

    @Test
    void updateExperienceLevelExpert() throws Exception {
        mockMvc.perform(put("/api/auth/profile").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"experienceLevel\":\"EXPERT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.experienceLevel").value("EXPERT"));
    }

    @Test
    void updateExperienceLevelCaseInsensitive() throws Exception {
        // lowercase is normalized to uppercase before validation
        mockMvc.perform(put("/api/auth/profile").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"experienceLevel\":\"beginner\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.experienceLevel").value("BEGINNER"));
    }

    @Test
    void updateExperienceLevelInvalidValueReturns400() throws Exception {
        mockMvc.perform(put("/api/auth/profile").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"experienceLevel\":\"HACKER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void updateExperienceLevelNullIsIgnored() throws Exception {
        // A null experienceLevel in JSON means absent — field is unchanged
        Map<String, Object> body = new HashMap<>();
        body.put("experienceLevel", null);
        mockMvc.perform(put("/api/auth/profile").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk()); // null is ignored, existing value preserved
    }

    @Test
    void updateExperienceLevelEmptyStringIsIgnored() throws Exception {
        // An empty string is treated as absent — field is unchanged
        mockMvc.perform(put("/api/auth/profile").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"experienceLevel\":\"\"}"))
                .andExpect(status().isOk());
    }

    // ── MED-02: username length validation ──────────────────────────────────

    @Test
    void updateUsernameValidLength() throws Exception {
        String validName = "validname" + System.nanoTime() % 10000;
        mockMvc.perform(put("/api/auth/profile").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + validName + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(validName));
    }

    @Test
    void updateUsernameTooShortReturns400() throws Exception {
        mockMvc.perform(put("/api/auth/profile").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ab\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void updateUsernameTooLongReturns400() throws Exception {
        String tooLong = "a".repeat(51);
        mockMvc.perform(put("/api/auth/profile").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void updateUsernameAtMaxBoundary50IsAccepted() throws Exception {
        String maxLen = "u" + "a".repeat(49); // 50 chars total, unique prefix
        mockMvc.perform(put("/api/auth/profile").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + maxLen + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateUsernameAtMinBoundary3IsAccepted() throws Exception {
        String minLen = "u" + System.nanoTime() % 10 + "x"; // 3 chars, unique
        mockMvc.perform(put("/api/auth/profile").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + minLen + "\"}"))
                .andExpect(status().isOk());
    }
}

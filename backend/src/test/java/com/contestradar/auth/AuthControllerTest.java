package com.contestradar.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.springframework.mock.web.MockHttpSession;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerAndLoginFlow() throws Exception {
        String registerBody = objectMapper.writeValueAsString(Map.of(
                "email", "test@example.com",
                "username", "testuser",
                "password", "password123"
        ));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.username").value("testuser"));

        String loginBody = objectMapper.writeValueAsString(Map.of(
                "email", "test@example.com",
                "password", "password123"
        ));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(get("/api/auth/me")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"));
    }

    @Test
    void meReturns401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginReturns401WithBadCredentials() throws Exception {
        String loginBody = objectMapper.writeValueAsString(Map.of(
                "email", "nobody@example.com",
                "password", "wrongpassword"
        ));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerRejects409DuplicateEmail() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "email", "dupe@example.com",
                "username", "dupeuser1",
                "password", "password123"
        ));
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        String body2 = objectMapper.writeValueAsString(Map.of(
                "email", "dupe@example.com",
                "username", "dupeuser2",
                "password", "password123"
        ));
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2))
                .andExpect(status().isConflict());
    }

    @Test
    void protectedEndpointReturns401WithoutAuth() throws Exception {
        mockMvc.perform(get("/api/saved-contests"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void contestsEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/contests"))
                .andExpect(status().isOk());
    }

    @Test
    void registerCreatesSessionAndAllowsMe() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "email", "regsess-" + System.nanoTime() + "@test.com",
                "username", "regsess" + System.nanoTime(),
                "password", "password123"
        ));

        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        MockHttpSession session = (MockHttpSession) regResult.getRequest().getSession(false);
        // Session must exist after register (auto-login)
        assert session != null : "Register should create a session";

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").exists());
    }

    @Test
    void registerAllowsCorsFromLocalhost() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "email", "cors-" + System.nanoTime() + "@test.com",
                "username", "corsuser" + System.nanoTime(),
                "password", "password123"
        ));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Origin", "http://localhost:5174")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5174"));
    }

    @Test
    void loginAllowsCorsFromLocalhost() throws Exception {
        String regBody = objectMapper.writeValueAsString(Map.of(
                "email", "corsl-" + System.nanoTime() + "@test.com",
                "username", "corsluser" + System.nanoTime(),
                "password", "password123"
        ));
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Origin", "http://localhost:5174")
                        .content(regBody))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5174"));
    }

    @Test
    void corsPreflightAllowedForAuthEndpoints() throws Exception {
        mockMvc.perform(options("/api/auth/register")
                        .header("Origin", "http://localhost:5174")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5174"))
                .andExpect(header().exists("Access-Control-Allow-Methods"));
    }
}

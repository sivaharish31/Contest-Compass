package com.contestradar.contest;

import com.contestradar.user.User;
import com.contestradar.user.UserRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ReminderValidationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ContestRepository contestRepository;
    @Autowired private UserRepository userRepository;

    private MockHttpSession session;
    private Long contestId;

    @BeforeEach
    void setUp() throws Exception {
        String email = "rv-" + System.nanoTime() + "@test.com";
        String username = "rvuser" + System.nanoTime();

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

        Contest c = new Contest();
        c.setExternalId("rv-test-" + System.nanoTime());
        c.setPlatform(Platform.CODEFORCES);
        c.setName("Reminder Validation Contest");
        c.setStartTime(Instant.now().plusSeconds(7200));
        c.setDurationSeconds(7200L);
        c.setUrl("https://codeforces.com/contest/rv");
        c.setStatus(ContestStatus.UPCOMING);
        contestId = contestRepository.save(c).getId();
    }

    private void postReminder(Object leadMinutes, int expectedStatus) throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("contestId", contestId, "leadMinutes", leadMinutes));
        mockMvc.perform(post("/api/reminders")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void leadMinutesOf1IsAccepted() throws Exception {
        postReminder(1, 201);
    }

    @Test
    void leadMinutesOfMaximum2880IsAccepted() throws Exception {
        postReminder(2880, 201);
    }

    @Test
    void leadMinutesOf2881ExceedsMaximumAndReturns400() throws Exception {
        postReminder(2881, 400);
    }

    @Test
    void leadMinutesOf0IsBelowMinimumAndReturns400() throws Exception {
        postReminder(0, 400);
    }

    @Test
    void leadMinutesOfIntegerMaxValueReturns400() throws Exception {
        postReminder(Integer.MAX_VALUE, 400);
    }
}

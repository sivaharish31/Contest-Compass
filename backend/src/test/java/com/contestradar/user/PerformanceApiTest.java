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

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PerformanceApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ConnectedProfileRepository profileRepository;
    @Autowired private RatingHistoryRepository ratingHistoryRepository;
    @Autowired private UserRepository userRepository;

    private MockHttpSession session;
    private User testUser;

    @BeforeEach
    void setUp() throws Exception {
        ratingHistoryRepository.deleteAll();
        profileRepository.deleteAll();

        String email = "perf-" + System.nanoTime() + "@test.com";
        String username = "perfuser" + System.nanoTime();
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

        testUser = userRepository.findByEmail(email).orElseThrow();
    }

    @Test
    void summaryIncludesEnhancedMetrics() throws Exception {
        ConnectedProfile cf = new ConnectedProfile();
        cf.setUserId(testUser.getId());
        cf.setPlatform("CODEFORCES");
        cf.setHandle("testcf");
        cf.setRating(1500);
        cf.setMaxRating(1600);
        cf.setFetchedAt(Instant.now());
        cf = profileRepository.save(cf);

        RatingHistory rh1 = new RatingHistory();
        rh1.setProfileId(cf.getId());
        rh1.setContestName("Contest 1");
        rh1.setContestDate(Instant.now().minusSeconds(10 * 86400));
        rh1.setRatingAfter(1500);
        ratingHistoryRepository.save(rh1);

        RatingHistory rh2 = new RatingHistory();
        rh2.setProfileId(cf.getId());
        rh2.setContestName("Contest 2");
        rh2.setContestDate(Instant.now().minusSeconds(5 * 86400));
        rh2.setRatingAfter(1550);
        ratingHistoryRepository.save(rh2);

        mockMvc.perform(get("/api/performance/summary").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectedProfiles").value(1))
                .andExpect(jsonPath("$.totalContests").value(2))
                .andExpect(jsonPath("$.contestsLast30Days").value(2))
                .andExpect(jsonPath("$.mostActivePlatform").value("CODEFORCES"))
                .andExpect(jsonPath("$.lastSyncedAt").exists())
                .andExpect(jsonPath("$.platforms[0].contestsLast30Days").value(2));
    }

    @Test
    void summaryCalculatesLast30DaysCorrectly() throws Exception {
        ConnectedProfile cf = new ConnectedProfile();
        cf.setUserId(testUser.getId());
        cf.setPlatform("CODEFORCES");
        cf.setHandle("testcf");
        cf = profileRepository.save(cf);

        RatingHistory old = new RatingHistory();
        old.setProfileId(cf.getId());
        old.setContestName("Old Contest");
        old.setContestDate(Instant.now().minusSeconds(40 * 86400));
        old.setRatingAfter(1400);
        ratingHistoryRepository.save(old);

        RatingHistory recent = new RatingHistory();
        recent.setProfileId(cf.getId());
        recent.setContestName("Recent Contest");
        recent.setContestDate(Instant.now().minusSeconds(5 * 86400));
        recent.setRatingAfter(1500);
        ratingHistoryRepository.save(recent);

        mockMvc.perform(get("/api/performance/summary").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalContests").value(2))
                .andExpect(jsonPath("$.contestsLast30Days").value(1));
    }

    @Test
    void heatmapFiltersByPlatform() throws Exception {
        ConnectedProfile cf = new ConnectedProfile();
        cf.setUserId(testUser.getId());
        cf.setPlatform("CODEFORCES");
        cf.setHandle("testcf");
        cf = profileRepository.save(cf);

        ConnectedProfile lc = new ConnectedProfile();
        lc.setUserId(testUser.getId());
        lc.setPlatform("LEETCODE");
        lc.setHandle("testlc");
        lc = profileRepository.save(lc);

        RatingHistory cfContest = new RatingHistory();
        cfContest.setProfileId(cf.getId());
        cfContest.setContestName("CF Contest");
        cfContest.setContestDate(Instant.parse("2024-01-15T12:00:00Z"));
        cfContest.setRatingAfter(1500);
        ratingHistoryRepository.save(cfContest);

        RatingHistory lcContest = new RatingHistory();
        lcContest.setProfileId(lc.getId());
        lcContest.setContestName("LC Contest");
        lcContest.setContestDate(Instant.parse("2024-01-15T12:00:00Z"));
        lcContest.setRatingAfter(1600);
        ratingHistoryRepository.save(lcContest);

        mockMvc.perform(get("/api/performance/heatmap").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platform").value("ALL"))
                .andExpect(jsonPath("$.entries[0].count").value(2));

        mockMvc.perform(get("/api/performance/heatmap?platform=CODEFORCES").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platform").value("CODEFORCES"))
                .andExpect(jsonPath("$.entries[0].count").value(1));

        mockMvc.perform(get("/api/performance/heatmap?platform=LEETCODE").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platform").value("LEETCODE"))
                .andExpect(jsonPath("$.entries[0].count").value(1));
    }

    @Test
    void ratingHistoryFiltersByDateRange() throws Exception {
        ConnectedProfile cf = new ConnectedProfile();
        cf.setUserId(testUser.getId());
        cf.setPlatform("CODEFORCES");
        cf.setHandle("testcf");
        cf = profileRepository.save(cf);

        RatingHistory old = new RatingHistory();
        old.setProfileId(cf.getId());
        old.setContestName("Old Contest");
        old.setContestDate(Instant.now().minusSeconds(100 * 86400));
        old.setRatingAfter(1400);
        ratingHistoryRepository.save(old);

        RatingHistory recent = new RatingHistory();
        recent.setProfileId(cf.getId());
        recent.setContestName("Recent Contest");
        recent.setContestDate(Instant.now().minusSeconds(20 * 86400));
        recent.setRatingAfter(1500);
        ratingHistoryRepository.save(recent);

        mockMvc.perform(get("/api/performance/history/CODEFORCES").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", hasSize(2)))
                .andExpect(jsonPath("$.dateRange").value("all"));

        mockMvc.perform(get("/api/performance/history/CODEFORCES?days=30").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", hasSize(1)))
                .andExpect(jsonPath("$.entries[0].contestName").value("Recent Contest"))
                .andExpect(jsonPath("$.dateRange").value("30 days"));
    }

    @Test
    void heatmapAggregatesDailyActivity() throws Exception {
        ConnectedProfile cf = new ConnectedProfile();
        cf.setUserId(testUser.getId());
        cf.setPlatform("CODEFORCES");
        cf.setHandle("testcf");
        cf = profileRepository.save(cf);

        Instant sameDay = Instant.parse("2024-01-15T10:00:00Z");

        RatingHistory rh1 = new RatingHistory();
        rh1.setProfileId(cf.getId());
        rh1.setContestName("Contest 1");
        rh1.setContestDate(sameDay);
        rh1.setRatingAfter(1500);
        ratingHistoryRepository.save(rh1);

        RatingHistory rh2 = new RatingHistory();
        rh2.setProfileId(cf.getId());
        rh2.setContestName("Contest 2");
        rh2.setContestDate(sameDay.plusSeconds(3600));
        rh2.setRatingAfter(1550);
        ratingHistoryRepository.save(rh2);

        mockMvc.perform(get("/api/performance/heatmap").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", hasSize(1)))
                .andExpect(jsonPath("$.entries[0].date").value("2024-01-15"))
                .andExpect(jsonPath("$.entries[0].count").value(2));
    }

    @Test
    void performanceDataScopedToUser() throws Exception {
        ConnectedProfile cf = new ConnectedProfile();
        cf.setUserId(testUser.getId());
        cf.setPlatform("CODEFORCES");
        cf.setHandle("testcf");
        cf = profileRepository.save(cf);

        User otherUser = new User();
        otherUser.setEmail("other" + System.nanoTime() + "@test.com");
        otherUser.setUsername("otheruser" + System.nanoTime());
        otherUser.setPasswordHash("hashed");
        otherUser = userRepository.save(otherUser);

        ConnectedProfile otherProfile = new ConnectedProfile();
        otherProfile.setUserId(otherUser.getId());
        otherProfile.setPlatform("CODEFORCES");
        otherProfile.setHandle("othercf");
        otherProfile = profileRepository.save(otherProfile);

        RatingHistory otherHistory = new RatingHistory();
        otherHistory.setProfileId(otherProfile.getId());
        otherHistory.setContestName("Other Contest");
        otherHistory.setContestDate(Instant.now());
        otherHistory.setRatingAfter(2000);
        ratingHistoryRepository.save(otherHistory);

        mockMvc.perform(get("/api/performance/summary").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectedProfiles").value(1))
                .andExpect(jsonPath("$.totalContests").value(0));

        mockMvc.perform(get("/api/performance/heatmap").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", hasSize(0)));
    }

    @Test
    void unauthenticatedCannotAccessPerformanceData() throws Exception {
        mockMvc.perform(get("/api/performance/summary"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/performance/heatmap"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/performance/history/CODEFORCES"))
                .andExpect(status().isUnauthorized());
    }
}

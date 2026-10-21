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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class HeatmapAndOverallApiTest {

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

        String email = "heatmap-" + System.nanoTime() + "@test.com";
        String username = "heatmapuser" + System.nanoTime();
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
    void allHistoryEndpointReturnsCombinedPlatformData() throws Exception {
        ConnectedProfile cf = createProfile("CODEFORCES", "testcf");
        ConnectedProfile lc = createProfile("LEETCODE", "testlc");

        createHistory(cf, "CF Round 1", "2024-06-01T10:00:00Z", 1500);
        createHistory(lc, "LC Weekly 1", "2024-06-02T14:00:00Z", 1600);
        createHistory(cf, "CF Round 2", "2024-06-03T10:00:00Z", 1550);

        mockMvc.perform(get("/api/performance/history/all").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platform").value("OVERALL"))
                .andExpect(jsonPath("$.entries", hasSize(3)))
                .andExpect(jsonPath("$.entries[0].platform").value("CODEFORCES"))
                .andExpect(jsonPath("$.entries[0].contestName").value("CF Round 1"))
                .andExpect(jsonPath("$.entries[1].platform").value("LEETCODE"))
                .andExpect(jsonPath("$.entries[2].platform").value("CODEFORCES"));
    }

    @Test
    void allHistoryEndpointSortedChronologically() throws Exception {
        ConnectedProfile cf = createProfile("CODEFORCES", "testcf");
        ConnectedProfile lc = createProfile("LEETCODE", "testlc");

        createHistory(cf, "Later Contest", "2024-07-15T10:00:00Z", 1500);
        createHistory(lc, "Earlier Contest", "2024-01-10T14:00:00Z", 1600);

        mockMvc.perform(get("/api/performance/history/all").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].contestName").value("Earlier Contest"))
                .andExpect(jsonPath("$.entries[1].contestName").value("Later Contest"));
    }

    @Test
    void allHistoryEndpointWithDateFilter() throws Exception {
        ConnectedProfile cf = createProfile("CODEFORCES", "testcf");

        createHistory(cf, "Old Contest", Instant.now().minusSeconds(100 * 86400).toString(), 1400);
        createHistory(cf, "Recent Contest", Instant.now().minusSeconds(10 * 86400).toString(), 1500);

        mockMvc.perform(get("/api/performance/history/all?days=30").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", hasSize(1)))
                .andExpect(jsonPath("$.entries[0].contestName").value("Recent Contest"));
    }

    @Test
    void allHistoryEndpointEmptyWhenNoData() throws Exception {
        createProfile("CODEFORCES", "testcf");

        mockMvc.perform(get("/api/performance/history/all").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platform").value("OVERALL"))
                .andExpect(jsonPath("$.entries", hasSize(0)));
    }

    @Test
    void allHistoryEndpointWorksWithSinglePlatform() throws Exception {
        ConnectedProfile cf = createProfile("CODEFORCES", "testcf");
        createHistory(cf, "Solo Contest", "2024-05-01T10:00:00Z", 1500);

        mockMvc.perform(get("/api/performance/history/all").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", hasSize(1)))
                .andExpect(jsonPath("$.entries[0].platform").value("CODEFORCES"));
    }

    @Test
    void heatmapDateBoundaryAtMidnightUTC() throws Exception {
        ConnectedProfile cf = createProfile("CODEFORCES", "testcf");

        createHistory(cf, "Late Night Contest", "2024-03-15T23:59:00Z", 1500);
        createHistory(cf, "Early Morning Contest", "2024-03-16T00:01:00Z", 1550);

        mockMvc.perform(get("/api/performance/heatmap").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", hasSize(2)))
                .andExpect(jsonPath("$.entries[0].date").value("2024-03-15"))
                .andExpect(jsonPath("$.entries[0].count").value(1))
                .andExpect(jsonPath("$.entries[1].date").value("2024-03-16"))
                .andExpect(jsonPath("$.entries[1].count").value(1));
    }

    @Test
    void heatmapCountsMultiplePlatformsSameDay() throws Exception {
        ConnectedProfile cf = createProfile("CODEFORCES", "testcf");
        ConnectedProfile lc = createProfile("LEETCODE", "testlc");

        createHistory(cf, "CF Contest", "2024-04-20T10:00:00Z", 1500);
        createHistory(lc, "LC Contest", "2024-04-20T14:00:00Z", 1600);

        mockMvc.perform(get("/api/performance/heatmap").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", hasSize(1)))
                .andExpect(jsonPath("$.entries[0].date").value("2024-04-20"))
                .andExpect(jsonPath("$.entries[0].count").value(2));
    }

    @Test
    void heatmapEmptyForPlatformWithNoHistory() throws Exception {
        createProfile("CODEFORCES", "testcf");

        mockMvc.perform(get("/api/performance/heatmap?platform=CODEFORCES").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", hasSize(0)));
    }

    @Test
    void profileResponseIncludesSyncStatus() throws Exception {
        ConnectedProfile cf = new ConnectedProfile();
        cf.setUserId(testUser.getId());
        cf.setPlatform("CODEFORCES");
        cf.setHandle("synctest");
        cf.setLastSyncStatus("FAILED");
        cf.setLastSyncError("Connection timeout");
        profileRepository.save(cf);

        mockMvc.perform(get("/api/profiles").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lastSyncStatus").value("FAILED"))
                .andExpect(jsonPath("$[0].lastSyncError").value("Connection timeout"));
    }

    @Test
    void profileResponseIncludesNullSyncStatusWhenNeverSynced() throws Exception {
        ConnectedProfile cf = new ConnectedProfile();
        cf.setUserId(testUser.getId());
        cf.setPlatform("CODEFORCES");
        cf.setHandle("newprofile");
        profileRepository.save(cf);

        mockMvc.perform(get("/api/profiles").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lastSyncStatus").doesNotExist())
                .andExpect(jsonPath("$[0].lastSyncError").doesNotExist());
    }

    @Test
    void allHistoryEndpointIsolatedToUser() throws Exception {
        ConnectedProfile cf = createProfile("CODEFORCES", "testcf");
        createHistory(cf, "My Contest", "2024-05-01T10:00:00Z", 1500);

        User otherUser = new User();
        otherUser.setEmail("other" + System.nanoTime() + "@test.com");
        otherUser.setUsername("other" + System.nanoTime());
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
        otherHistory.setContestDate(Instant.parse("2024-05-01T10:00:00Z"));
        otherHistory.setRatingAfter(2000);
        ratingHistoryRepository.save(otherHistory);

        mockMvc.perform(get("/api/performance/history/all").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", hasSize(1)))
                .andExpect(jsonPath("$.entries[0].contestName").value("My Contest"));
    }

    @Test
    void allHistoryEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/performance/history/all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void heatmapCombinesProblemsAndContestsWithPlatformBreakdown() throws Exception {
        ConnectedProfile lc = createProfile("LEETCODE", "lchandle");
        lc.setProblemsSolved(50);
        lc.setEasySolved(30);
        lc.setMediumSolved(20);
        lc.setHardSolved(0);
        lc.setSubmissionCalendar("{\"2024-06-15\": 5, \"2024-06-16\": 2}");
        profileRepository.save(lc);

        ConnectedProfile cf = createProfile("CODEFORCES", "cfhandle");
        profileRepository.save(cf);
        createHistory(cf, "CF Div 2", "2024-06-15T14:30:00Z", 1600);

        mockMvc.perform(get("/api/performance/heatmap").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", hasSize(2)))
                // 2024-06-15: 5 problems + 1 contest = count 6
                .andExpect(jsonPath("$.entries[0].date").value("2024-06-15"))
                .andExpect(jsonPath("$.entries[0].count").value(6))
                .andExpect(jsonPath("$.entries[0].problemsSolved").value(5))
                .andExpect(jsonPath("$.entries[0].contestsAttended").value(1))
                .andExpect(jsonPath("$.entries[0].platforms.LEETCODE.problemsSolved").value(5))
                .andExpect(jsonPath("$.entries[0].platforms.CODEFORCES.contestsAttended").value(1))
                .andExpect(jsonPath("$.entries[0].details", hasSize(2)))
                // 2024-06-16: 2 problems + 0 contests = count 2
                .andExpect(jsonPath("$.entries[1].date").value("2024-06-16"))
                .andExpect(jsonPath("$.entries[1].count").value(2))
                .andExpect(jsonPath("$.entries[1].problemsSolved").value(2))
                .andExpect(jsonPath("$.entries[1].contestsAttended").value(0));
    }

    @Test
    void summaryIncludesEasyMediumHardBreakdown() throws Exception {
        ConnectedProfile lc = createProfile("LEETCODE", "lchandle");
        lc.setProblemsSolved(172);
        lc.setEasySolved(140);
        lc.setMediumSolved(30);
        lc.setHardSolved(2);
        profileRepository.save(lc);

        ConnectedProfile cf = createProfile("CODEFORCES", "cfhandle");
        profileRepository.save(cf);

        mockMvc.perform(get("/api/performance/summary").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEasySolved").value(140))
                .andExpect(jsonPath("$.totalMediumSolved").value(30))
                .andExpect(jsonPath("$.totalHardSolved").value(2))
                .andExpect(jsonPath("$.hasDifficultyBreakdown").value(true))
                .andExpect(jsonPath("$.platforms[?(@.platform == 'LEETCODE')].hasDifficultyBreakdown").value(contains(true)))
                .andExpect(jsonPath("$.platforms[?(@.platform == 'CODEFORCES')].hasDifficultyBreakdown").value(contains(false)));
    }

    private ConnectedProfile createProfile(String platform, String handle) {
        ConnectedProfile p = new ConnectedProfile();
        p.setUserId(testUser.getId());
        p.setPlatform(platform);
        p.setHandle(handle);
        return profileRepository.save(p);
    }

    private void createHistory(ConnectedProfile profile, String name, String dateISO, int ratingAfter) {
        RatingHistory rh = new RatingHistory();
        rh.setProfileId(profile.getId());
        rh.setContestName(name);
        rh.setContestDate(Instant.parse(dateISO));
        rh.setRatingAfter(ratingAfter);
        ratingHistoryRepository.save(rh);
    }
}

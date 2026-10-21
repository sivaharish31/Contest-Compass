package com.contestradar.contest;

import com.contestradar.provider.AtCoderProvider;
import com.contestradar.provider.CodeChefProvider;
import com.contestradar.provider.CodeforcesProvider;
import com.contestradar.provider.ContestData;
import com.contestradar.provider.ContestProvider;
import com.contestradar.provider.LeetCodeProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-level tests for POST /api/contests/sync.
 *
 * <h3>Provider isolation</h3>
 * <p>The four real provider beans ({@link CodeforcesProvider}, {@link LeetCodeProvider},
 * {@link AtCoderProvider}, {@link CodeChefProvider}) are replaced by Mockito mocks via
 * {@code @MockBean}.  Mockito's default answer returns an empty {@code List} for
 * {@code fetchContests()}, so no outbound HTTP calls are made in any test in this class.
 *
 * <h3>Blocking provider</h3>
 * <p>A static inner {@code @TestConfiguration} contributes a {@link BlockingContestProvider}
 * bean.  It implements {@link Ordered} with {@code Integer.MIN_VALUE} so Spring places it
 * first in the {@code List<ContestProvider>} injected into {@link ContestSyncService}
 * (mocked real providers follow in undefined order).
 *
 * <p>The blocking provider is "disarmed" by default (latches are null) so that fast-path
 * tests complete in milliseconds.  The 409 concurrency test arms it with fresh
 * {@link CountDownLatch} instances, then disarms it in a {@code finally} block.
 * {@code @AfterEach} disarms defensively after every test.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ContestSyncControllerTest {

    // ── Mock out all real providers — no network calls in any test ──

    @MockBean CodeforcesProvider codeforcesProvider;
    @MockBean LeetCodeProvider   leetCodeProvider;
    @MockBean AtCoderProvider    atCoderProvider;
    @MockBean CodeChefProvider   codeChefProvider;

    // ── Inner @TestConfiguration — auto-detected by Spring Boot's test infrastructure ──

    /**
     * A {@link ContestProvider} that blocks inside {@code fetchContests()} when
     * armed with latches.  When disarmed (latches null) it returns immediately.
     *
     * <p>Implements {@link Ordered} with {@code Integer.MIN_VALUE} so that Spring
     * places this provider first in the injected {@code List<ContestProvider>}.
     */
    static class BlockingContestProvider implements ContestProvider, Ordered {

        private volatile CountDownLatch enteredLatch;
        private volatile CountDownLatch releaseLatch;

        void arm(CountDownLatch entered, CountDownLatch release) {
            this.enteredLatch = entered;
            this.releaseLatch = release;
        }

        /**
         * Disarms and releases any thread blocked inside {@code fetchContests()}.
         * Safe to call when already disarmed (no-op).
         */
        void disarm() {
            CountDownLatch release = this.releaseLatch;
            if (release != null) {
                release.countDown();
            }
            this.enteredLatch = null;
            this.releaseLatch = null;
        }

        @Override
        public Platform platform() { return null; }

        @Override
        public List<ContestData> fetchContests() {
            CountDownLatch entered = this.enteredLatch;
            CountDownLatch release = this.releaseLatch;
            if (entered != null) {
                entered.countDown();
            }
            if (release != null) {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return List.of();
        }

        @Override
        public int getOrder() { return Integer.MIN_VALUE; }
    }

    @TestConfiguration
    static class BlockingProviderConfig {
        @Bean
        BlockingContestProvider blockingContestProvider() {
            return new BlockingContestProvider();
        }
    }

    // ── Autowired fields ──

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ContestSyncService syncService;
    @Autowired private BlockingContestProvider blockingProvider;

    // ── Lifecycle ──

    @AfterEach
    void disarmProvider() {
        blockingProvider.disarm();
    }

    // ── Helper ──

    private MockHttpSession authenticatedSession() throws Exception {
        String email = "sync-ctrl-" + System.nanoTime() + "@test.com";
        String user  = "syncctrl"   + System.nanoTime();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "username", user, "password", "password123"))))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "password", "password123"))))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    // ── Tests ──

    @Test
    void triggerSyncReturns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/contests/sync"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Blocking provider is disarmed; all five providers return immediately.
     * No outbound HTTP calls. Sync completes in milliseconds.
     */
    @Test
    void triggerSyncReturns202WhenNoSyncRunning() throws Exception {
        MockHttpSession session = authenticatedSession();

        mockMvc.perform(post("/api/contests/sync").session(session))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("started"));
    }

    /**
     * Deterministic 409 test — no real external network calls in any phase.
     *
     * <p>Sequence:
     * <ol>
     *   <li>Arm the blocking provider with fresh latches.
     *   <li>Submit {@code syncService.triggerSync()} to a background thread.  It
     *       acquires the flag and blocks inside {@code BlockingContestProvider.fetchContests()}.
     *   <li>Await {@code enteredLatch} — confirms the flag is held.
     *   <li>Assert {@code isSyncInProgress()} is {@code true}.
     *   <li>Issue the competing HTTP request; assert 409 with the expected body.
     *   <li>{@code finally}: release the blocking provider, disarm, shut down the
     *       executor, await termination (all mocked providers return instantly, so
     *       the background sync completes in well under 1 s), and observe the Future.
     * </ol>
     *
     * <p>No {@code Thread.sleep()} is used.  After the blocking provider is released,
     * the sync iterates the remaining mocked providers (all return empty lists immediately).
     * Zero outbound HTTP calls occur in any phase of this test.
     */
    @Test
    void triggerSyncReturns409WhenSyncAlreadyRunning() throws Exception {
        MockHttpSession session = authenticatedSession();

        CountDownLatch enteredLatch = new CountDownLatch(1);
        CountDownLatch releaseLatch = new CountDownLatch(1);
        blockingProvider.arm(enteredLatch, releaseLatch);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> syncFuture = executor.submit(syncService::triggerSync);

        try {
            boolean entered = enteredLatch.await(5, TimeUnit.SECONDS);
            assertTrue(entered,
                    "Blocking provider was not entered within 5 seconds");

            assertTrue(syncService.isSyncInProgress(),
                    "syncInProgress flag must be true while provider is blocking");

            mockMvc.perform(post("/api/contests/sync").session(session))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value("skipped"))
                    .andExpect(jsonPath("$.reason").isNotEmpty());

        } finally {
            releaseLatch.countDown();
            blockingProvider.disarm();

            executor.shutdown();
            boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);
            if (!finished) {
                fail("Background sync executor did not terminate within 10 seconds");
            }

            try {
                Boolean started = syncFuture.get(0, TimeUnit.SECONDS);
                assertTrue(started,
                        "Background triggerSync() should have returned true");
            } catch (java.util.concurrent.TimeoutException ignored) {
                // awaitTermination timed out above — already reported.
            } catch (java.util.concurrent.ExecutionException e) {
                fail("Background triggerSync() threw unexpectedly: " + e.getCause());
            }
        }
    }

    @Test
    void triggerSyncResponseBodyDistinguishesStartedFromSkipped() throws Exception {
        MockHttpSession session = authenticatedSession();

        MvcResult result = mockMvc.perform(post("/api/contests/sync").session(session))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        var node = objectMapper.readTree(body);

        assertTrue(node.has("status"), "Response must contain 'status' field");
        String status = node.get("status").asText();
        assertTrue("started".equals(status) || "skipped".equals(status),
                "status must be 'started' or 'skipped', got: " + status);

        if ("skipped".equals(status)) {
            assertTrue(node.has("reason"), "Skipped response must contain 'reason' field");
            assertFalse(node.get("reason").asText().isBlank(), "reason must not be empty");
        }
    }
}

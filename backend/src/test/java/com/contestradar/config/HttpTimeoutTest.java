package com.contestradar.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class HttpTimeoutTest {

    @Autowired
    private RestClient.Builder restClientBuilder;

    private ServerSocket stallServer;
    private ExecutorService executor;

    @BeforeEach
    void startStallServer() throws IOException {
        stallServer = new ServerSocket(0); // bind to random free port
        executor = Executors.newSingleThreadExecutor();
        // Accept connections but never write a response — simulates a stalled upstream.
        executor.submit(() -> {
            while (!stallServer.isClosed()) {
                try {
                    Socket client = stallServer.accept();
                    // Intentionally do nothing — holds the connection open
                    client.setSoTimeout(60_000);
                } catch (IOException ignored) {
                    // Expected when stallServer is closed in @AfterEach
                }
            }
            return null;
        });
    }

    @AfterEach
    void stopStallServer() throws IOException {
        stallServer.close();
        executor.shutdownNow();
    }

    @Test
    void restClientTimesOutWhenServerStalls() {
        int port = stallServer.getLocalPort();
        RestClient client = restClientBuilder
                .baseUrl("http://localhost:" + port)
                .build();

        long start = System.currentTimeMillis();

        // The call must throw because the server accepts but never responds.
        // With a 30-second read timeout this would take 30s; we override to 1.5s
        // via the test profile property (read-timeout-ms=1500) to keep the test fast.
        assertThrows(ResourceAccessException.class,
                () -> client.get().uri("/test").retrieve().body(String.class),
                "Expected ResourceAccessException due to read timeout");

        long elapsed = System.currentTimeMillis() - start;
        // Must fail within a reasonable window (test profile sets read-timeout-ms=1500,
        // so allow up to 5 s for test infrastructure overhead).
        assertTrue(elapsed < 5_000,
                "Timeout should fire within 5 seconds, but took " + elapsed + " ms");
    }
}

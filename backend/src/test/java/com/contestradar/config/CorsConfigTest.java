package com.contestradar.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CorsConfigTest {

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    @AutoConfigureMockMvc
    @TestPropertySource(properties = {
            "contestradar.cors.allowed-origins=https://app.contestradar.dev,https://staging.contestradar.dev"
    })
    class ConfiguredOriginsTest {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void configuredOriginIsAllowed() throws Exception {
            mockMvc.perform(options("/api/contests")
                            .header(HttpHeaders.ORIGIN, "https://app.contestradar.dev")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name()))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.contestradar.dev"))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
        }

        @Test
        void secondConfiguredOriginIsAllowed() throws Exception {
            mockMvc.perform(options("/api/contests")
                            .header(HttpHeaders.ORIGIN, "https://staging.contestradar.dev")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name()))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://staging.contestradar.dev"))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
        }

        @Test
        void untrustedOriginIsNotGrantedCorsAccess() throws Exception {
            mockMvc.perform(options("/api/contests")
                            .header(HttpHeaders.ORIGIN, "https://malicious-site.com")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name()))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    @AutoConfigureMockMvc
    class DefaultOriginsTest {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void localhostOriginIsAllowedByDefault() throws Exception {
            mockMvc.perform(options("/api/contests")
                            .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name()))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
        }

        @Test
        void untrustedOriginIsDeniedWithDefaultConfig() throws Exception {
            mockMvc.perform(options("/api/contests")
                            .header(HttpHeaders.ORIGIN, "https://attacker.com")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name()))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }
    }
}

package com.algomentor.config;

import com.algomentor.controller.HealthController;
import com.algomentor.service.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HealthController.class)
@Import({
        SecurityConfig.class,
        JwtAuthFilter.class,
        JwtService.class,
        JwtProperties.class,
        CorsProperties.class,
        OAuth2Properties.class,
        OAuth2AuthenticationSuccessHandler.class
})
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-at-least-32-characters-long-for-testing-purposes!",
        "app.jwt.expiry-minutes=60",
        "app.oauth2.redirect-uri=http://localhost:5173/auth/callback",
        "app.cors.allowed-origins=http://localhost:5173"
})
public class CorsConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private com.algomentor.repository.UserRepository userRepository;

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    @DisplayName("CORS preflight request from allowed origin succeeds with headers and no credentials")
    void corsPreflight_allowedOrigin_succeeds() throws Exception {
        mockMvc.perform(options("/api/health")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    @DisplayName("CORS preflight request from disallowed origin is rejected")
    void corsPreflight_disallowedOrigin_rejected() throws Exception {
        mockMvc.perform(options("/api/health")
                        .header("Origin", "http://malicious-attacker.com")
                        .header("Access-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}

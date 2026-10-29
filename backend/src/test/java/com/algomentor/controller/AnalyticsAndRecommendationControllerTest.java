package com.algomentor.controller;

import com.algomentor.config.AnalyticsProperties;
import com.algomentor.config.DateTimeConfig;
import com.algomentor.config.JwtAuthFilter;
import com.algomentor.config.JwtProperties;
import com.algomentor.config.OAuth2AuthenticationSuccessHandler;
import com.algomentor.config.OAuth2Properties;
import com.algomentor.config.RecommenderProperties;
import com.algomentor.config.SecurityConfig;
import com.algomentor.domain.Role;
import com.algomentor.domain.User;
import com.algomentor.dto.DailySolveDto;
import com.algomentor.dto.RatingBucketDto;
import com.algomentor.dto.RecommendationResponseDto;
import com.algomentor.dto.TagStatDto;
import com.algomentor.exception.GlobalExceptionHandler;
import com.algomentor.repository.UserRepository;
import com.algomentor.service.AnalyticsService;
import com.algomentor.service.JwtService;
import com.algomentor.service.RecommendationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AnalyticsController.class, RecommendationController.class})
@Import({
        SecurityConfig.class,
        JwtAuthFilter.class,
        JwtService.class,
        JwtProperties.class,
        OAuth2Properties.class,
        OAuth2AuthenticationSuccessHandler.class,
        GlobalExceptionHandler.class,
        AnalyticsProperties.class,
        RecommenderProperties.class,
        DateTimeConfig.class
})
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-at-least-32-characters-long-for-testing-purposes!",
        "app.jwt.expiry-minutes=60",
        "app.oauth2.redirect-uri=http://localhost:5173/auth/callback",
        "app.timezone=UTC"
})
class AnalyticsAndRecommendationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AnalyticsService analyticsService;
    @MockBean private RecommendationService recommendationService;
    @MockBean private UserRepository userRepository;
    @MockBean private ClientRegistrationRepository clientRegistrationRepository;

    private User regularUser;
    private String userToken;

    @BeforeEach
    void setUp() {
        regularUser = new User();
        regularUser.setId(UUID.randomUUID());
        regularUser.setEmail("user@example.com");
        regularUser.setName("Regular User");
        regularUser.setRole(Role.USER);
        regularUser.setAuthProvider("GOOGLE");
        regularUser.setCfHandle("tourist");

        userToken = jwtService.generate(regularUser);
    }

    // ─── Security checks ──────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/me/analytics/weak-tags without token -> 401")
    void weakTags_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/me/analytics/weak-tags"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/me/analytics/rating-accuracy without token -> 401")
    void ratingAccuracy_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/me/analytics/rating-accuracy"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/me/analytics/trend without token -> 401")
    void trend_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/me/analytics/trend"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/me/recommendations/today without token -> 401")
    void todayRecommendations_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/me/recommendations/today"))
                .andExpect(status().isUnauthorized());
    }

    // ─── Analytics happy-path ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/me/analytics/weak-tags with valid token -> 200 with list")
    void weakTags_validToken_returns200() throws Exception {
        List<TagStatDto> tags = List.of(
                new TagStatDto("dp", 10L, 3L, 0.3),
                new TagStatDto("greedy", 8L, 4L, 0.5)
        );
        when(analyticsService.getWeakTags(regularUser.getId(), 5)).thenReturn(tags);

        mockMvc.perform(get("/api/me/analytics/weak-tags")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].tag").value("dp"))
                .andExpect(jsonPath("$[0].accuracy").value(0.3))
                .andExpect(jsonPath("$[1].tag").value("greedy"));
    }

    @Test
    @DisplayName("GET /api/me/analytics/rating-accuracy with valid token -> 200 with buckets")
    void ratingAccuracy_validToken_returns200() throws Exception {
        List<RatingBucketDto> buckets = List.of(
                new RatingBucketDto(1200, 10L, 7L, 0.7),
                new RatingBucketDto(1400, 8L, 2L, 0.25)
        );
        when(analyticsService.getRatingAccuracy(regularUser.getId())).thenReturn(buckets);

        mockMvc.perform(get("/api/me/analytics/rating-accuracy")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].bucket").value(1200))
                .andExpect(jsonPath("$[0].accuracy").value(0.7));
    }

    @Test
    @DisplayName("GET /api/me/analytics/trend with valid token -> 200 with daily series")
    void trend_validToken_returns200() throws Exception {
        List<DailySolveDto> trend = List.of(
                new DailySolveDto(LocalDate.of(2024, 1, 14), 2L),
                new DailySolveDto(LocalDate.of(2024, 1, 15), 5L)
        );
        when(analyticsService.getTrend(regularUser.getId(), 30)).thenReturn(trend);

        mockMvc.perform(get("/api/me/analytics/trend")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].count").value(5));
    }

    @Test
    @DisplayName("GET /api/me/analytics/trend with days=0 -> 400 Bad Request")
    void trend_invalidDays_returns400() throws Exception {
        when(analyticsService.getTrend(regularUser.getId(), 0))
                .thenThrow(new IllegalArgumentException("Days must be between 1 and 365"));

        mockMvc.perform(get("/api/me/analytics/trend?days=0")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    // ─── Recommendations happy-path ───────────────────────────────────────────

    @Test
    @DisplayName("GET /api/me/recommendations/today with valid token -> 200 with list")
    void todayRecommendations_validToken_returns200() throws Exception {
        when(userRepository.findById(regularUser.getId()))
                .thenReturn(Optional.of(regularUser));

        List<RecommendationResponseDto> recs = List.of(
                new RecommendationResponseDto(
                        UUID.randomUUID(), "A+B Problem", 800,
                        List.of("math"), "https://codeforces.com/problemset/problem/1/A",
                        "Starter problem, rating 800", "SERVED")
        );
        when(recommendationService.getOrCreateToday(regularUser)).thenReturn(recs);

        mockMvc.perform(get("/api/me/recommendations/today")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("SERVED"))
                .andExpect(jsonPath("$[0].problemName").value("A+B Problem"));
    }

    @Test
    @DisplayName("PATCH /api/me/recommendations/{id}/status with invalid status -> 400")
    void patchStatus_invalidStatusValue_returns400() throws Exception {
        UUID recId = UUID.randomUUID();
        String body = "{\"status\":\"SOLVED\"}"; // SOLVED is not allowed from client

        mockMvc.perform(patch("/api/me/recommendations/" + recId + "/status")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /api/me/recommendations/{id}/status with another user's id -> 404")
    void patchStatus_anotherUsersRecommendation_returns404() throws Exception {
        UUID recId = UUID.randomUUID();
        String body = "{\"status\":\"SKIPPED\"}";

        when(recommendationService.updateStatus(eq(regularUser.getId()), eq(recId), eq("SKIPPED")))
                .thenThrow(new ResponseStatusException(NOT_FOUND, "Recommendation not found"));

        mockMvc.perform(patch("/api/me/recommendations/" + recId + "/status")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH /api/me/recommendations/{id}/status with SKIPPED -> 200")
    void patchStatus_validSkipped_returns200() throws Exception {
        UUID recId = UUID.randomUUID();
        String body = "{\"status\":\"SKIPPED\"}";

        RecommendationResponseDto dto = new RecommendationResponseDto(
                recId, "Some Problem", 1200,
                List.of("dp"), "https://codeforces.com/problemset/problem/1/A",
                "Weak tag: dp", "SKIPPED");

        when(recommendationService.updateStatus(regularUser.getId(), recId, "SKIPPED"))
                .thenReturn(dto);

        mockMvc.perform(patch("/api/me/recommendations/" + recId + "/status")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SKIPPED"))
                .andExpect(jsonPath("$.problemName").value("Some Problem"));
    }
}

package com.algomentor.controller;

import com.algomentor.config.AiProperties;
import com.algomentor.config.CorsProperties;
import com.algomentor.config.DateTimeConfig;
import com.algomentor.config.JwtAuthFilter;
import com.algomentor.config.JwtProperties;
import com.algomentor.config.OAuth2AuthenticationSuccessHandler;
import com.algomentor.config.OAuth2Properties;
import com.algomentor.config.SecurityConfig;
import com.algomentor.domain.Problem;
import com.algomentor.domain.Role;
import com.algomentor.domain.User;
import com.algomentor.dto.AiProblemDto;
import com.algomentor.dto.MentorHintRequestDto;
import com.algomentor.dto.MentorHintResponseDto;
import com.algomentor.exception.AiUnavailableException;
import com.algomentor.exception.GlobalExceptionHandler;
import com.algomentor.repository.ProblemRepository;
import com.algomentor.repository.UserRepository;
import com.algomentor.service.AiServiceClient;
import com.algomentor.service.JwtService;
import com.algomentor.service.MentorService;
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

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MentorController.class)
@Import({
        SecurityConfig.class,
        CorsProperties.class,
        JwtAuthFilter.class,
        JwtService.class,
        JwtProperties.class,
        OAuth2Properties.class,
        OAuth2AuthenticationSuccessHandler.class,
        GlobalExceptionHandler.class,
        DateTimeConfig.class,
        MentorService.class,
        AiProperties.class
})
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-at-least-32-characters-long-for-testing-purposes!",
        "app.jwt.expiry-minutes=60",
        "app.oauth2.redirect-uri=http://localhost:5173/auth/callback",
        "app.ai.base-url=http://localhost:8000",
        "app.ai.service-key=test-ai-service-key-32-characters-long!!"
})
public class MentorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private ProblemRepository problemRepository;

    @MockBean
    private AiServiceClient aiServiceClient;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    private User testUser;
    private String jwtToken;
    private UUID problemId;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("student@example.com");
        testUser.setName("Student");
        testUser.setRole(Role.USER);

        jwtToken = jwtService.generate(testUser);
        problemId = UUID.randomUUID();
    }

    @Test
    @DisplayName("401 Unauthorized when requesting hint without JWT token")
    void getHint_withoutToken_returns401() throws Exception {
        MentorHintRequestDto request = new MentorHintRequestDto(problemId);

        mockMvc.perform(post("/api/me/mentor/hint")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("404 Not Found when requesting hint for unknown problem")
    void getHint_unknownProblem_returns404() throws Exception {
        when(problemRepository.findByIdWithTags(problemId)).thenReturn(Optional.empty());

        MentorHintRequestDto request = new MentorHintRequestDto(problemId);

        mockMvc.perform(post("/api/me/mentor/hint")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("502 Bad Gateway when AI service is unavailable during hint generation")
    void getHint_aiServiceDown_returns502() throws Exception {
        Problem problem = new Problem();
        problem.setId(problemId);
        problem.setName("Two Sum");
        problem.setRating(800);
        problem.setUrl("https://codeforces.com/problemset/problem/1/A");
        problem.setTags(Collections.emptySet());

        when(problemRepository.findByIdWithTags(problemId)).thenReturn(Optional.of(problem));
        when(aiServiceClient.getHint(eq(testUser.getId()), any(AiProblemDto.class)))
                .thenThrow(new AiUnavailableException("AI service unavailable during hint generation", null));

        MentorHintRequestDto request = new MentorHintRequestDto(problemId);

        mockMvc.perform(post("/api/me/mentor/hint")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Bad Gateway"));
    }

    @Test
    @DisplayName("200 OK when hint successfully generated")
    void getHint_success_returns200() throws Exception {
        Problem problem = new Problem();
        problem.setId(problemId);
        problem.setName("Two Sum");
        problem.setRating(800);
        problem.setUrl("https://codeforces.com/problemset/problem/1/A");
        problem.setTags(Collections.emptySet());

        MentorHintResponseDto responseDto = new MentorHintResponseDto(
                1,
                "What relationship exists between the current element and the target sum?",
                "Can you check if the complement was already seen?",
                false,
                Collections.emptyList()
        );

        when(problemRepository.findByIdWithTags(problemId)).thenReturn(Optional.of(problem));
        when(aiServiceClient.getHint(eq(testUser.getId()), any(AiProblemDto.class)))
                .thenReturn(responseDto);

        MentorHintRequestDto request = new MentorHintRequestDto(problemId);

        mockMvc.perform(post("/api/me/mentor/hint")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value(1))
                .andExpect(jsonPath("$.hint").value("What relationship exists between the current element and the target sum?"))
                .andExpect(jsonPath("$.exhausted").value(false));
    }
}


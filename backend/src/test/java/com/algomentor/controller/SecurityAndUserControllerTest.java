package com.algomentor.controller;

import com.algomentor.config.JwtAuthFilter;
import com.algomentor.config.JwtProperties;
import com.algomentor.config.OAuth2AuthenticationSuccessHandler;
import com.algomentor.config.OAuth2Properties;
import com.algomentor.config.SecurityConfig;
import com.algomentor.domain.Role;
import com.algomentor.domain.User;
import com.algomentor.dto.UpdateCfHandleRequest;
import com.algomentor.dto.UserResponseDto;
import com.algomentor.exception.CodeforcesHandleNotFoundException;
import com.algomentor.exception.CodeforcesServiceException;
import com.algomentor.exception.GlobalExceptionHandler;
import com.algomentor.repository.UserRepository;
import com.algomentor.service.JwtService;
import com.algomentor.service.SyncService;
import com.algomentor.service.UserService;
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

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {UserController.class, AdminController.class, HealthController.class})
@Import({
        SecurityConfig.class,
        JwtAuthFilter.class,
        JwtService.class,
        JwtProperties.class,
        OAuth2Properties.class,
        OAuth2AuthenticationSuccessHandler.class,
        GlobalExceptionHandler.class
})
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-at-least-32-characters-long-for-testing-purposes!",
        "app.jwt.expiry-minutes=60",
        "app.oauth2.redirect-uri=http://localhost:5173/auth/callback"
})
class SecurityAndUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private SyncService syncService;

    @MockBean
    private com.algomentor.service.DigestService digestService;

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    private User regularUser;
    private User adminUser;
    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        regularUser = new User();
        regularUser.setId(UUID.randomUUID());
        regularUser.setEmail("user@example.com");
        regularUser.setName("Regular User");
        regularUser.setRole(Role.USER);
        regularUser.setAuthProvider("GOOGLE");
        regularUser.setCfHandle("tourist");

        adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setEmail("admin@example.com");
        adminUser.setName("Admin User");
        adminUser.setRole(Role.ADMIN);
        adminUser.setAuthProvider("GOOGLE");

        userToken = jwtService.generate(regularUser);
        adminToken = jwtService.generate(adminUser);
    }

    @Test
    @DisplayName("GET /api/me without token -> 401 Unauthorized")
    void getMeWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("GET /api/me with valid USER token -> 200 OK with user details")
    void getMeWithValidUserTokenReturns200() throws Exception {
        when(userService.getProfile(regularUser.getId())).thenReturn(UserResponseDto.from(regularUser));

        mockMvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(regularUser.getId().toString()))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.name").value("Regular User"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.cfHandle").value("tourist"));
    }

    @Test
    @DisplayName("GET /api/admin/ping with USER token -> 403 Forbidden")
    void getAdminPingWithUserTokenReturns403() throws Exception {
        mockMvc.perform(get("/api/admin/ping")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("GET /api/admin/ping with ADMIN token -> 200 OK")
    void getAdminPingWithAdminTokenReturns200() throws Exception {
        mockMvc.perform(get("/api/admin/ping")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("pong"))
                .andExpect(jsonPath("$.admin").value(true));
    }

    @Test
    @DisplayName("PUT /api/me/cf-handle with valid handle -> 200 OK")
    void linkCfHandleSuccess() throws Exception {
        User updated = new User();
        updated.setId(regularUser.getId());
        updated.setEmail(regularUser.getEmail());
        updated.setName(regularUser.getName());
        updated.setRole(regularUser.getRole());
        updated.setCfHandle("petr");

        when(userService.updateCfHandle(regularUser.getId(), "petr")).thenReturn(UserResponseDto.from(updated));

        UpdateCfHandleRequest request = new UpdateCfHandleRequest("petr");

        mockMvc.perform(put("/api/me/cf-handle")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cfHandle").value("petr"));
    }

    @Test
    @DisplayName("PUT /api/me/cf-handle when Codeforces handle not found -> 400 Bad Request")
    void linkCfHandleNotFoundReturns400() throws Exception {
        when(userService.updateCfHandle(regularUser.getId(), "non_existent_user_xyz"))
                .thenThrow(new CodeforcesHandleNotFoundException("non_existent_user_xyz"));

        UpdateCfHandleRequest request = new UpdateCfHandleRequest("non_existent_user_xyz");

        mockMvc.perform(put("/api/me/cf-handle")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Codeforces handle 'non_existent_user_xyz' does not exist"));
    }

    @Test
    @DisplayName("PUT /api/me/cf-handle when Codeforces is unreachable -> 502 Bad Gateway")
    void linkCfHandleServiceUnreachableReturns502() throws Exception {
        when(userService.updateCfHandle(regularUser.getId(), "petr"))
                .thenThrow(new CodeforcesServiceException("Codeforces API is currently unreachable"));

        UpdateCfHandleRequest request = new UpdateCfHandleRequest("petr");

        mockMvc.perform(put("/api/me/cf-handle")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Bad Gateway"))
                .andExpect(jsonPath("$.message").value("Codeforces API is currently unreachable"));
    }
}

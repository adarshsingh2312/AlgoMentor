package com.algomentor.controller;

import com.algomentor.config.JwtAuthFilter;
import com.algomentor.config.JwtProperties;
import com.algomentor.config.OAuth2AuthenticationSuccessHandler;
import com.algomentor.config.OAuth2Properties;
import com.algomentor.config.SecurityConfig;
import com.algomentor.domain.Role;
import com.algomentor.domain.User;
import com.algomentor.dto.SyncResult;
import com.algomentor.exception.GlobalExceptionHandler;
import com.algomentor.repository.UserRepository;
import com.algomentor.service.JwtService;
import com.algomentor.service.SyncService;
import com.algomentor.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {UserController.class, AdminController.class})
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
class SyncControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private UserService userService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private SyncService syncService;

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
    @DisplayName("POST /api/me/sync without token -> 401 Unauthorized")
    void syncMeWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/me/sync"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("POST /api/me/sync without CF handle -> 400 Bad Request with clear message")
    void syncMeWithoutCfHandleReturns400() throws Exception {
        regularUser.setCfHandle(null);
        when(userRepository.findById(regularUser.getId())).thenReturn(Optional.of(regularUser));
        when(syncService.syncUserSubmissions(regularUser))
                .thenThrow(new IllegalStateException("User does not have a Codeforces handle linked"));

        mockMvc.perform(post("/api/me/sync")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("User does not have a Codeforces handle linked"));
    }

    @Test
    @DisplayName("POST /api/me/sync with linked CF handle -> 200 OK with SyncResult")
    void syncMeWithCfHandleReturns200() throws Exception {
        regularUser.setCfHandle("tourist");
        when(userRepository.findById(regularUser.getId())).thenReturn(Optional.of(regularUser));
        when(syncService.syncUserSubmissions(regularUser))
                .thenReturn(new SyncResult(0, 0, 5, 2));

        mockMvc.perform(post("/api/me/sync")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submissionsInserted").value(5))
                .andExpect(jsonPath("$.submissionsSkipped").value(2));
    }

    @Test
    @DisplayName("POST /api/admin/sync/problemset with USER role -> 403 Forbidden")
    void syncProblemsetWithUserRoleReturns403() throws Exception {
        mockMvc.perform(post("/api/admin/sync/problemset")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("POST /api/admin/sync/problemset with ADMIN role -> 200 OK with SyncResult")
    void syncProblemsetWithAdminRoleReturns200() throws Exception {
        when(syncService.syncProblemset())
                .thenReturn(new SyncResult(12, 3, 0, 0));

        mockMvc.perform(post("/api/admin/sync/problemset")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.problemsInserted").value(12))
                .andExpect(jsonPath("$.problemsUpdated").value(3));
    }
}

package com.algomentor.service;

import com.algomentor.config.JwtProperties;
import com.algomentor.domain.Role;
import com.algomentor.domain.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String TEST_SECRET = "super-secret-key-that-is-at-least-32-bytes-long-for-testing!";
    private JwtService jwtService;
    private User testUser;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(TEST_SECRET, 60);
        jwtService = new JwtService(properties);

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("tourist@example.com");
        testUser.setName("Gennady Korotkevich");
        testUser.setRole(Role.USER);
        testUser.setAuthProvider("GOOGLE");
    }

    @Test
    @DisplayName("Should successfully generate and parse a valid token")
    void validTokenOk() {
        String token = jwtService.generate(testUser);

        assertThat(token).isNotBlank();

        JwtUserClaims claims = jwtService.parseAndValidate(token);
        assertThat(claims.userId()).isEqualTo(testUser.getId());
        assertThat(claims.role()).isEqualTo(Role.USER);
        assertThat(claims.email()).isEqualTo("tourist@example.com");
    }

    @Test
    @DisplayName("Should reject a tampered token")
    void tamperedTokenRejected() {
        String token = jwtService.generate(testUser);

        // Tamper with the signature (last part of JWT)
        String[] parts = token.split("\\.");
        char lastChar = parts[2].charAt(parts[2].length() - 1);
        char replacedChar = (lastChar == 'a') ? 'b' : 'a';
        String tamperedSignature = parts[2].substring(0, parts[2].length() - 1) + replacedChar;
        String tamperedToken = parts[0] + "." + parts[1] + "." + tamperedSignature;

        assertThatThrownBy(() -> jwtService.parseAndValidate(tamperedToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Should reject an expired token")
    void expiredTokenRejected() {
        // Service with negative expiry to simulate expired token
        JwtProperties expiredProperties = new JwtProperties(TEST_SECRET, -5);
        JwtService expiredJwtService = new JwtService(expiredProperties);

        String expiredToken = expiredJwtService.generate(testUser);

        assertThatThrownBy(() -> expiredJwtService.parseAndValidate(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }
}

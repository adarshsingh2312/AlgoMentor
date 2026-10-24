package com.algomentor.service;

import com.algomentor.config.JwtProperties;
import com.algomentor.domain.Role;
import com.algomentor.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        if (properties.getSecret() == null || properties.getSecret().trim().length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters long");
        }
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generate(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.getExpiryMinutes(), ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public JwtUserClaims parseAndValidate(String token) {
        Claims payload = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        UUID userId = UUID.fromString(payload.getSubject());
        Role role = Role.valueOf(payload.get("role", String.class));
        String email = payload.get("email", String.class);

        return new JwtUserClaims(userId, role, email);
    }
}

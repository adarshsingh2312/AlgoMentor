package com.algomentor.service;

import com.algomentor.domain.Role;

import java.util.UUID;

public record JwtUserClaims(UUID userId, Role role, String email) {
}

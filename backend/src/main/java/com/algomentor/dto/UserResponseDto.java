package com.algomentor.dto;

import com.algomentor.domain.Role;
import com.algomentor.domain.User;

import java.util.UUID;

public record UserResponseDto(
        UUID id,
        String email,
        String name,
        Role role,
        String cfHandle
) {
    public static UserResponseDto from(User user) {
        return new UserResponseDto(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                user.getCfHandle()
        );
    }
}

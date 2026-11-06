package com.algomentor.dto;

import java.util.UUID;

public record MentorResetResponseDto(
        String status,
        UUID userId,
        UUID problemId
) {}


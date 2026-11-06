package com.algomentor.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record MentorHintRequestDto(
        @NotNull(message = "problemId is required")
        UUID problemId
) {}

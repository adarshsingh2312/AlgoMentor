package com.algomentor.dto;

import java.util.UUID;

public record AiMentorHintRequest(
        UUID userId,
        AiProblemDto problem
) {}


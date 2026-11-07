package com.algomentor.dto;

import java.util.List;
import java.util.UUID;

public record StruggledProblemDto(
        UUID problemId,
        String problemName,
        Integer rating,
        String url,
        List<String> tags,
        long failedAttempts
) {}


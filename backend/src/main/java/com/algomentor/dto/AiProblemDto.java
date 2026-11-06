package com.algomentor.dto;

import java.util.List;
import java.util.UUID;

public record AiProblemDto(
        UUID id,
        String name,
        Integer rating,
        List<String> tags,
        String url
) {}


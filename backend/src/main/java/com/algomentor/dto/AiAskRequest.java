package com.algomentor.dto;

import java.util.UUID;

public record AiAskRequest(
        UUID userId,
        String question,
        int topK
) {}


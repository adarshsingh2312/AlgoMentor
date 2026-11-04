package com.algomentor.dto;

import java.util.UUID;

public record AiIngestRequest(
        UUID documentId,
        UUID userId,
        String displayName,
        String text
) {}


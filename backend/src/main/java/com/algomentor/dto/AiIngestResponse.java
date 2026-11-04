package com.algomentor.dto;

import java.util.UUID;

public record AiIngestResponse(
        UUID documentId,
        int chunksCount,
        String status
) {}


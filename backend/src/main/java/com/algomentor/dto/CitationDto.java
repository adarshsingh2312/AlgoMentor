package com.algomentor.dto;

import java.util.UUID;

public record CitationDto(
        UUID documentId,
        String displayName,
        int chunkIndex,
        String snippet,
        double score
) {}


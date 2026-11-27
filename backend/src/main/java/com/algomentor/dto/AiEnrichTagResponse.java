package com.algomentor.dto;

public record AiEnrichTagResponse(
        String tag,
        String status,
        int chunksCount
) {}

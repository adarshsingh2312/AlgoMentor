package com.algomentor.dto;

public record DigestRunSummaryDto(
        int sent,
        int skipped,
        int failed,
        long durationMs
) {}


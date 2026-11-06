package com.algomentor.dto;

import java.util.List;

public record MentorHintResponseDto(
        int level,
        String hint,
        String followUp,
        boolean exhausted,
        List<CitationDto> citations
) {}


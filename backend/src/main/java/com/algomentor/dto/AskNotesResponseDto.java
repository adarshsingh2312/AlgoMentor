package com.algomentor.dto;

import java.util.List;

public record AskNotesResponseDto(
        String answer,
        List<CitationDto> citations
) {}

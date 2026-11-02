package com.algomentor.dto;

import com.algomentor.domain.NoteStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record NoteDocumentListDto(
        UUID id,
        String displayName,
        long sizeBytes,
        NoteStatus status,
        LocalDateTime createdAt
) {}


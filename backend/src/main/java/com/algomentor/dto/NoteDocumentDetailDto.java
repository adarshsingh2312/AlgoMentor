package com.algomentor.dto;

import com.algomentor.domain.NoteStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record NoteDocumentDetailDto(
        UUID id,
        String displayName,
        String contentType,
        long sizeBytes,
        String contentHash,
        String content,
        NoteStatus status,
        LocalDateTime createdAt
) {}


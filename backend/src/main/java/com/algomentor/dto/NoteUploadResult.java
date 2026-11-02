package com.algomentor.dto;

public record NoteUploadResult(
        NoteDocumentDetailDto note,
        boolean isDuplicate
) {}


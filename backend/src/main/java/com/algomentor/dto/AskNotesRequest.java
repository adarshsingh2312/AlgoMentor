package com.algomentor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AskNotesRequest(
        @NotBlank(message = "Question must not be blank")
        @Size(max = 1000, message = "Question must not exceed 1000 characters")
        String question,

        @Valid
        @Size(max = 20, message = "History cannot exceed 20 messages")
        List<ChatMessageDto> history
) {
    public AskNotesRequest(String question) {
        this(question, List.of());
    }
}


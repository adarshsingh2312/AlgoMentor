package com.algomentor.dto;

import java.util.List;
import java.util.UUID;

public record AiAskRequest(
        UUID userId,
        String question,
        int topK,
        List<ChatMessageDto> history
) {
    public AiAskRequest(UUID userId, String question, int topK) {
        this(userId, question, topK, List.of());
    }
}


package com.algomentor.dto;

import com.algomentor.domain.DifficultyLevel;
import com.algomentor.domain.TopicStatus;

import java.util.UUID;

public record DsaTopicProgressDto(
        UUID id,
        String title,
        String url,
        DifficultyLevel difficulty,
        TopicStatus status
) {}


package com.algomentor.dto;

import com.algomentor.domain.TopicStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateProgressRequest(
        @NotNull(message = "status is required") TopicStatus status
) {}


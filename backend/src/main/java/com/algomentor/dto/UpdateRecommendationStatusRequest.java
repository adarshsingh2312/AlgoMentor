package com.algomentor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateRecommendationStatusRequest(
        @NotBlank(message = "Status is required")
        @Pattern(regexp = "^(?i)SKIPPED$", message = "Only SKIPPED status is allowed from client")
        String status
) {}

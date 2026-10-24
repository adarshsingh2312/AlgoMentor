package com.algomentor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateCfHandleRequest(
        @NotBlank(message = "Handle must not be blank")
        @Size(min = 3, max = 24, message = "Handle must be between 3 and 24 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "Handle may only contain letters, numbers, underscores, hyphens, or periods")
        String handle
) {
}

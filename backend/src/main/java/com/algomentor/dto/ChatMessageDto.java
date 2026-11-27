package com.algomentor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChatMessageDto(
        @NotBlank(message = "Message role is required")
        @Pattern(regexp = "user|assistant", message = "Role must be 'user' or 'assistant'")
        String role,

        @NotBlank(message = "Message content is required")
        @Size(max = 4000, message = "Message content must not exceed 4000 characters")
        String content
) {}

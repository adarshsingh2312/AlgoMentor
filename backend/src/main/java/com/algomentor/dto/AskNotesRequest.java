package com.algomentor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskNotesRequest(
        @NotBlank(message = "Question must not be blank")
        @Size(max = 500, message = "Question must not exceed 500 characters")
        String question
) {}


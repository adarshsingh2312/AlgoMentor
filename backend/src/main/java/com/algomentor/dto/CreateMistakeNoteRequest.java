package com.algomentor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateMistakeNoteRequest(
        @NotBlank(message = "Problem title is required")
        @Size(max = 200, message = "Problem title must not exceed 200 characters")
        String problemTitle,

        List<String> tags,

        @NotBlank(message = "Mistake description is required")
        @Size(max = 2000, message = "Mistake description must not exceed 2000 characters")
        String mistakeText,

        @Size(max = 1000, message = "Key takeaway must not exceed 1000 characters")
        String keyTakeaway
) {}


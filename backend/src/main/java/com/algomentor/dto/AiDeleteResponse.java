package com.algomentor.dto;

import java.util.UUID;

public record AiDeleteResponse(
        UUID documentId,
        int deletedCount
) {}


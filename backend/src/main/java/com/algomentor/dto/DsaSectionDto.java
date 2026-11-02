package com.algomentor.dto;

import java.util.List;

public record DsaSectionDto(
        String section,
        List<DsaTopicProgressDto> items,
        int done,
        int total
) {}


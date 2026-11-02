package com.algomentor.dto;

import java.util.List;

public record DsaRoadmapDto(
        List<DsaSectionDto> sections,
        int totalDone,
        int totalTopics
) {}


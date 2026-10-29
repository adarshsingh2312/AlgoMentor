package com.algomentor.dto;

public record TagStatDto(
        String tag,
        long attempted,
        long solved,
        double accuracy
) {}

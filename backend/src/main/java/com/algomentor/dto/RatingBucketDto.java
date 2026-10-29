package com.algomentor.dto;

public record RatingBucketDto(
        int bucket,
        long attempted,
        long solved,
        double accuracy
) {}

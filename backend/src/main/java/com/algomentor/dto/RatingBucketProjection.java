package com.algomentor.dto;

public interface RatingBucketProjection {
    Integer getBucket();
    Long getAttempted();
    Long getSolved();
}

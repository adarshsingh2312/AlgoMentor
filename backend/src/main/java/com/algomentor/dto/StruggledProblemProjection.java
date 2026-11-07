package com.algomentor.dto;

import java.util.UUID;

public interface StruggledProblemProjection {
    UUID getProblemId();
    String getProblemName();
    Integer getRating();
    String getUrl();
    Long getFailedAttempts();
}


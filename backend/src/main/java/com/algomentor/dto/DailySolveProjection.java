package com.algomentor.dto;

import java.time.LocalDate;

public interface DailySolveProjection {
    LocalDate getSolveDate();
    Long getSolvedCount();
}

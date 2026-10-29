package com.algomentor.dto;

import java.time.LocalDate;

public record DailySolveDto(
        LocalDate date,
        long count
) {}

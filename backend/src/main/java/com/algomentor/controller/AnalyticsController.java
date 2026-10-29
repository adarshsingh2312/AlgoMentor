package com.algomentor.controller;

import com.algomentor.dto.DailySolveDto;
import com.algomentor.dto.RatingBucketDto;
import com.algomentor.dto.TagStatDto;
import com.algomentor.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/me/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/weak-tags")
    public ResponseEntity<List<TagStatDto>> getWeakTags(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "5") int limit) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return ResponseEntity.ok(analyticsService.getWeakTags(userId, limit));
    }

    @GetMapping("/rating-accuracy")
    public ResponseEntity<List<RatingBucketDto>> getRatingAccuracy(
            @AuthenticationPrincipal UUID userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return ResponseEntity.ok(analyticsService.getRatingAccuracy(userId));
    }

    @GetMapping("/trend")
    public ResponseEntity<List<DailySolveDto>> getTrend(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "30") int days) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return ResponseEntity.ok(analyticsService.getTrend(userId, days));
    }
}

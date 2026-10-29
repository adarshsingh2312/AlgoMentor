package com.algomentor.service;

import com.algomentor.config.AnalyticsProperties;
import com.algomentor.dto.DailySolveDto;
import com.algomentor.dto.DailySolveProjection;
import com.algomentor.dto.RatingBucketDto;
import com.algomentor.dto.RatingBucketProjection;
import com.algomentor.dto.TagStatDto;
import com.algomentor.dto.TagStatProjection;
import com.algomentor.repository.SubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private final SubmissionRepository submissionRepository;
    private final AnalyticsProperties analyticsProperties;
    private final Clock clock;

    public AnalyticsService(SubmissionRepository submissionRepository,
                            AnalyticsProperties analyticsProperties,
                            Clock clock) {
        this.submissionRepository = submissionRepository;
        this.analyticsProperties = analyticsProperties;
        this.clock = clock;
    }

    /**
     * Returns the user's weakest topic tags.
     * Filtered by attempted >= minAttempts, sorted by accuracy ascending (lowest solve ratio first).
     */
    public List<TagStatDto> getWeakTags(UUID userId, int limit) {
        if (userId == null) {
            return List.of();
        }
        int effectiveLimit = Math.max(1, limit);
        int minAttempts = analyticsProperties.getMinAttempts();

        List<TagStatProjection> projections = submissionRepository.findTagStatsByUserId(userId);
        if (projections == null || projections.isEmpty()) {
            return List.of();
        }

        return projections.stream()
                .filter(p -> p.getAttempted() != null && p.getAttempted() >= minAttempts)
                .map(p -> {
                    long attempted = p.getAttempted();
                    long solved = p.getSolved() != null ? p.getSolved() : 0L;
                    double accuracy = attempted > 0 ? (double) solved / attempted : 0.0;
                    return new TagStatDto(p.getTag(), attempted, solved, round(accuracy));
                })
                .sorted(Comparator.comparingDouble(TagStatDto::accuracy)
                        .thenComparing(Comparator.comparingLong(TagStatDto::attempted).reversed()))
                .limit(effectiveLimit)
                .toList();
    }

    /**
     * Returns accuracy across rating buckets (multiples of 100), ordered ascending by rating bucket.
     */
    public List<RatingBucketDto> getRatingAccuracy(UUID userId) {
        if (userId == null) {
            return List.of();
        }

        List<RatingBucketProjection> projections = submissionRepository.findRatingBucketStatsByUserId(userId);
        if (projections == null || projections.isEmpty()) {
            return List.of();
        }

        return projections.stream()
                .map(p -> {
                    int bucket = p.getBucket() != null ? p.getBucket() : 0;
                    long attempted = p.getAttempted() != null ? p.getAttempted() : 0L;
                    long solved = p.getSolved() != null ? p.getSolved() : 0L;
                    double accuracy = attempted > 0 ? (double) solved / attempted : 0.0;
                    return new RatingBucketDto(bucket, attempted, solved, round(accuracy));
                })
                .toList();
    }

    /**
     * Returns distinct solved problems per day over the last N days.
     * Generates a continuous daily series from (today - (days - 1)) up to today.
     *
     * @throws IllegalArgumentException if days < 1 or days > 365
     */
    public List<DailySolveDto> getTrend(UUID userId, int days) {
        if (days < 1 || days > 365) {
            throw new IllegalArgumentException("Days must be between 1 and 365");
        }
        if (userId == null) {
            return List.of();
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate startDate = today.minusDays(days - 1);

        List<DailySolveProjection> projections = submissionRepository.findDailySolvesByUserIdSince(userId, startDate);
        Map<LocalDate, Long> solvesByDate = (projections != null ? projections : List.<DailySolveProjection>of())
                .stream()
                .filter(p -> p.getSolveDate() != null)
                .collect(Collectors.toMap(
                        DailySolveProjection::getSolveDate,
                        p -> p.getSolvedCount() != null ? p.getSolvedCount() : 0L,
                        (a, b) -> a));

        List<DailySolveDto> result = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            LocalDate date = startDate.plusDays(i);
            long count = solvesByDate.getOrDefault(date, 0L);
            result.add(new DailySolveDto(date, count));
        }

        return result;
    }

    private double round(double val) {
        return Math.round(val * 10000.0) / 10000.0;
    }
}

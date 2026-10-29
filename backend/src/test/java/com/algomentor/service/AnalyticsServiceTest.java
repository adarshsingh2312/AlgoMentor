package com.algomentor.service;

import com.algomentor.config.AnalyticsProperties;
import com.algomentor.dto.DailySolveDto;
import com.algomentor.dto.DailySolveProjection;
import com.algomentor.dto.RatingBucketDto;
import com.algomentor.dto.RatingBucketProjection;
import com.algomentor.dto.TagStatDto;
import com.algomentor.dto.TagStatProjection;
import com.algomentor.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for AnalyticsService — no Spring context, no DB.
 */
class AnalyticsServiceTest {

    // Fixed clock: 2024-01-15
    private static final LocalDate FIXED_DATE = LocalDate.of(2024, 1, 15);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2024-01-15T00:00:00Z"), ZoneId.of("UTC"));

    private SubmissionRepository submissionRepository;
    private AnalyticsProperties props;
    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        submissionRepository = mock(SubmissionRepository.class);
        props = new AnalyticsProperties();
        props.setMinAttempts(5);
        service = new AnalyticsService(submissionRepository, props, FIXED_CLOCK);
    }

    // ─── getWeakTags ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getWeakTags - returns empty list when userId is null")
    void getWeakTags_nullUser_returnsEmpty() {
        assertThat(service.getWeakTags(null, 5)).isEmpty();
    }

    @Test
    @DisplayName("getWeakTags - filters out tags below minAttempts threshold")
    void getWeakTags_filtersBelowMinAttempts() {
        UUID userId = UUID.randomUUID();

        // "graphs" has 3 attempts (below threshold=5), "dp" has 10 attempts
        TagStatProjection graphs = makeTagStat("graphs", 3L, 1L);
        TagStatProjection dp    = makeTagStat("dp", 10L, 3L);

        when(submissionRepository.findTagStatsByUserId(userId)).thenReturn(List.of(graphs, dp));

        List<TagStatDto> result = service.getWeakTags(userId, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).tag()).isEqualTo("dp"); // only dp passes
    }

    @Test
    @DisplayName("getWeakTags - sorts by accuracy ascending (weakest first)")
    void getWeakTags_sortsByAccuracyAscending() {
        UUID userId = UUID.randomUUID();

        // greedy: 6/10 = 0.6, dp: 5/10 = 0.5 - dp should be first
        TagStatProjection greedy = makeTagStat("greedy", 10L, 6L);
        TagStatProjection dp     = makeTagStat("dp", 10L, 5L);

        when(submissionRepository.findTagStatsByUserId(userId)).thenReturn(List.of(greedy, dp));

        List<TagStatDto> result = service.getWeakTags(userId, 10);

        assertThat(result).extracting(TagStatDto::tag).containsExactly("dp", "greedy");
    }

    @Test
    @DisplayName("getWeakTags - respects limit parameter")
    void getWeakTags_respectsLimit() {
        UUID userId = UUID.randomUUID();

        List<TagStatProjection> tags = List.of(
                makeTagStat("dp", 10L, 1L),
                makeTagStat("greedy", 10L, 2L),
                makeTagStat("graphs", 10L, 3L)
        );
        when(submissionRepository.findTagStatsByUserId(userId)).thenReturn(tags);

        List<TagStatDto> result = service.getWeakTags(userId, 2);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("getWeakTags - accuracy is solved/attempted rounded to 4 decimal places")
    void getWeakTags_accuracyIsDistinctProblemsRatio() {
        UUID userId = UUID.randomUUID();

        // 3 solved out of 9 attempted -> accuracy = 3/9 = 0.3333
        TagStatProjection dp = makeTagStat("dp", 9L, 3L);
        when(submissionRepository.findTagStatsByUserId(userId)).thenReturn(List.of(dp));

        List<TagStatDto> result = service.getWeakTags(userId, 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).accuracy()).isEqualTo(0.3333); // rounded to 4 decimal places
    }

    // ─── getRatingAccuracy ────────────────────────────────────────────────────

    @Test
    @DisplayName("getRatingAccuracy - returns empty list for null userId")
    void getRatingAccuracy_nullUser_returnsEmpty() {
        assertThat(service.getRatingAccuracy(null)).isEmpty();
    }

    @Test
    @DisplayName("getRatingAccuracy - maps projections to DTOs preserving order")
    void getRatingAccuracy_mapsCorrectly() {
        UUID userId = UUID.randomUUID();

        RatingBucketProjection b1200 = makeRatingBucket(1200, 10L, 7L);
        RatingBucketProjection b1400 = makeRatingBucket(1400, 8L, 2L);
        when(submissionRepository.findRatingBucketStatsByUserId(userId)).thenReturn(List.of(b1200, b1400));

        List<RatingBucketDto> result = service.getRatingAccuracy(userId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).bucket()).isEqualTo(1200);
        assertThat(result.get(0).accuracy()).isEqualTo(0.7);
        assertThat(result.get(1).bucket()).isEqualTo(1400);
        assertThat(result.get(1).accuracy()).isEqualTo(0.25);
    }

    // ─── getTrend ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getTrend - throws for days < 1")
    void getTrend_daysLessThan1_throws() {
        UUID userId = UUID.randomUUID();
        assertThatThrownBy(() -> service.getTrend(userId, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 and 365");
    }

    @Test
    @DisplayName("getTrend - throws for days > 365")
    void getTrend_daysMoreThan365_throws() {
        UUID userId = UUID.randomUUID();
        assertThatThrownBy(() -> service.getTrend(userId, 366))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 and 365");
    }

    @Test
    @DisplayName("getTrend - generates continuous series with zeros for missing days")
    void getTrend_fillsZerosForMissingDays() {
        UUID userId = UUID.randomUUID();

        // Only 2024-01-14 has solves; 2024-01-13 and 2024-01-15 should be zero
        DailySolveProjection jan14 = makeDailySolve(LocalDate.of(2024, 1, 14), 3L);
        when(submissionRepository.findDailySolvesByUserIdSince(
                userId, LocalDate.of(2024, 1, 13))).thenReturn(List.of(jan14));

        List<DailySolveDto> result = service.getTrend(userId, 3);

        assertThat(result).hasSize(3);
        assertThat(result.get(0)).isEqualTo(new DailySolveDto(LocalDate.of(2024, 1, 13), 0L));
        assertThat(result.get(1)).isEqualTo(new DailySolveDto(LocalDate.of(2024, 1, 14), 3L));
        assertThat(result.get(2)).isEqualTo(new DailySolveDto(LocalDate.of(2024, 1, 15), 0L));
    }

    @Test
    @DisplayName("getTrend - last entry in series is always today (from fixed clock)")
    void getTrend_lastEntryIsToday() {
        UUID userId = UUID.randomUUID();
        when(submissionRepository.findDailySolvesByUserIdSince(
                userId, FIXED_DATE.minusDays(6))).thenReturn(List.of());

        List<DailySolveDto> result = service.getTrend(userId, 7);

        assertThat(result).hasSize(7);
        assertThat(result.get(6).date()).isEqualTo(FIXED_DATE);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private TagStatProjection makeTagStat(String tag, long attempted, long solved) {
        return new TagStatProjection() {
            public String getTag()      { return tag; }
            public Long getAttempted()  { return attempted; }
            public Long getSolved()     { return solved; }
        };
    }

    private RatingBucketProjection makeRatingBucket(int bucket, long attempted, long solved) {
        return new RatingBucketProjection() {
            public Integer getBucket()  { return bucket; }
            public Long getAttempted()  { return attempted; }
            public Long getSolved()     { return solved; }
        };
    }

    private DailySolveProjection makeDailySolve(LocalDate date, long count) {
        return new DailySolveProjection() {
            public LocalDate getSolveDate()   { return date; }
            public Long getSolvedCount()       { return count; }
        };
    }
}

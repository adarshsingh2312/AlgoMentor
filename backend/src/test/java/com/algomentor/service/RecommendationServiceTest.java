package com.algomentor.service;

import com.algomentor.config.RecommenderProperties;
import com.algomentor.domain.Problem;
import com.algomentor.domain.Recommendation;
import com.algomentor.domain.RecommendationStatus;
import com.algomentor.domain.Role;
import com.algomentor.domain.Tag;
import com.algomentor.domain.User;
import com.algomentor.dto.RecommendationResponseDto;
import com.algomentor.dto.TagStatDto;
import com.algomentor.repository.ProblemRepository;
import com.algomentor.repository.RecommendationRepository;
import com.algomentor.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RecommendationService — no Spring context, no DB.
 * Uses a fixed Clock and seeded Random for determinism.
 */
class RecommendationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2024, 1, 15);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2024-01-15T00:00:00Z"), ZoneId.of("UTC"));
    // seed=0 produces reproducible shuffle order
    private static final Random SEEDED_RANDOM = new Random(0);

    private RecommendationRepository recommendationRepository;
    private ProblemRepository problemRepository;
    private SubmissionRepository submissionRepository;
    private AnalyticsService analyticsService;
    private RecommenderProperties props;
    private RecommendationService service;

    private User testUser;

    @BeforeEach
    void setUp() {
        recommendationRepository = mock(RecommendationRepository.class);
        problemRepository = mock(ProblemRepository.class);
        submissionRepository = mock(SubmissionRepository.class);
        analyticsService = mock(AnalyticsService.class);

        props = new RecommenderProperties();
        props.setDailyCount(3);
        props.setDefaultRating(1200);
        props.setWindowLower(100);
        props.setWindowUpper(200);
        props.setCooldownDays(14);

        service = new RecommendationService(
                recommendationRepository, problemRepository, submissionRepository,
                analyticsService, props, FIXED_CLOCK, SEEDED_RANDOM);

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("test@example.com");
        testUser.setName("Tester");
        testUser.setRole(Role.USER);
        testUser.setCfHandle("tourist");
    }

    // ─── getOrCreateToday ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getOrCreateToday - returns existing recommendations idempotently")
    void getOrCreateToday_existingRecommendations_returnsWithoutCreating() {
        Recommendation existing = buildRecommendation(RecommendationStatus.SERVED);
        when(recommendationRepository.findByUserIdAndRecommendedDate(testUser.getId(), TODAY))
                .thenReturn(List.of(existing));

        // No solve exists -> status stays SERVED
        when(submissionRepository.existsByUserIdAndProblemIdAndVerdict(
                any(), any(), eq("OK"))).thenReturn(false);

        List<RecommendationResponseDto> result = service.getOrCreateToday(testUser);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo("SERVED");
    }

    @Test
    @DisplayName("getOrCreateToday - syncs SERVED recommendation to SOLVED when problem was solved")
    void getOrCreateToday_servesSync_updatesToSolvedWhenProblemSolved() {
        Recommendation existing = buildRecommendation(RecommendationStatus.SERVED);
        when(recommendationRepository.findByUserIdAndRecommendedDate(testUser.getId(), TODAY))
                .thenReturn(List.of(existing));
        when(submissionRepository.existsByUserIdAndProblemIdAndVerdict(
                any(), any(), eq("OK"))).thenReturn(true);
        when(recommendationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<RecommendationResponseDto> result = service.getOrCreateToday(testUser);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo("SOLVED");
    }

    @Test
    @DisplayName("getOrCreateToday - returns empty list for null user")
    void getOrCreateToday_nullUser_returnsEmpty() {
        assertThat(service.getOrCreateToday(null)).isEmpty();
    }

    @Test
    @DisplayName("getOrCreateToday - uses defaultRating when user has no solved problems")
    void getOrCreateToday_noSolvedProblems_usesDefaultRating() {
        when(recommendationRepository.findByUserIdAndRecommendedDate(testUser.getId(), TODAY))
                .thenReturn(List.of());
        // Starter: no rated solves
        when(submissionRepository.findLastSolvedProblemRatings(testUser.getId(), 20))
                .thenReturn(List.of());
        when(submissionRepository.findSolvedProblemIdsByUserId(testUser.getId()))
                .thenReturn(Set.of());
        when(recommendationRepository.findRecentlyRecommendedProblemIds(any(), any()))
                .thenReturn(Set.of());
        when(analyticsService.getWeakTags(any(), anyInt())).thenReturn(List.of());

        List<Problem> candidates = List.of(
                buildProblem(1200), buildProblem(1300), buildProblem(1400)
        );
        // For starter: no weak tags -> directly goes to general candidates
        when(problemRepository.findGeneralCandidates(anyInt(), anyInt(), anyBoolean(), anySet(), anyInt()))
                .thenReturn(candidates);
        when(problemRepository.findByIdInWithTags(anyList())).thenReturn(candidates);
        when(recommendationRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<RecommendationResponseDto> result = service.getOrCreateToday(testUser);

        assertThat(result).hasSize(3);
        // reason should contain "Starter"
        assertThat(result.get(0).reason()).containsIgnoringCase("starter");
    }

    // ─── updateStatus ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateStatus - returns 404 when recommendation belongs to another user")
    void updateStatus_anotherUsersRecommendation_throws404() {
        UUID otherId = UUID.randomUUID();
        UUID recId   = UUID.randomUUID();

        when(recommendationRepository.findByIdAndUserId(recId, otherId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(otherId, recId, "SKIPPED"))
                .hasMessageContaining("404");
    }

    @Test
    @DisplayName("updateStatus - throws IllegalArgumentException for disallowed status")
    void updateStatus_disallowedStatus_throwsIllegalArgument() {
        UUID userId = testUser.getId();
        UUID recId  = UUID.randomUUID();

        assertThatThrownBy(() -> service.updateStatus(userId, recId, "SOLVED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SKIPPED");
    }

    @Test
    @DisplayName("updateStatus - successfully marks own recommendation as SKIPPED")
    void updateStatus_ownRecommendation_updatesToSkipped() {
        UUID recId = UUID.randomUUID();
        Recommendation rec = buildRecommendation(RecommendationStatus.SERVED);
        rec.setId(recId);

        when(recommendationRepository.findByIdAndUserId(recId, testUser.getId()))
                .thenReturn(Optional.of(rec));
        when(recommendationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RecommendationResponseDto result = service.updateStatus(testUser.getId(), recId, "SKIPPED");

        assertThat(result.status()).isEqualTo("SKIPPED");
        verify(recommendationRepository).save(rec);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private Recommendation buildRecommendation(RecommendationStatus status) {
        Problem problem = buildProblem(1200);
        Recommendation rec = new Recommendation();
        rec.setId(UUID.randomUUID());
        rec.setUser(testUser);
        rec.setProblem(problem);
        rec.setReason("Test reason");
        rec.setStatus(status);
        rec.setRecommendedDate(TODAY);
        return rec;
    }

    private Problem buildProblem(int rating) {
        Problem p = new Problem();
        p.setId(UUID.randomUUID());
        p.setName("Test Problem " + rating);
        p.setRating(rating);
        p.setUrl("https://codeforces.com/problemset/problem/1/A");
        p.setTags(Set.of());
        return p;
    }
}

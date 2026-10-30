package com.algomentor.service;

import com.algomentor.config.MailProperties;
import com.algomentor.domain.Role;
import com.algomentor.domain.User;
import com.algomentor.dto.DailySolveDto;
import com.algomentor.dto.DigestRunSummaryDto;
import com.algomentor.dto.RecommendationResponseDto;
import com.algomentor.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DigestServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-10-30T07:00:00Z"), ZoneId.of("UTC"));

    private UserRepository userRepository;
    private RecommendationService recommendationService;
    private AnalyticsService analyticsService;
    private MailService mailService;
    private MailProperties mailProperties;
    private DigestService digestService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        recommendationService = mock(RecommendationService.class);
        analyticsService = mock(AnalyticsService.class);
        mailService = mock(MailService.class);

        mailProperties = new MailProperties();
        mailProperties.setEnabled(true);

        digestService = new DigestService(
                userRepository, recommendationService, analyticsService,
                mailService, mailProperties, FIXED_CLOCK);
    }

    @Test
    @DisplayName("Test 1: Digest for one user sent once; running sendDailyDigests() twice same day sends exactly one email")
    void sendDailyDigests_idempotentPerDay_sendsExactlyOnce() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setName("Coder");
        user.setRole(Role.USER);
        user.setCfHandle("tourist");
        user.setDigestEnabled(true);
        user.setLastDigestDate(null);

        when(userRepository.findAll()).thenReturn(List.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        RecommendationResponseDto rec = new RecommendationResponseDto(
                UUID.randomUUID(), "Problem A", 1200, List.of("greedy"),
                "https://codeforces.com/problemset/problem/1/A", "Practice", "SERVED");
        when(recommendationService.getOrCreateToday(user)).thenReturn(List.of(rec));
        when(analyticsService.getTrend(user.getId(), 7)).thenReturn(List.of(new DailySolveDto(TODAY, 3L)));
        when(mailService.sendDigestAsync(eq(user), anyList(), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // First run on today: should send 1 email
        DigestRunSummaryDto firstRun = digestService.sendDailyDigests();
        assertThat(firstRun.sent()).isEqualTo(1);
        assertThat(firstRun.skipped()).isEqualTo(0);
        assertThat(firstRun.failed()).isEqualTo(0);
        assertThat(user.getLastDigestDate()).isEqualTo(TODAY);

        verify(mailService, times(1)).sendDigestAsync(eq(user), anyList(), anyLong());

        // Second run on the same day: user already has lastDigestDate == TODAY, should skip
        DigestRunSummaryDto secondRun = digestService.sendDailyDigests();
        assertThat(secondRun.sent()).isEqualTo(0);
        assertThat(secondRun.skipped()).isEqualTo(1);
        assertThat(secondRun.failed()).isEqualTo(0);

        // Verify no second email was sent
        verify(mailService, times(1)).sendDigestAsync(eq(user), anyList(), anyLong());
    }

    @Test
    @DisplayName("Test 2: If sending fails for user A, user B still gets the mail and A's lastDigestDate is NOT set")
    void sendDailyDigests_partialFailure_isolatesErrorsAndPreservesDates() {
        User userA = new User();
        userA.setId(UUID.randomUUID());
        userA.setEmail("userA@example.com");
        userA.setName("User A");
        userA.setRole(Role.USER);
        userA.setCfHandle("handleA");
        userA.setDigestEnabled(true);
        userA.setLastDigestDate(null);

        User userB = new User();
        userB.setId(UUID.randomUUID());
        userB.setEmail("userB@example.com");
        userB.setName("User B");
        userB.setRole(Role.USER);
        userB.setCfHandle("handleB");
        userB.setDigestEnabled(true);
        userB.setLastDigestDate(null);

        when(userRepository.findAll()).thenReturn(List.of(userA, userB));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        when(recommendationService.getOrCreateToday(any())).thenReturn(List.of());
        when(analyticsService.getTrend(any(), eq(7))).thenReturn(List.of());

        // userA fails with exception
        when(mailService.sendDigestAsync(eq(userA), anyList(), anyLong()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Mail server down")));

        // userB succeeds
        when(mailService.sendDigestAsync(eq(userB), anyList(), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(null));

        DigestRunSummaryDto result = digestService.sendDailyDigests();

        assertThat(result.sent()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);

        // User A's lastDigestDate was NOT updated due to failure
        assertThat(userA.getLastDigestDate()).isNull();

        // User B's lastDigestDate was updated
        assertThat(userB.getLastDigestDate()).isEqualTo(TODAY);

        verify(mailService).sendDigestAsync(eq(userA), anyList(), anyLong());
        verify(mailService).sendDigestAsync(eq(userB), anyList(), anyLong());
    }
}


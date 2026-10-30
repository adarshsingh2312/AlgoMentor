package com.algomentor.service;

import com.algomentor.config.MailProperties;
import com.algomentor.domain.User;
import com.algomentor.dto.DailySolveDto;
import com.algomentor.dto.DigestRunSummaryDto;
import com.algomentor.dto.RecommendationResponseDto;
import com.algomentor.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class DigestService {

    private static final Logger log = LoggerFactory.getLogger(DigestService.class);

    private final UserRepository userRepository;
    private final RecommendationService recommendationService;
    private final AnalyticsService analyticsService;
    private final MailService mailService;
    private final MailProperties mailProperties;
    private final Clock clock;

    public DigestService(UserRepository userRepository,
                         RecommendationService recommendationService,
                         AnalyticsService analyticsService,
                         MailService mailService,
                         MailProperties mailProperties,
                         Clock clock) {
        this.userRepository = userRepository;
        this.recommendationService = recommendationService;
        this.analyticsService = analyticsService;
        this.mailService = mailService;
        this.mailProperties = mailProperties;
        this.clock = clock;
    }

    /**
     * Builds and sends daily problem digests to all eligible users.
     * Skips users with digestEnabled=false, missing cfHandle, or lastDigestDate == today.
     */
    public DigestRunSummaryDto sendDailyDigests() {
        long startTime = System.currentTimeMillis();
        LocalDate today = LocalDate.now(clock);

        List<User> users = userRepository.findAll();
        int sent = 0;
        int skipped = 0;
        int failed = 0;

        for (User user : users) {
            if (!user.isDigestEnabled()
                    || user.getCfHandle() == null
                    || user.getCfHandle().isBlank()
                    || today.equals(user.getLastDigestDate())) {
                skipped++;
                continue;
            }

            try {
                List<RecommendationResponseDto> recs = recommendationService.getOrCreateToday(user);

                long solvedLast7Days = analyticsService.getTrend(user.getId(), 7).stream()
                        .mapToLong(DailySolveDto::count)
                        .sum();

                // Hand send to @Async method on separate MailService bean
                mailService.sendDigestAsync(user, recs, solvedLast7Days).join();

                // Set lastDigestDate only after successful delivery
                user.setLastDigestDate(today);
                userRepository.save(user);
                sent++;
            } catch (Exception ex) {
                log.error("[Digest] Failed to process daily digest for user {} (id={}): {}",
                        user.getEmail(), user.getId(), ex.getMessage(), ex);
                failed++;
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("[Digest] Daily digest run completed: sent={}, skipped={}, failed={}, duration={}ms",
                sent, skipped, failed, duration);

        return new DigestRunSummaryDto(sent, skipped, failed, duration);
    }
}


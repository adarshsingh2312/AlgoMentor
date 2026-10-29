package com.algomentor.service;

import com.algomentor.config.RecommenderProperties;
import com.algomentor.domain.Problem;
import com.algomentor.domain.Recommendation;
import com.algomentor.domain.RecommendationStatus;
import com.algomentor.domain.Tag;
import com.algomentor.domain.User;
import com.algomentor.dto.RecommendationResponseDto;
import com.algomentor.dto.TagStatDto;
import com.algomentor.repository.ProblemRepository;
import com.algomentor.repository.RecommendationRepository;
import com.algomentor.repository.SubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private final RecommendationRepository recommendationRepository;
    private final ProblemRepository problemRepository;
    private final SubmissionRepository submissionRepository;
    private final AnalyticsService analyticsService;
    private final RecommenderProperties props;
    private final Clock clock;
    private final Random random;

    public RecommendationService(RecommendationRepository recommendationRepository,
                                 ProblemRepository problemRepository,
                                 SubmissionRepository submissionRepository,
                                 AnalyticsService analyticsService,
                                 RecommenderProperties props,
                                 Clock clock,
                                 Random random) {
        this.recommendationRepository = recommendationRepository;
        this.problemRepository = problemRepository;
        this.submissionRepository = submissionRepository;
        this.analyticsService = analyticsService;
        this.props = props;
        this.clock = clock;
        this.random = random;
    }

    /**
     * Retrieves today's recommendations for the user or creates them idempotently.
     */
    public List<RecommendationResponseDto> getOrCreateToday(User user) {
        if (user == null) {
            return List.of();
        }

        LocalDate today = LocalDate.now(clock);

        // 1. Idempotent check: if recommendations already exist for (user, today), return them
        List<Recommendation> existing = recommendationRepository.findByUserIdAndRecommendedDate(user.getId(), today);
        if (!existing.isEmpty()) {
            return syncAndMapRecommendations(user.getId(), existing);
        }

        // 2. Base rating calculation (median rating of last 20 solved rated problems)
        List<Integer> lastRatings = submissionRepository.findLastSolvedProblemRatings(user.getId(), 20);
        boolean isStarter = (lastRatings == null || lastRatings.isEmpty() || user.getCfHandle() == null);

        int baseRating;
        if (isStarter) {
            baseRating = props.getDefaultRating();
        } else {
            List<Integer> sorted = new ArrayList<>(lastRatings);
            Collections.sort(sorted);
            int n = sorted.size();
            baseRating = (n % 2 == 1) ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
        }

        int roundedBase = (int) (Math.round(baseRating / 100.0) * 100);
        int minRating = roundedBase - props.getWindowLower();
        int maxRating = roundedBase + props.getWindowUpper();

        // 3. Exclusions: all solved problems + problems recommended in the last 14 days
        Set<UUID> excludedIds = new HashSet<>(submissionRepository.findSolvedProblemIdsByUserId(user.getId()));
        LocalDate cooldownDate = today.minusDays(props.getCooldownDays());
        excludedIds.addAll(recommendationRepository.findRecentlyRecommendedProblemIds(user.getId(), cooldownDate));

        // 4. Candidate selection (prefer weak tags, then fill with general candidates)
        int dailyCount = props.getDailyCount();
        List<TagStatDto> weakTags = isStarter ? List.of() : analyticsService.getWeakTags(user.getId(), 5);
        Map<String, TagStatDto> weakTagMap = weakTags.stream()
                .collect(Collectors.toMap(TagStatDto::tag, t -> t, (a, b) -> a));
        List<String> weakTagNames = weakTags.stream().map(TagStatDto::tag).toList();

        List<Problem> selectedProblems = new ArrayList<>();
        Map<UUID, String> problemReasons = new HashMap<>();

        if (!weakTagNames.isEmpty()) {
            List<Problem> weakCandidates = new ArrayList<>(problemRepository.findWeakTagCandidates(
                    minRating, maxRating, weakTagNames, !excludedIds.isEmpty(), excludedIds, 50));
            Collections.shuffle(weakCandidates, random);

            for (Problem p : weakCandidates) {
                if (selectedProblems.size() >= dailyCount) {
                    break;
                }
                selectedProblems.add(p);
                excludedIds.add(p.getId());

                String matchedWeakTag = p.getTags() != null
                        ? p.getTags().stream().map(Tag::getName).filter(weakTagMap::containsKey).findFirst().orElse(null)
                        : null;
                if (matchedWeakTag == null && !weakTagNames.isEmpty()) {
                    matchedWeakTag = weakTagNames.get(0);
                }

                TagStatDto stat = weakTagMap.get(matchedWeakTag);
                String reason = stat != null
                        ? "Weak tag: " + matchedWeakTag + " (" + stat.solved() + "/" + stat.attempted() + " solved), rating " + p.getRating()
                        : "Rating " + p.getRating() + " practice problem";
                problemReasons.put(p.getId(), reason);
            }
        }

        if (selectedProblems.size() < dailyCount) {
            List<Problem> generalCandidates = new ArrayList<>(problemRepository.findGeneralCandidates(
                    minRating, maxRating, !excludedIds.isEmpty(), excludedIds, 50));
            Collections.shuffle(generalCandidates, random);

            for (Problem p : generalCandidates) {
                if (selectedProblems.size() >= dailyCount) {
                    break;
                }
                selectedProblems.add(p);
                excludedIds.add(p.getId());

                String reason = isStarter
                        ? "Starter problem, rating " + p.getRating()
                        : "Rating " + p.getRating() + " practice problem";
                problemReasons.put(p.getId(), reason);
            }
        }

        if (selectedProblems.isEmpty()) {
            return List.of();
        }

        // Fetch selected problems with tags loaded eagerly
        List<Problem> problemsWithTags = problemRepository.findByIdInWithTags(
                selectedProblems.stream().map(Problem::getId).toList());

        List<Recommendation> toSave = new ArrayList<>();
        for (Problem p : problemsWithTags) {
            Recommendation r = new Recommendation();
            r.setUser(user);
            r.setProblem(p);
            r.setReason(problemReasons.getOrDefault(p.getId(), "Daily recommendation"));
            r.setStatus(RecommendationStatus.SERVED);
            r.setRecommendedDate(today);
            toSave.add(r);
        }

        try {
            List<Recommendation> saved = recommendationRepository.saveAll(toSave);
            return saved.stream().map(RecommendationResponseDto::from).toList();
        } catch (DataIntegrityViolationException ex) {
            // Concurrent creation on same day; re-fetch winning records
            log.warn("[Recommender] Concurrent generation detected for user {}; returning saved recommendations", user.getId());
            List<Recommendation> reloaded = recommendationRepository.findByUserIdAndRecommendedDate(user.getId(), today);
            return syncAndMapRecommendations(user.getId(), reloaded);
        }
    }

    /**
     * Updates status of a recommendation (only client SKIPPED allowed; SOLVED is system set).
     * If recommendation does not belong to user, throws 404.
     */
    public RecommendationResponseDto updateStatus(UUID userId, UUID recommendationId, String newStatus) {
        if (!"SKIPPED".equalsIgnoreCase(newStatus)) {
            throw new IllegalArgumentException("Only SKIPPED status update is allowed from client");
        }

        Recommendation rec = recommendationRepository.findByIdAndUserId(recommendationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recommendation not found"));

        rec.setStatus(RecommendationStatus.SKIPPED);
        Recommendation updated = recommendationRepository.save(rec);
        return RecommendationResponseDto.from(updated);
    }

    /**
     * Checks if any SERVED recommendation has since been solved by the user, updating to SOLVED.
     */
    private List<RecommendationResponseDto> syncAndMapRecommendations(UUID userId, List<Recommendation> recommendations) {
        for (Recommendation r : recommendations) {
            if (r.getStatus() == RecommendationStatus.SERVED && r.getProblem() != null) {
                boolean solved = submissionRepository.existsByUserIdAndProblemIdAndVerdict(userId, r.getProblem().getId(), "OK");
                if (solved) {
                    r.setStatus(RecommendationStatus.SOLVED);
                    recommendationRepository.save(r);
                }
            }
        }
        return recommendations.stream().map(RecommendationResponseDto::from).toList();
    }
}

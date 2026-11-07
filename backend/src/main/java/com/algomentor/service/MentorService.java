package com.algomentor.service;

import com.algomentor.domain.Problem;
import com.algomentor.domain.Tag;
import com.algomentor.dto.AiProblemDto;
import com.algomentor.dto.MentorHintResponseDto;
import com.algomentor.dto.MentorResetResponseDto;
import com.algomentor.dto.StruggledProblemDto;
import com.algomentor.dto.StruggledProblemProjection;
import com.algomentor.repository.ProblemRepository;
import com.algomentor.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MentorService {

    private final ProblemRepository problemRepository;
    private final SubmissionRepository submissionRepository;
    private final AiServiceClient aiServiceClient;

    @Transactional(readOnly = true)
    public MentorHintResponseDto getHint(UUID userId, UUID problemId) {
        Problem problem = problemRepository.findByIdWithTags(problemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Problem not found"));

        List<String> tags = problem.getTags() != null
                ? problem.getTags().stream().map(Tag::getName).toList()
                : List.of();

        AiProblemDto problemDto = new AiProblemDto(
                problem.getId(),
                problem.getName(),
                problem.getRating(),
                tags,
                problem.getUrl()
        );

        return aiServiceClient.getHint(userId, problemDto);
    }

    public MentorResetResponseDto resetHint(UUID userId, UUID problemId) {
        // Enforce 404 if problem is unknown
        if (!problemRepository.existsById(problemId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Problem not found");
        }
        return aiServiceClient.resetHint(userId, problemId);
    }

    @Transactional(readOnly = true)
    public List<StruggledProblemDto> getStruggledProblems(UUID userId) {
        if (userId == null) {
            return List.of();
        }
        var projections = submissionRepository.findStruggledProblems(userId, 2, 10);
        if (projections == null || projections.isEmpty()) {
            return List.of();
        }

        List<UUID> problemIds = projections.stream().map(StruggledProblemProjection::getProblemId).toList();
        Map<UUID, List<String>> tagsByProblem = problemRepository.findByIdInWithTags(problemIds).stream()
                .collect(Collectors.toMap(
                        Problem::getId,
                        p -> p.getTags() != null ? p.getTags().stream().map(Tag::getName).toList() : List.of(),
                        (a, b) -> a
                ));

        return projections.stream()
                .map(p -> new StruggledProblemDto(
                        p.getProblemId(),
                        p.getProblemName(),
                        p.getRating(),
                        p.getUrl(),
                        tagsByProblem.getOrDefault(p.getProblemId(), List.of()),
                        p.getFailedAttempts() != null ? p.getFailedAttempts() : 0L
                ))
                .toList();
    }
}


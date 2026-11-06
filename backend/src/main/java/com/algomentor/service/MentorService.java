package com.algomentor.service;

import com.algomentor.domain.Problem;
import com.algomentor.domain.Tag;
import com.algomentor.dto.AiProblemDto;
import com.algomentor.dto.MentorHintResponseDto;
import com.algomentor.dto.MentorResetResponseDto;
import com.algomentor.repository.ProblemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MentorService {

    private final ProblemRepository problemRepository;
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
}


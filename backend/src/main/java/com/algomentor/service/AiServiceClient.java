package com.algomentor.service;

import com.algomentor.config.AiProperties;
import com.algomentor.dto.AiAskRequest;
import com.algomentor.dto.AiDeleteResponse;
import com.algomentor.dto.AiIngestRequest;
import com.algomentor.dto.AiIngestResponse;
import com.algomentor.dto.AiMentorHintRequest;
import com.algomentor.dto.AiProblemDto;
import com.algomentor.dto.AskNotesResponseDto;
import com.algomentor.dto.MentorHintResponseDto;
import com.algomentor.dto.MentorResetResponseDto;
import com.algomentor.exception.AiUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.UUID;

@Service
public class AiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);

    private final RestClient restClient;

    public AiServiceClient(AiProperties aiProperties, RestClient.Builder restClientBuilder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(aiProperties.getAskTimeoutSeconds()));

        this.restClient = restClientBuilder
                .baseUrl(aiProperties.getBaseUrl())
                .defaultHeader("X-Service-Key", aiProperties.getServiceKey() != null ? aiProperties.getServiceKey() : "")
                .requestFactory(requestFactory)
                .build();
    }

    public AiIngestResponse ingest(UUID documentId, UUID userId, String displayName, String text) {
        try {
            AiIngestRequest request = new AiIngestRequest(documentId, userId, displayName, text);
            return restClient.post()
                    .uri("/ingest")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiIngestResponse.class);
        } catch (ResourceAccessException | RestClientResponseException e) {
            log.error("AI service ingest failed for documentId={}: {}", documentId, e.getMessage());
            throw new AiUnavailableException("AI service unavailable during document ingestion", e);
        }
    }

    public AiDeleteResponse delete(UUID documentId, UUID userId) {
        try {
            return restClient.delete()
                    .uri(uriBuilder -> uriBuilder
                            .path("/documents/{documentId}")
                            .queryParam("userId", userId)
                            .build(documentId))
                    .retrieve()
                    .body(AiDeleteResponse.class);
        } catch (ResourceAccessException | RestClientResponseException e) {
            log.error("AI service delete failed for documentId={}: {}", documentId, e.getMessage());
            throw new AiUnavailableException("AI service unavailable during document deletion", e);
        }
    }

    public AskNotesResponseDto ask(UUID userId, String question, int topK) {
        try {
            AiAskRequest request = new AiAskRequest(userId, question, topK);
            return restClient.post()
                    .uri("/ask")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AskNotesResponseDto.class);
        } catch (ResourceAccessException | RestClientResponseException e) {
            log.error("AI service ask failed for userId={}: {}", userId, e.getMessage());
            throw new AiUnavailableException("AI service unavailable during question retrieval", e);
        }
    }

    public MentorHintResponseDto getHint(UUID userId, AiProblemDto problem) {
        try {
            AiMentorHintRequest request = new AiMentorHintRequest(userId, problem);
            return restClient.post()
                    .uri("/mentor/hint")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(MentorHintResponseDto.class);
        } catch (ResourceAccessException | RestClientResponseException e) {
            log.error("AI service getHint failed for userId={}, problemId={}: {}", userId, problem.id(), e.getMessage());
            throw new AiUnavailableException("AI service unavailable during hint generation", e);
        }
    }

    public MentorResetResponseDto resetHint(UUID userId, UUID problemId) {
        try {
            return restClient.delete()
                    .uri(uriBuilder -> uriBuilder
                            .path("/mentor/hint")
                            .queryParam("userId", userId)
                            .queryParam("problemId", problemId)
                            .build())
                    .retrieve()
                    .body(MentorResetResponseDto.class);
        } catch (ResourceAccessException | RestClientResponseException e) {
            log.error("AI service resetHint failed for userId={}, problemId={}: {}", userId, problemId, e.getMessage());
            throw new AiUnavailableException("AI service unavailable during hint reset", e);
        }
    }
}


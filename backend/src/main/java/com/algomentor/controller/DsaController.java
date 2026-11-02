package com.algomentor.controller;

import com.algomentor.dto.DsaRoadmapDto;
import com.algomentor.dto.UpdateProgressRequest;
import com.algomentor.service.DsaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/me/dsa")
@RequiredArgsConstructor
public class DsaController {

    private final DsaService dsaService;

    @GetMapping
    public ResponseEntity<DsaRoadmapDto> getRoadmap(
            @AuthenticationPrincipal UUID userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return ResponseEntity.ok(dsaService.getRoadmap(userId));
    }

    @PutMapping("/{topicId}/progress")
    public ResponseEntity<Void> updateProgress(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID topicId,
            @Valid @RequestBody UpdateProgressRequest request) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        dsaService.updateProgress(userId, topicId, request.status());
        return ResponseEntity.noContent().build(); // 204 — idempotent PUT returns no body
    }
}


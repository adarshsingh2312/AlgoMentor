package com.algomentor.controller;

import com.algomentor.dto.MentorHintRequestDto;
import com.algomentor.dto.MentorHintResponseDto;
import com.algomentor.dto.MentorResetResponseDto;
import com.algomentor.dto.StruggledProblemDto;
import com.algomentor.service.MentorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/me/mentor")
@RequiredArgsConstructor
public class MentorController {

    private final MentorService mentorService;

    @GetMapping("/struggles")
    public ResponseEntity<List<StruggledProblemDto>> getStruggles(
            @AuthenticationPrincipal UUID userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return ResponseEntity.ok(mentorService.getStruggledProblems(userId));
    }

    @PostMapping("/hint")
    public ResponseEntity<MentorHintResponseDto> getHint(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody MentorHintRequestDto request) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return ResponseEntity.ok(mentorService.getHint(userId, request.problemId()));
    }

    @DeleteMapping("/hint")
    public ResponseEntity<MentorResetResponseDto> resetHint(
            @AuthenticationPrincipal UUID userId,
            @RequestParam("problemId") UUID problemId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return ResponseEntity.ok(mentorService.resetHint(userId, problemId));
    }
}


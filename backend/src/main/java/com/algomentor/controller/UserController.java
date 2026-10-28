package com.algomentor.controller;

import com.algomentor.domain.User;
import com.algomentor.dto.SyncResult;
import com.algomentor.dto.UpdateCfHandleRequest;
import com.algomentor.dto.UserResponseDto;
import com.algomentor.repository.UserRepository;
import com.algomentor.service.SyncService;
import com.algomentor.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final SyncService syncService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<UserResponseDto> getMe(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    @PutMapping("/cf-handle")
    public ResponseEntity<UserResponseDto> linkCfHandle(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody UpdateCfHandleRequest request) {
        return ResponseEntity.ok(userService.updateCfHandle(userId, request.handle()));
    }

    @PostMapping("/sync")
    public ResponseEntity<SyncResult> syncMe(@AuthenticationPrincipal UUID userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return ResponseEntity.ok(syncService.syncUserSubmissions(user));
    }
}
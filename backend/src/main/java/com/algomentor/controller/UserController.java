package com.algomentor.controller;

import com.algomentor.domain.User;
import com.algomentor.dto.SyncResult;
import com.algomentor.dto.UpdateCfHandleRequest;
import com.algomentor.dto.UserResponseDto;
import com.algomentor.repository.UserRepository;
import com.algomentor.service.CodeforcesService;
import com.algomentor.service.SyncService;
import com.algomentor.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final SyncService syncService;
    private final CodeforcesService codeforcesService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<UserResponseDto> getMe(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    @PutMapping("/cf-handle")
    public ResponseEntity<UserResponseDto> linkCfHandle(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody UpdateCfHandleRequest request) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        String handle = request.handle().trim();

        // Step 1: Verify handle & fetch rating/rank on Codeforces BEFORE opening any DB transaction
        CodeforcesService.CfUserInfo cfInfo = codeforcesService.fetchUserInfo(handle);

        // Step 2: Atomic DB commit — clears old data, saves new handle & rating. Tx commits before this returns.
        User updated = userService.updateCfHandle(userId, handle, cfInfo.rating(), cfInfo.rank());

        // Step 3: Trigger sync AFTER the DB transaction has fully committed (safe from rollback-only)
        try {
            syncService.syncUserSubmissions(updated);
        } catch (Exception ex) {
            log.warn("[Controller] Auto-sync after handle change failed for user {}: {}", userId, ex.getMessage());
        }

        return ResponseEntity.ok(UserResponseDto.from(updated));
    }

    @DeleteMapping("/cf-handle")
    public ResponseEntity<UserResponseDto> disconnectCfHandle(
            @AuthenticationPrincipal UUID userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return ResponseEntity.ok(userService.disconnectCfHandle(userId));
    }

    @PostMapping("/sync")
    public ResponseEntity<SyncResult> syncMe(@AuthenticationPrincipal UUID userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        SyncResult result = syncService.syncUserSubmissions(user);
        if (user.getCfHandle() != null && !user.getCfHandle().isBlank()) {
            try {
                CodeforcesService.CfUserInfo cfInfo = codeforcesService.fetchUserInfo(user.getCfHandle());
                userService.updateCfRating(userId, cfInfo.rating(), cfInfo.rank());
            } catch (Exception ex) {
                log.warn("[Controller] Failed to refresh CF rating during sync for user {}: {}", userId, ex.getMessage());
            }
        }
        return ResponseEntity.ok(result);
    }
}
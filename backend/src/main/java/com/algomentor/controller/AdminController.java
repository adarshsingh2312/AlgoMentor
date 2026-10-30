package com.algomentor.controller;

import com.algomentor.dto.AdminPingResponse;
import com.algomentor.dto.DigestRunSummaryDto;
import com.algomentor.dto.SyncResult;
import com.algomentor.service.DigestService;
import com.algomentor.service.SyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final SyncService syncService;
    private final DigestService digestService;

    @GetMapping("/ping")
    public ResponseEntity<AdminPingResponse> ping() {
        return ResponseEntity.ok(new AdminPingResponse("pong", true));
    }

    @PostMapping("/sync/problemset")
    public ResponseEntity<SyncResult> syncProblemset() {
        return ResponseEntity.ok(syncService.syncProblemset());
    }

    @PostMapping("/digest/run")
    public ResponseEntity<DigestRunSummaryDto> runDigest() {
        return ResponseEntity.ok(digestService.sendDailyDigests());
    }
}

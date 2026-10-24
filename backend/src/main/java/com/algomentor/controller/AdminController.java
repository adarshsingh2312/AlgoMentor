package com.algomentor.controller;

import com.algomentor.dto.AdminPingResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @GetMapping("/ping")
    public ResponseEntity<AdminPingResponse> ping() {
        return ResponseEntity.ok(new AdminPingResponse("pong", true));
    }
}

package com.algomentor.controller;

import com.algomentor.dto.AskNotesRequest;
import com.algomentor.dto.AskNotesResponseDto;
import com.algomentor.dto.NoteDocumentDetailDto;
import com.algomentor.dto.NoteDocumentListDto;
import com.algomentor.dto.NoteUploadResult;
import com.algomentor.service.NoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/me/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<NoteDocumentDetailDto> uploadNote(
            @AuthenticationPrincipal UUID userId,
            @RequestParam("file") MultipartFile file) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        NoteUploadResult result = noteService.upload(userId, file);
        if (result.isDuplicate()) {
            return ResponseEntity.ok(result.note());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(result.note());
    }

    @PostMapping("/{id}/reindex")
    public ResponseEntity<NoteDocumentDetailDto> reindexNote(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return ResponseEntity.ok(noteService.reindex(userId, id));
    }

    @PostMapping("/ask")
    public ResponseEntity<AskNotesResponseDto> askNotes(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody AskNotesRequest request) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return ResponseEntity.ok(noteService.ask(userId, request.question()));
    }

    @GetMapping
    public ResponseEntity<List<NoteDocumentListDto>> listNotes(
            @AuthenticationPrincipal UUID userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return ResponseEntity.ok(noteService.listNotes(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NoteDocumentDetailDto> getNote(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return ResponseEntity.ok(noteService.getNote(userId, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNote(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        noteService.deleteNote(userId, id);
        return ResponseEntity.noContent().build();
    }
}

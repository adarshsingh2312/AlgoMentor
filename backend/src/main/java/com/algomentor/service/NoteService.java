package com.algomentor.service;

import com.algomentor.config.NotesProperties;
import com.algomentor.domain.NoteDocument;
import com.algomentor.domain.NoteStatus;
import com.algomentor.domain.User;
import com.algomentor.dto.NoteDocumentDetailDto;
import com.algomentor.dto.NoteDocumentListDto;
import com.algomentor.dto.NoteUploadResult;
import com.algomentor.repository.NoteDocumentRepository;
import com.algomentor.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteDocumentRepository noteDocumentRepository;
    private final UserRepository userRepository;
    private final NotesProperties notesProperties;

    @Transactional
    public NoteUploadResult upload(UUID userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required and must not be empty");
        }

        String rawFilename = file.getOriginalFilename();
        if (rawFilename == null || rawFilename.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filename must not be empty");
        }

        // Sanitize filename to prevent path traversal
        String sanitizedName = sanitizeFilename(rawFilename);
        String lowerName = sanitizedName.toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".md") && !lowerName.endsWith(".txt")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only .md and .txt files are allowed");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read file", e);
        }

        if (bytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }

        String content = new String(bytes, StandardCharsets.UTF_8);
        String contentHash = computeSha256Hex(bytes);

        // Deduplication: if user already uploaded identical content, return existing document
        var existing = noteDocumentRepository.findByUserIdAndContentHash(userId, contentHash);
        if (existing.isPresent()) {
            return new NoteUploadResult(toDetailDto(existing.get()), true);
        }

        // Check per-user storage quota
        long count = noteDocumentRepository.countByUserId(userId);
        if (count >= notesProperties.getMaxDocuments()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Maximum note limit reached (" + notesProperties.getMaxDocuments() + ")");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = lowerName.endsWith(".md") ? "text/markdown" : "text/plain";
        }

        NoteDocument doc = new NoteDocument();
        doc.setUser(user);
        doc.setDisplayName(sanitizedName);
        doc.setContentType(contentType);
        doc.setSizeBytes(bytes.length);
        doc.setContentHash(contentHash);
        doc.setContent(content);
        doc.setStatus(NoteStatus.UPLOADED);

        NoteDocument saved = noteDocumentRepository.save(doc);
        return new NoteUploadResult(toDetailDto(saved), false);
    }

    @Transactional(readOnly = true)
    public List<NoteDocumentListDto> listNotes(UUID userId) {
        return noteDocumentRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toListDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public NoteDocumentDetailDto getNote(UUID userId, UUID noteId) {
        // Return 404 if not found or belongs to another user (prevents ID enumeration)
        NoteDocument doc = noteDocumentRepository.findByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found"));
        return toDetailDto(doc);
    }

    @Transactional
    public void deleteNote(UUID userId, UUID noteId) {
        // Return 404 if not found or belongs to another user
        NoteDocument doc = noteDocumentRepository.findByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found"));
        noteDocumentRepository.delete(doc);
    }

    private String sanitizeFilename(String filename) {
        // Strip any path traversal sequences or directory separators
        String nameOnly = Path.of(filename).getFileName().toString();
        // Replace non-whitelisted characters or excessive whitespace if needed, and truncate to 255 chars
        nameOnly = nameOnly.trim();
        if (nameOnly.length() > 255) {
            int extIndex = nameOnly.lastIndexOf('.');
            if (extIndex > 0 && extIndex >= nameOnly.length() - 10) {
                String ext = nameOnly.substring(extIndex);
                nameOnly = nameOnly.substring(0, 255 - ext.length()) + ext;
            } else {
                nameOnly = nameOnly.substring(0, 255);
            }
        }
        return nameOnly;
    }

    private String computeSha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private NoteDocumentListDto toListDto(NoteDocument doc) {
        return new NoteDocumentListDto(
                doc.getId(),
                doc.getDisplayName(),
                doc.getSizeBytes(),
                doc.getStatus(),
                doc.getCreatedAt()
        );
    }

    private NoteDocumentDetailDto toDetailDto(NoteDocument doc) {
        return new NoteDocumentDetailDto(
                doc.getId(),
                doc.getDisplayName(),
                doc.getContentType(),
                doc.getSizeBytes(),
                doc.getContentHash(),
                doc.getContent(),
                doc.getStatus(),
                doc.getCreatedAt()
        );
    }
}


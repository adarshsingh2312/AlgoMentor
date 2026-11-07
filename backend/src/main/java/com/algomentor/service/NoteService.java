package com.algomentor.service;

import com.algomentor.config.NotesProperties;
import com.algomentor.domain.NoteDocument;
import com.algomentor.domain.NoteStatus;
import com.algomentor.domain.User;
import com.algomentor.dto.AskNotesResponseDto;
import com.algomentor.dto.NoteDocumentDetailDto;
import com.algomentor.dto.NoteDocumentListDto;
import com.algomentor.dto.NoteUploadResult;
import com.algomentor.exception.AiUnavailableException;
import com.algomentor.repository.NoteDocumentRepository;
import com.algomentor.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

    private static final Logger log = LoggerFactory.getLogger(NoteService.class);

    private final NoteDocumentRepository noteDocumentRepository;
    private final UserRepository userRepository;
    private final NotesProperties notesProperties;
    private final AiServiceClient aiServiceClient;

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

        // Step 1: Save initial note in short transaction (UPLOADED)
        NoteDocument saved = saveInitialNote(user, sanitizedName, contentType, bytes.length, contentHash, content);

        // Step 2: Ingest with AI service OUTSIDE any DB transaction
        NoteStatus finalStatus;
        try {
            aiServiceClient.ingest(saved.getId(), userId, saved.getDisplayName(), saved.getContent());
            finalStatus = NoteStatus.INDEXED;
        } catch (Exception e) {
            log.warn("AI indexing failed for noteId={}: {}. Marking status as FAILED", saved.getId(), e.getMessage());
            finalStatus = NoteStatus.FAILED;
        }

        // Step 3: Update note status in short transaction
        NoteDocument updated = updateNoteStatus(saved.getId(), finalStatus);
        return new NoteUploadResult(toDetailDto(updated), false);
    }

    @Transactional
    public NoteDocument saveInitialNote(User user, String displayName, String contentType,
                                        long sizeBytes, String contentHash, String content) {
        NoteDocument doc = new NoteDocument();
        doc.setUser(user);
        doc.setDisplayName(displayName);
        doc.setContentType(contentType);
        doc.setSizeBytes(sizeBytes);
        doc.setContentHash(contentHash);
        doc.setContent(content);
        doc.setStatus(NoteStatus.UPLOADED);
        return noteDocumentRepository.save(doc);
    }

    @Transactional
    public NoteDocument updateNoteStatus(UUID noteId, NoteStatus status) {
        NoteDocument doc = noteDocumentRepository.findById(noteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found"));
        doc.setStatus(status);
        return noteDocumentRepository.save(doc);
    }

    public NoteDocumentDetailDto reindex(UUID userId, UUID noteId) {
        NoteDocument doc = noteDocumentRepository.findByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found"));

        updateNoteStatus(doc.getId(), NoteStatus.INDEXING);

        try {
            aiServiceClient.ingest(doc.getId(), userId, doc.getDisplayName(), doc.getContent());
            NoteDocument updated = updateNoteStatus(doc.getId(), NoteStatus.INDEXED);
            return toDetailDto(updated);
        } catch (Exception e) {
            log.error("Reindex failed for noteId={}: {}", noteId, e.getMessage());
            updateNoteStatus(doc.getId(), NoteStatus.FAILED);
            throw e;
        }
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

    public void deleteNote(UUID userId, UUID noteId) {
        // Check document ownership first (throws 404 if not found or belongs to another user)
        NoteDocument doc = noteDocumentRepository.findByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found"));

        // Order is critical: delete chunks in AI service FIRST
        // If AI service is down, this throws AiUnavailableException -> 502, keeping the DB row intact
        aiServiceClient.delete(noteId, userId);

        // Second: delete row in PostgreSQL
        deleteNoteRow(doc);
    }

    @Transactional
    public void deleteNoteRow(NoteDocument doc) {
        noteDocumentRepository.delete(doc);
    }

    public AskNotesResponseDto ask(UUID userId, String question) {
        return aiServiceClient.ask(userId, question, 4);
    }

    public NoteDocumentDetailDto createMistakeNote(UUID userId, com.algomentor.dto.CreateMistakeNoteRequest request) {
        if (request == null || request.problemTitle() == null || request.mistakeText() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Problem title and mistake text are required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        long count = noteDocumentRepository.countByUserId(userId);
        if (count >= notesProperties.getMaxDocuments()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Maximum note limit reached (" + notesProperties.getMaxDocuments() + ")");
        }

        String tagsStr = (request.tags() != null && !request.tags().isEmpty())
                ? String.join(", ", request.tags())
                : "General";

        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("# Problem Takeaway & Mistake Log: ").append(request.problemTitle().trim()).append("\n\n");
        contentBuilder.append("- **Tags/Topics**: ").append(tagsStr).append("\n");
        contentBuilder.append("- **Logged At**: ").append(java.time.LocalDate.now()).append("\n\n");
        contentBuilder.append("## What Went Wrong / Challenge Faced:\n");
        contentBuilder.append(request.mistakeText().trim()).append("\n\n");

        if (request.keyTakeaway() != null && !request.keyTakeaway().isBlank()) {
            contentBuilder.append("## Key Invariant / Rule to Remember:\n");
            contentBuilder.append(request.keyTakeaway().trim()).append("\n");
        }

        String markdownContent = contentBuilder.toString();
        byte[] bytes = markdownContent.getBytes(StandardCharsets.UTF_8);
        String contentHash = computeSha256Hex(bytes);

        // Deduplication: if user already logged this exact mistake note
        var existing = noteDocumentRepository.findByUserIdAndContentHash(userId, contentHash);
        if (existing.isPresent()) {
            return toDetailDto(existing.get());
        }

        String displayName = "Mistake Log: " + request.problemTitle().trim();
        if (displayName.length() > 250) {
            displayName = displayName.substring(0, 250);
        }

        NoteDocument saved = saveInitialNote(user, displayName, "text/markdown", bytes.length, contentHash, markdownContent);

        NoteStatus finalStatus;
        try {
            aiServiceClient.ingest(saved.getId(), userId, saved.getDisplayName(), saved.getContent());
            finalStatus = NoteStatus.INDEXED;
            log.info("[RAG] Successfully ingested mistake log into AI vector memory for user={}, docId={}", userId, saved.getId());
        } catch (Exception e) {
            log.warn("[RAG] AI indexing failed for mistake log docId={}: {}. Marking status as FAILED", saved.getId(), e.getMessage());
            finalStatus = NoteStatus.FAILED;
        }

        NoteDocument updated = updateNoteStatus(saved.getId(), finalStatus);
        return toDetailDto(updated);
    }

    public void createAutoStruggleNote(User user, String problemName, Integer rating, List<String> tags, long failedAttempts) {
        String tagsStr = (tags != null && !tags.isEmpty()) ? String.join(", ", tags) : "General";
        String ratingStr = rating != null ? String.valueOf(rating) : "Unrated";

        StringBuilder sb = new StringBuilder();
        sb.append("# Codeforces Struggle Record: ").append(problemName).append("\n\n");
        sb.append("- **Tags/Topics**: ").append(tagsStr).append("\n");
        sb.append("- **Difficulty Rating**: ").append(ratingStr).append("\n");
        sb.append("- **Failed Attempts Count**: ").append(failedAttempts).append(" submissions\n");
        sb.append("- **Status**: Currently Unsolved\n\n");
        sb.append("## Automated Struggle Diagnostic Context:\n");
        sb.append("The student has struggled on this problem across ").append(failedAttempts)
          .append(" unsuccessful attempts on Codeforces, facing repeated test failures. ")
          .append("When mentoring the student on this or related topics (").append(tagsStr)
          .append("), proactively encourage them to question their invariants, edge cases, and time-complexity assumptions.\n");

        String content = sb.toString();
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String hash = computeSha256Hex(bytes);

        // Deduplicate: If already indexed for this user and content, don't recreate
        if (noteDocumentRepository.findByUserIdAndContentHash(user.getId(), hash).isPresent()) {
            return;
        }

        String displayName = "CF Struggle: " + problemName;
        if (displayName.length() > 250) {
            displayName = displayName.substring(0, 250);
        }

        NoteDocument saved = saveInitialNote(user, displayName, "text/markdown", bytes.length, hash, content);
        try {
            aiServiceClient.ingest(saved.getId(), user.getId(), saved.getDisplayName(), saved.getContent());
            updateNoteStatus(saved.getId(), NoteStatus.INDEXED);
            log.info("[RAG] Auto-indexed CF struggle problem '{}' into AI vector memory for user {}", problemName, user.getId());
        } catch (Exception e) {
            updateNoteStatus(saved.getId(), NoteStatus.FAILED);
            log.warn("[RAG] Failed to auto-index struggle problem '{}': {}", problemName, e.getMessage());
        }
    }

    private String sanitizeFilename(String filename) {
        String nameOnly = Path.of(filename).getFileName().toString().trim();
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

package com.algomentor.repository;

import com.algomentor.domain.NoteDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteDocumentRepository extends JpaRepository<NoteDocument, UUID> {

    Optional<NoteDocument> findByUserIdAndContentHash(UUID userId, String contentHash);

    List<NoteDocument> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<NoteDocument> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);
}


package com.algomentor.repository;

import com.algomentor.domain.NoteDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteDocumentRepository extends JpaRepository<NoteDocument, UUID> {

    Optional<NoteDocument> findByUserIdAndContentHash(UUID userId, String contentHash);

    Optional<NoteDocument> findByUserIdAndDisplayName(UUID userId, String displayName);

    List<NoteDocument> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<NoteDocument> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("DELETE FROM NoteDocument n WHERE n.user.id = :userId AND n.displayName LIKE 'CF Struggle:%'")
    void deleteAutoStruggleNotesByUserId(@org.springframework.data.repository.query.Param("userId") UUID userId);
}


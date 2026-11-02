package com.algomentor.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "note_documents",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "content_hash"})
)
@Getter
@Setter
@NoArgsConstructor
public class NoteDocument extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Original filename shown to the user; never used as a filesystem path
    @Column(nullable = false, length = 255)
    private String displayName;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    // SHA-256 hex of the file content — used for deduplication per user
    @Column(nullable = false, length = 64)
    private String contentHash;

    // Full text content stored in the DB (TEXT column)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NoteStatus status;
}


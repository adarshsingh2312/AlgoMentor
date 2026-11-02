package com.algomentor.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "dsa_topics",
    uniqueConstraints = @UniqueConstraint(columnNames = {"section", "title"})
)
@Getter
@Setter
@NoArgsConstructor
public class DsaTopic extends BaseEntity {

    @Column(nullable = false)
    private String section;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DifficultyLevel difficulty;

    // Controls display order within a section
    @Column(nullable = false)
    private int orderIndex;
}


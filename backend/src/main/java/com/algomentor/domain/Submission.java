package com.algomentor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "submissions",
    indexes = {
        @Index(name = "idx_submissions_user_problem", columnList = "user_id, problem_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Submission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(name = "cf_submission_id", nullable = false, unique = true)
    private Long cfSubmissionId;

    @Column(nullable = false)
    private String verdict;

    @Column(nullable = false)
    private String language;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;
}
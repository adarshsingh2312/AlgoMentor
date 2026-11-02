package com.algomentor.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "user_topic_progress",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "topic_id"}),
    indexes = @Index(name = "idx_user_topic_progress_user_id", columnList = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
public class UserTopicProgress extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private DsaTopic topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TopicStatus status;

    @Column
    private LocalDateTime completedAt;
}


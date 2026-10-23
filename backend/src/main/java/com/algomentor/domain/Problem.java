package com.algomentor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
    name = "problems",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_problem_contest_index", columnNames = {"contest_id", "problem_index"})
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Problem extends BaseEntity {

    @Column(name = "contest_id", nullable = false)
    private Integer contestId;

    @Column(name = "problem_index", nullable = false)
    private String problemIndex;

    @Column(nullable = false)
    private String name;

    @Column
    private Integer rating;

    @Column(nullable = false)
    private String url;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "problem_tags",
        joinColumns = @JoinColumn(name = "problem_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();
}

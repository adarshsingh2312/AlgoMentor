package com.algomentor.repository;

import com.algomentor.domain.Problem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProblemRepository extends JpaRepository<Problem, UUID> {

    @Query("SELECT p FROM Problem p LEFT JOIN FETCH p.tags")
    List<Problem> findAllWithTags();

    Optional<Problem> findByContestIdAndProblemIndex(Integer contestId, String problemIndex);

    boolean existsByContestIdAndProblemIndex(Integer contestId, String problemIndex);
}

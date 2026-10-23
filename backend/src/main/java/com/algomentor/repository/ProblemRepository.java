package com.algomentor.repository;

import com.algomentor.domain.Problem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProblemRepository extends JpaRepository<Problem, UUID> {

    Optional<Problem> findByContestIdAndProblemIndex(Integer contestId, String problemIndex);

    boolean existsByContestIdAndProblemIndex(Integer contestId, String problemIndex);
}

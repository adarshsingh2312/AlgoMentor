package com.algomentor.repository;

import com.algomentor.domain.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    Optional<Submission> findByCfSubmissionId(Long cfSubmissionId);

    List<Submission> findByUserId(UUID userId);

    List<Submission> findByProblemId(UUID problemId);
}

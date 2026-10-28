package com.algomentor.repository;

import com.algomentor.domain.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.algomentor.domain.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    @Query("SELECT s.cfSubmissionId FROM Submission s WHERE s.user = :user AND s.cfSubmissionId IN :ids")
    Set<Long> findExistingCfIds(@Param("user") User user, @Param("ids") Collection<Long> ids);

    Optional<Submission> findByCfSubmissionId(Long cfSubmissionId);

    List<Submission> findByUserId(UUID userId);

    List<Submission> findByProblemId(UUID problemId);
}

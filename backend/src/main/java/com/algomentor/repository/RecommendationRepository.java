package com.algomentor.repository;

import com.algomentor.domain.Recommendation;
import com.algomentor.domain.RecommendationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {

    List<Recommendation> findByUserId(UUID userId);

    List<Recommendation> findByUserIdAndStatus(UUID userId, RecommendationStatus status);

    List<Recommendation> findByUserIdAndRecommendedDate(UUID userId, LocalDate recommendedDate);

    Optional<Recommendation> findByIdAndUserId(UUID id, UUID userId);

    @Query("SELECT r.problem.id FROM Recommendation r WHERE r.user.id = :userId AND r.recommendedDate >= :sinceDate")
    Set<UUID> findRecentlyRecommendedProblemIds(@Param("userId") UUID userId, @Param("sinceDate") LocalDate sinceDate);
}

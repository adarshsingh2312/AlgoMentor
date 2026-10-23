package com.algomentor.repository;

import com.algomentor.domain.Recommendation;
import com.algomentor.domain.RecommendationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {

    List<Recommendation> findByUserId(UUID userId);

    List<Recommendation> findByUserIdAndStatus(UUID userId, RecommendationStatus status);
}

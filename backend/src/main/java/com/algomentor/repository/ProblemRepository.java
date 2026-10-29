package com.algomentor.repository;

import com.algomentor.domain.Problem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProblemRepository extends JpaRepository<Problem, UUID> {

    @Query("SELECT p FROM Problem p LEFT JOIN FETCH p.tags")
    List<Problem> findAllWithTags();

    @Query("SELECT DISTINCT p FROM Problem p LEFT JOIN FETCH p.tags WHERE p.id IN :ids")
    List<Problem> findByIdInWithTags(@Param("ids") Collection<UUID> ids);

    Optional<Problem> findByContestIdAndProblemIndex(Integer contestId, String problemIndex);

    boolean existsByContestIdAndProblemIndex(Integer contestId, String problemIndex);

    @Query(value = """
            SELECT DISTINCT p.*
            FROM problems p
            JOIN problem_tags pt ON pt.problem_id = p.id
            JOIN tags t ON t.id = pt.tag_id
            WHERE p.rating BETWEEN :minRating AND :maxRating
              AND t.name IN (:tagNames)
              AND (CAST(:hasExcludes AS boolean) = false OR p.id NOT IN (:excludedIds))
            LIMIT :limit
            """, nativeQuery = true)
    List<Problem> findWeakTagCandidates(
            @Param("minRating") int minRating,
            @Param("maxRating") int maxRating,
            @Param("tagNames") Collection<String> tagNames,
            @Param("hasExcludes") boolean hasExcludes,
            @Param("excludedIds") Collection<UUID> excludedIds,
            @Param("limit") int limit);

    @Query(value = """
            SELECT p.*
            FROM problems p
            WHERE p.rating BETWEEN :minRating AND :maxRating
              AND (CAST(:hasExcludes AS boolean) = false OR p.id NOT IN (:excludedIds))
            LIMIT :limit
            """, nativeQuery = true)
    List<Problem> findGeneralCandidates(
            @Param("minRating") int minRating,
            @Param("maxRating") int maxRating,
            @Param("hasExcludes") boolean hasExcludes,
            @Param("excludedIds") Collection<UUID> excludedIds,
            @Param("limit") int limit);
}

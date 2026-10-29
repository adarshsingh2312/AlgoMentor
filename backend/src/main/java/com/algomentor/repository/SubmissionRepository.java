package com.algomentor.repository;

import com.algomentor.domain.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.algomentor.domain.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.algomentor.dto.DailySolveProjection;
import com.algomentor.dto.RatingBucketProjection;
import com.algomentor.dto.TagStatProjection;

import java.time.LocalDate;
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

    boolean existsByUserIdAndProblemIdAndVerdict(UUID userId, UUID problemId, String verdict);

    @Query(value = """
            SELECT 
                t.name AS tag,
                COUNT(DISTINCT s.problem_id) AS attempted,
                COUNT(DISTINCT CASE WHEN s.verdict = 'OK' THEN s.problem_id END) AS solved
            FROM submissions s
            JOIN problem_tags pt ON pt.problem_id = s.problem_id
            JOIN tags t ON t.id = pt.tag_id
            WHERE s.user_id = :userId
            GROUP BY t.name
            ORDER BY attempted DESC
            """, nativeQuery = true)
    List<TagStatProjection> findTagStatsByUserId(@Param("userId") UUID userId);

    @Query(value = """
            SELECT 
                (p.rating / 100) * 100 AS bucket,
                COUNT(DISTINCT s.problem_id) AS attempted,
                COUNT(DISTINCT CASE WHEN s.verdict = 'OK' THEN s.problem_id END) AS solved
            FROM submissions s
            JOIN problems p ON p.id = s.problem_id
            WHERE s.user_id = :userId AND p.rating IS NOT NULL
            GROUP BY (p.rating / 100) * 100
            ORDER BY bucket ASC
            """, nativeQuery = true)
    List<RatingBucketProjection> findRatingBucketStatsByUserId(@Param("userId") UUID userId);

    @Query(value = """
            SELECT 
                first_ok.solve_date AS solveDate,
                COUNT(first_ok.problem_id) AS solvedCount
            FROM (
                SELECT 
                    s.problem_id,
                    CAST(MIN(s.submitted_at) AS date) AS solve_date
                FROM submissions s
                WHERE s.user_id = :userId AND s.verdict = 'OK'
                GROUP BY s.problem_id
            ) first_ok
            WHERE first_ok.solve_date >= :startDate
            GROUP BY first_ok.solve_date
            ORDER BY first_ok.solve_date ASC
            """, nativeQuery = true)
    List<DailySolveProjection> findDailySolvesByUserIdSince(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate);

    @Query(value = """
            SELECT p.rating
            FROM (
                SELECT s.problem_id, MAX(s.submitted_at) AS max_time
                FROM submissions s
                JOIN problems p ON p.id = s.problem_id
                WHERE s.user_id = :userId AND s.verdict = 'OK' AND p.rating IS NOT NULL
                GROUP BY s.problem_id
                ORDER BY max_time DESC
                LIMIT :limit
            ) latest
            JOIN problems p ON p.id = latest.problem_id
            """, nativeQuery = true)
    List<Integer> findLastSolvedProblemRatings(@Param("userId") UUID userId, @Param("limit") int limit);

    @Query(value = """
            SELECT DISTINCT s.problem_id
            FROM submissions s
            WHERE s.user_id = :userId AND s.verdict = 'OK'
            """, nativeQuery = true)
    Set<UUID> findSolvedProblemIdsByUserId(@Param("userId") UUID userId);
}

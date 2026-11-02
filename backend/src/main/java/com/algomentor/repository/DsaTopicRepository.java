package com.algomentor.repository;

import com.algomentor.domain.DsaTopic;
import com.algomentor.domain.UserTopicProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DsaTopicRepository extends JpaRepository<DsaTopic, UUID> {

    List<DsaTopic> findAllByOrderByOrderIndexAsc();

    Optional<DsaTopic> findBySectionAndTitle(String section, String title);

    /**
     * Single query: all topics LEFT JOIN the given user's progress rows.
     * Topics with no progress row appear with p == null (status treated as TODO by the service).
     * The ON clause filters progress rows to this user only, so other users' rows are ignored.
     */
    @Query("""
            SELECT t, p
            FROM DsaTopic t
            LEFT JOIN UserTopicProgress p ON p.topic = t AND p.user.id = :userId
            ORDER BY t.orderIndex ASC
            """)
    List<Object[]> findAllWithProgressForUser(@Param("userId") UUID userId);
}


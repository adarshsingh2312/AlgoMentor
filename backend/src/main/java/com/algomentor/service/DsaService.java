package com.algomentor.service;

import com.algomentor.domain.DsaTopic;
import com.algomentor.domain.TopicStatus;
import com.algomentor.domain.User;
import com.algomentor.domain.UserTopicProgress;
import com.algomentor.dto.DsaRoadmapDto;
import com.algomentor.dto.DsaSectionDto;
import com.algomentor.dto.DsaTopicProgressDto;
import com.algomentor.repository.DsaTopicRepository;
import com.algomentor.repository.UserRepository;
import com.algomentor.repository.UserTopicProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DsaService {

    private final DsaTopicRepository dsaTopicRepository;
    private final UserTopicProgressRepository progressRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DsaRoadmapDto getRoadmap(UUID userId) {
        List<Object[]> rows = dsaTopicRepository.findAllWithProgressForUser(userId);

        // Group rows by section, preserving orderIndex order (query already sorted)
        Map<String, List<DsaTopicProgressDto>> bySection = new LinkedHashMap<>();
        for (Object[] row : rows) {
            DsaTopic topic = (DsaTopic) row[0];
            UserTopicProgress progress = (UserTopicProgress) row[1]; // null if no row exists

            TopicStatus status = (progress != null) ? progress.getStatus() : TopicStatus.TODO;

            bySection
                .computeIfAbsent(topic.getSection(), k -> new ArrayList<>())
                .add(new DsaTopicProgressDto(
                        topic.getId(),
                        topic.getTitle(),
                        topic.getUrl(),
                        topic.getDifficulty(),
                        status));
        }

        int totalDone = 0;
        int totalTopics = 0;
        List<DsaSectionDto> sections = new ArrayList<>();

        for (Map.Entry<String, List<DsaTopicProgressDto>> entry : bySection.entrySet()) {
            List<DsaTopicProgressDto> items = entry.getValue();
            int done = (int) items.stream().filter(i -> i.status() == TopicStatus.DONE).count();
            sections.add(new DsaSectionDto(entry.getKey(), items, done, items.size()));
            totalDone += done;
            totalTopics += items.size();
        }

        return new DsaRoadmapDto(sections, totalDone, totalTopics);
    }

    @Transactional
    public void updateProgress(UUID userId, UUID topicId, TopicStatus newStatus) {
        DsaTopic topic = dsaTopicRepository.findById(topicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        UserTopicProgress progress = progressRepository
                .findByUserIdAndTopicId(userId, topicId)
                .orElseGet(() -> {
                    UserTopicProgress p = new UserTopicProgress();
                    p.setUser(user);
                    p.setTopic(topic);
                    return p;
                });

        progress.setStatus(newStatus);
        progress.setCompletedAt(newStatus == TopicStatus.DONE ? LocalDateTime.now(clock) : null);
        progressRepository.save(progress);
    }
}


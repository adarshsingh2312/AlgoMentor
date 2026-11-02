package com.algomentor.service;

import com.algomentor.domain.DifficultyLevel;
import com.algomentor.domain.DsaTopic;
import com.algomentor.domain.TopicStatus;
import com.algomentor.domain.User;
import com.algomentor.domain.UserTopicProgress;
import com.algomentor.dto.DsaRoadmapDto;
import com.algomentor.repository.DsaTopicRepository;
import com.algomentor.repository.UserRepository;
import com.algomentor.repository.UserTopicProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DsaServiceTest {

    @Mock private DsaTopicRepository dsaTopicRepository;
    @Mock private UserTopicProgressRepository progressRepository;
    @Mock private UserRepository userRepository;
    @Spy private Clock clock = Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneId.of("UTC"));

    @InjectMocks
    private DsaService dsaService;

    private User testUser;
    private DsaTopic testTopic;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());

        testTopic = new DsaTopic();
        testTopic.setId(UUID.randomUUID());
        testTopic.setSection("Arrays");
        testTopic.setTitle("Two Sum");
        testTopic.setUrl("https://leetcode.com/problems/two-sum/");
        testTopic.setDifficulty(DifficultyLevel.EASY);
        testTopic.setOrderIndex(1);
    }

    @Test
    @DisplayName("Updating progress when no previous row exists creates a new progress record")
    void updateProgressCreatesNewRecord() {
        when(dsaTopicRepository.findById(testTopic.getId())).thenReturn(Optional.of(testTopic));
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(progressRepository.findByUserIdAndTopicId(testUser.getId(), testTopic.getId()))
                .thenReturn(Optional.empty());

        dsaService.updateProgress(testUser.getId(), testTopic.getId(), TopicStatus.DONE);

        ArgumentCaptor<UserTopicProgress> captor = ArgumentCaptor.forClass(UserTopicProgress.class);
        verify(progressRepository).save(captor.capture());

        UserTopicProgress saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(testUser);
        assertThat(saved.getTopic()).isEqualTo(testTopic);
        assertThat(saved.getStatus()).isEqualTo(TopicStatus.DONE);
        assertThat(saved.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("Updating progress when row already exists modifies the record idempotently")
    void updateProgressUpdatesExistingRecord() {
        UserTopicProgress existing = new UserTopicProgress();
        existing.setUser(testUser);
        existing.setTopic(testTopic);
        existing.setStatus(TopicStatus.IN_PROGRESS);

        when(dsaTopicRepository.findById(testTopic.getId())).thenReturn(Optional.of(testTopic));
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(progressRepository.findByUserIdAndTopicId(testUser.getId(), testTopic.getId()))
                .thenReturn(Optional.of(existing));

        dsaService.updateProgress(testUser.getId(), testTopic.getId(), TopicStatus.DONE);

        verify(progressRepository).save(existing);
        assertThat(existing.getStatus()).isEqualTo(TopicStatus.DONE);
        assertThat(existing.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("Setting status to IN_PROGRESS clears completedAt")
    void settingStatusToInProgressClearsCompletedAt() {
        UserTopicProgress existing = new UserTopicProgress();
        existing.setUser(testUser);
        existing.setTopic(testTopic);
        existing.setStatus(TopicStatus.DONE);
        existing.setCompletedAt(java.time.LocalDateTime.now());

        when(dsaTopicRepository.findById(testTopic.getId())).thenReturn(Optional.of(testTopic));
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(progressRepository.findByUserIdAndTopicId(testUser.getId(), testTopic.getId()))
                .thenReturn(Optional.of(existing));

        dsaService.updateProgress(testUser.getId(), testTopic.getId(), TopicStatus.IN_PROGRESS);

        assertThat(existing.getStatus()).isEqualTo(TopicStatus.IN_PROGRESS);
        assertThat(existing.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("Updating progress for nonexistent topic throws 404")
    void updateProgressNonexistentTopicThrows404() {
        UUID unknownId = UUID.randomUUID();
        when(dsaTopicRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dsaService.updateProgress(testUser.getId(), unknownId, TopicStatus.DONE))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode() == HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("getRoadmap computes section totals and defaults missing rows to TODO")
    void getRoadmapComputesTotalsAndDefaultsToTodo() {
        DsaTopic topic2 = new DsaTopic();
        topic2.setId(UUID.randomUUID());
        topic2.setSection("Arrays");
        topic2.setTitle("Second Problem");
        topic2.setUrl("https://leetcode.com/problems/second");
        topic2.setDifficulty(DifficultyLevel.MEDIUM);
        topic2.setOrderIndex(2);

        UserTopicProgress p1 = new UserTopicProgress();
        p1.setUser(testUser);
        p1.setTopic(testTopic);
        p1.setStatus(TopicStatus.DONE);

        List<Object[]> rows = List.of(
                new Object[]{testTopic, p1},
                new Object[]{topic2, null}
        );

        when(dsaTopicRepository.findAllWithProgressForUser(testUser.getId())).thenReturn(rows);

        DsaRoadmapDto roadmap = dsaService.getRoadmap(testUser.getId());

        assertThat(roadmap.totalTopics()).isEqualTo(2);
        assertThat(roadmap.totalDone()).isEqualTo(1);
        assertThat(roadmap.sections()).hasSize(1);
        assertThat(roadmap.sections().get(0).section()).isEqualTo("Arrays");
        assertThat(roadmap.sections().get(0).done()).isEqualTo(1);
        assertThat(roadmap.sections().get(0).total()).isEqualTo(2);
        assertThat(roadmap.sections().get(0).items().get(1).status()).isEqualTo(TopicStatus.TODO);
    }
}


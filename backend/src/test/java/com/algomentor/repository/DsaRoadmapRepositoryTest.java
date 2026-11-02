package com.algomentor.repository;

import com.algomentor.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DsaRoadmapRepositoryTest {

    @Autowired private DsaTopicRepository topicRepository;
    @Autowired private UserTopicProgressRepository progressRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TestEntityManager entityManager;

    @Test
    @DisplayName("Roadmap query returns topics with null progress (TODO) when no progress row exists")
    void roadmapQueryReturnsNullProgressWhenNoRowExists() {
        User user = createUser("alice@example.com", "Alice");
        DsaTopic topic1 = createTopic("Arrays", "Two Sum", 1);
        DsaTopic topic2 = createTopic("Arrays", "Best Time to Buy Stock", 2);

        entityManager.flush();
        entityManager.clear();

        List<Object[]> rows = topicRepository.findAllWithProgressForUser(user.getId());

        assertThat(rows).hasSize(2);
        for (Object[] row : rows) {
            assertThat(row[0]).isInstanceOf(DsaTopic.class);
            assertThat(row[1]).isNull(); // Null progress means TODO
        }
    }

    @Test
    @DisplayName("Roadmap query returns populated progress only for the querying user, isolating other users' progress")
    void roadmapQueryIsolatesProgressPerUser() {
        User alice = createUser("alice@example.com", "Alice");
        User bob = createUser("bob@example.com", "Bob");
        DsaTopic topic = createTopic("Trees", "Invert Binary Tree", 1);

        // Bob marks as DONE
        UserTopicProgress bobProgress = new UserTopicProgress();
        bobProgress.setUser(bob);
        bobProgress.setTopic(topic);
        bobProgress.setStatus(TopicStatus.DONE);
        bobProgress.setCompletedAt(LocalDateTime.now());
        progressRepository.save(bobProgress);

        entityManager.flush();
        entityManager.clear();

        // Alice queries -> should see null progress
        List<Object[]> aliceRows = topicRepository.findAllWithProgressForUser(alice.getId());
        assertThat(aliceRows).hasSize(1);
        assertThat(aliceRows.get(0)[1]).isNull();

        // Bob queries -> should see DONE
        List<Object[]> bobRows = topicRepository.findAllWithProgressForUser(bob.getId());
        assertThat(bobRows).hasSize(1);
        assertThat(bobRows.get(0)[1]).isNotNull();
        UserTopicProgress fetched = (UserTopicProgress) bobRows.get(0)[1];
        assertThat(fetched.getStatus()).isEqualTo(TopicStatus.DONE);
    }

    private User createUser(String email, String name) {
        User user = new User();
        user.setEmail(email);
        user.setName(name);
        user.setRole(Role.USER);
        user.setAuthProvider("LOCAL");
        return userRepository.save(user);
    }

    private DsaTopic createTopic(String section, String title, int orderIndex) {
        DsaTopic topic = new DsaTopic();
        topic.setSection(section);
        topic.setTitle(title);
        topic.setUrl("https://leetcode.com/problems/example");
        topic.setDifficulty(DifficultyLevel.EASY);
        topic.setOrderIndex(orderIndex);
        return topicRepository.save(topic);
    }
}


package com.algomentor.repository;

import com.algomentor.domain.Problem;
import com.algomentor.domain.Role;
import com.algomentor.domain.Submission;
import com.algomentor.domain.Tag;
import com.algomentor.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("Should save and read back a User, a Problem with two Tags, and a Submission")
    void shouldSaveAndReadBackUserProblemTagsAndSubmission() {

        // 1. Create and save a User
        User user = new User();
        user.setEmail("tourist@example.com");
        user.setName("Gennady Korotkevich");
        user.setRole(Role.USER);
        user.setAuthProvider("LOCAL");
        user.setCfHandle("tourist");
        User savedUser = userRepository.save(user);

        // 2. Create and save two Tags
        Tag tagDp = new Tag("dp");
        Tag tagGraphs = new Tag("graphs");
        Tag tagGreedy = new Tag("greedy");
        tagRepository.save(tagDp);
        tagRepository.save(tagGraphs);
        tagRepository.save(tagGreedy);

        // 3. Create and save a Problem with the two Tags
        Problem problem = new Problem();
        problem.setContestId(1500);
        problem.setProblemIndex("A");
        problem.setName("Going Home");
        problem.setRating(1800);
        problem.setUrl("https://codeforces.com/contest/1500/problem/A");
        problem.setTags(Set.of(tagDp, tagGraphs,tagGreedy));
        Problem savedProblem = problemRepository.save(problem);

        // 4. Create and save a Submission linking the User and Problem
        Submission submission = new Submission();
        submission.setUser(savedUser);
        submission.setProblem(savedProblem);
        submission.setCfSubmissionId(999888777L);
        submission.setVerdict("OK");
        submission.setLanguage("GNU C++20");
        submission.setSubmittedAt(LocalDateTime.now());
        submissionRepository.save(submission);

        // Flush changes and clear the first-level Hibernate cache
        // to force queries to go directly to PostgreSQL
        entityManager.flush();
        entityManager.clear();

        // 5. Read back and verify User
        Optional<User> fetchedUserOpt = userRepository.findById(savedUser.getId());
        assertThat(fetchedUserOpt).isPresent();
        User fetchedUser = fetchedUserOpt.get();
        assertThat(fetchedUser.getEmail()).isEqualTo("tourist@example.com");
        assertThat(fetchedUser.getName()).isEqualTo("Gennady Korotkevich");
        assertThat(fetchedUser.getCfHandle()).isEqualTo("tourist");
        assertThat(fetchedUser.getRole()).isEqualTo(Role.USER);
        assertThat(fetchedUser.getCreatedAt()).isNotNull();

        // 6. Read back and verify Problem and Tags
        Optional<Problem> fetchedProblemOpt = problemRepository.findById(savedProblem.getId());
        assertThat(fetchedProblemOpt).isPresent();
        Problem fetchedProblem = fetchedProblemOpt.get();
        assertThat(fetchedProblem.getContestId()).isEqualTo(1500);
        assertThat(fetchedProblem.getProblemIndex()).isEqualTo("A");
        assertThat(fetchedProblem.getName()).isEqualTo("Going Home");
        assertThat(fetchedProblem.getTags())
                .hasSize(2)
                .extracting(Tag::getName)
                .containsExactlyInAnyOrder("dp", "graphs");

        // 7. Read back and verify Submission
        Optional<Submission> fetchedSubmissionOpt = submissionRepository.findByCfSubmissionId(999888777L);
        assertThat(fetchedSubmissionOpt).isPresent();
        Submission fetchedSubmission = fetchedSubmissionOpt.get();
        assertThat(fetchedSubmission.getVerdict()).isEqualTo("OK");
        assertThat(fetchedSubmission.getLanguage()).isEqualTo("GNU C++17");
        assertThat(fetchedSubmission.getUser().getId()).isEqualTo(savedUser.getId());
        assertThat(fetchedSubmission.getProblem().getId()).isEqualTo(savedProblem.getId());
    }
}
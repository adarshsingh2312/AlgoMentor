package com.algomentor.service;

import com.algomentor.dto.SyncResult;
import com.algomentor.repository.ProblemRepository;
import com.algomentor.repository.TagRepository;
import com.algomentor.service.CodeforcesService.CfProblem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({UpsertService.class, ProblemBatchBean.class, SubmissionBatchBean.class})
class ProblemsetIdempotencyTest {

    @Autowired
    private UpsertService upsertService;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private TagRepository tagRepository;

    @Test
    @DisplayName("Should be idempotent when upserting identical problemset twice")
    void shouldBeIdempotentWhenUpsertingProblemsetTwice() {
        List<CfProblem> initialPayload = List.of(
                new CfProblem(1, "A", "Theatre Square", 1000, List.of("math")),
                new CfProblem(1, "B", "Spreadsheets", 1600, List.of("implementation", "math")),
                new CfProblem(4, "A", "Watermelon", 800, List.of("brute force", "math"))
        );

        // First run: all 3 problems should be inserted
        SyncResult res1 = upsertService.upsertProblems(initialPayload);
        assertThat(res1.problemsInserted()).isEqualTo(3);
        assertThat(res1.problemsUpdated()).isEqualTo(0);

        long problemCountAfterFirstRun = problemRepository.count();
        long tagCountAfterFirstRun = tagRepository.count();
        assertThat(problemCountAfterFirstRun).isGreaterThanOrEqualTo(3);

        // Second run with identical payload: 0 inserted, 3 updated, counts unchanged
        SyncResult res2 = upsertService.upsertProblems(initialPayload);
        assertThat(res2.problemsInserted()).isEqualTo(0);
        assertThat(res2.problemsUpdated()).isEqualTo(3);
        assertThat(problemRepository.count()).isEqualTo(problemCountAfterFirstRun);
        assertThat(tagRepository.count()).isEqualTo(tagCountAfterFirstRun);
    }
}

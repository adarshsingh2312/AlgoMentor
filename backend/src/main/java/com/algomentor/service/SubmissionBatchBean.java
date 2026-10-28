package com.algomentor.service;

import com.algomentor.domain.Problem;
import com.algomentor.domain.Submission;
import com.algomentor.domain.Tag;
import com.algomentor.domain.User;
import com.algomentor.dto.SyncResult;
import com.algomentor.repository.SubmissionRepository;
import com.algomentor.service.CodeforcesService.CfSubmission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Executes a single submission batch in its own transaction.
 * Must be a separate Spring bean so that the call from UpsertService goes
 * through the Spring transaction proxy.
 */
@Service
public class SubmissionBatchBean {

    private final SubmissionRepository submissionRepository;
    private final ProblemBatchBean problemBatchBean;

    public SubmissionBatchBean(SubmissionRepository submissionRepository,
                               ProblemBatchBean problemBatchBean) {
        this.submissionRepository = submissionRepository;
        this.problemBatchBean = problemBatchBean;
    }

    @Transactional
    public SyncResult processBatch(User user,
                                   List<CfSubmission> batch,
                                   Map<String, Tag> tagsByName,
                                   Map<String, Problem> problemsByKey) {
        List<Long> candidateIds = batch.stream()
                .filter(s -> s.id() != null && s.verdict() != null && s.problem() != null)
                .map(CfSubmission::id)
                .toList();

        if (candidateIds.isEmpty()) {
            return SyncResult.EMPTY;
        }

        Set<Long> existingIds = new HashSet<>(submissionRepository.findExistingCfIds(user, candidateIds));
        int skipped = (int) candidateIds.stream().filter(existingIds::contains).count();
        int inserted = 0;
        int problemsInserted = 0;
        int problemsUpdated = 0;

        List<Submission> toSave = new ArrayList<>();

        for (CfSubmission sub : batch) {
            // Skip if missing critical info or still in judgment
            if (sub.id() == null || sub.verdict() == null || sub.problem() == null) {
                continue;
            }
            if (sub.problem().contestId() == null || sub.problem().index() == null) {
                continue;
            }
            if (existingIds.contains(sub.id())) {
                continue;
            }

            String problemKey = sub.problem().contestId() + ":" + sub.problem().index();
            Problem problem = problemsByKey.get(problemKey);

            // If the submission's problem is not yet in our DB, upsert it first
            if (problem == null) {
                SyncResult pRes = problemBatchBean.processBatch(
                        List.of(sub.problem()), tagsByName, problemsByKey);
                problemsInserted += pRes.problemsInserted();
                problemsUpdated += pRes.problemsUpdated();
                problem = problemsByKey.get(problemKey);
            }

            Submission entity = new Submission();
            entity.setUser(user);
            entity.setProblem(problem);
            entity.setCfSubmissionId(sub.id());
            entity.setVerdict(sub.verdict());
            entity.setLanguage(sub.programmingLanguage() != null ? sub.programmingLanguage() : "UNKNOWN");

            LocalDateTime submittedAt = Instant.ofEpochSecond(sub.creationTimeSeconds())
                    .atZone(ZoneOffset.UTC)
                    .toLocalDateTime();
            entity.setSubmittedAt(submittedAt);

            toSave.add(entity);
            existingIds.add(sub.id());
            inserted++;
        }

        if (!toSave.isEmpty()) {
            submissionRepository.saveAll(toSave);
        }

        return new SyncResult(problemsInserted, problemsUpdated, inserted, skipped);
    }
}

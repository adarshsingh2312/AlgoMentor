package com.algomentor.service;

import com.algomentor.domain.Problem;
import com.algomentor.domain.Tag;
import com.algomentor.domain.User;
import com.algomentor.dto.SyncResult;
import com.algomentor.repository.ProblemRepository;
import com.algomentor.repository.TagRepository;
import com.algomentor.service.CodeforcesService.CfProblem;
import com.algomentor.service.CodeforcesService.CfSubmission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
public class UpsertService {

    private static final Logger log = LoggerFactory.getLogger(UpsertService.class);
    private static final int BATCH_SIZE = 500;

    private final ProblemRepository problemRepository;
    private final TagRepository tagRepository;
    private final ProblemBatchBean problemBatchBean;
    private final SubmissionBatchBean submissionBatchBean;

    public UpsertService(ProblemRepository problemRepository,
                         TagRepository tagRepository,
                         ProblemBatchBean problemBatchBean,
                         SubmissionBatchBean submissionBatchBean) {
        this.problemRepository = problemRepository;
        this.tagRepository = tagRepository;
        this.problemBatchBean = problemBatchBean;
        this.submissionBatchBean = submissionBatchBean;
    }

    public SyncResult upsertProblems(List<CfProblem> problems) {
        if (problems == null || problems.isEmpty()) {
            return SyncResult.EMPTY;
        }

        Map<String, Tag> tagsByName = loadTagsMap();
        Map<String, Problem> problemsByKey = loadProblemsMap();

        SyncResult total = SyncResult.EMPTY;
        for (int i = 0; i < problems.size(); i += BATCH_SIZE) {
            List<CfProblem> chunk = problems.subList(i, Math.min(i + BATCH_SIZE, problems.size()));
            SyncResult chunkRes = problemBatchBean.processBatch(chunk, tagsByName, problemsByKey);
            total = total.add(chunkRes);
        }

        log.info("[Sync] Problems upsert complete: {} inserted, {} updated",
                total.problemsInserted(), total.problemsUpdated());
        return total;
    }

    public SyncResult upsertSubmissions(User user, List<CfSubmission> submissions) {
        if (submissions == null || submissions.isEmpty()) {
            return SyncResult.EMPTY;
        }

        Map<String, Tag> tagsByName = loadTagsMap();
        Map<String, Problem> problemsByKey = loadProblemsMap();

        SyncResult total = SyncResult.EMPTY;
        for (int i = 0; i < submissions.size(); i += BATCH_SIZE) {
            List<CfSubmission> chunk = submissions.subList(i, Math.min(i + BATCH_SIZE, submissions.size()));
            SyncResult chunkRes = submissionBatchBean.processBatch(user, chunk, tagsByName, problemsByKey);
            total = total.add(chunkRes);
        }

        log.info("[Sync] Submissions upsert complete for user {}: {} inserted, {} skipped",
                user.getId(), total.submissionsInserted(), total.submissionsSkipped());
        return total;
    }

    private Map<String, Tag> loadTagsMap() {
        return tagRepository.findAll().stream()
                .collect(Collectors.toMap(Tag::getName, t -> t, (a, b) -> a, HashMap::new));
    }

    private Map<String, Problem> loadProblemsMap() {
        return problemRepository.findAllWithTags().stream()
                .collect(Collectors.toMap(
                        p -> p.getContestId() + ":" + p.getProblemIndex(),
                        p -> p,
                        (a, b) -> a,
                        HashMap::new));
    }
}

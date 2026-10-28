package com.algomentor.service;

import com.algomentor.domain.Problem;
import com.algomentor.domain.Tag;
import com.algomentor.dto.SyncResult;
import com.algomentor.repository.ProblemRepository;
import com.algomentor.repository.TagRepository;
import com.algomentor.service.CodeforcesService.CfProblem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Executes a single problem batch in its own transaction.
 * Must be a separate Spring bean so that the call from UpsertService goes
 * through the Spring transaction proxy.
 */
@Service
public class ProblemBatchBean {

    private final ProblemRepository problemRepository;
    private final TagRepository tagRepository;

    public ProblemBatchBean(ProblemRepository problemRepository, TagRepository tagRepository) {
        this.problemRepository = problemRepository;
        this.tagRepository = tagRepository;
    }

    @Transactional
    public SyncResult processBatch(List<CfProblem> batch,
                                   Map<String, Tag> tagsByName,
                                   Map<String, Problem> problemsByKey) {
        int inserted = 0;
        int updated = 0;
        Map<String, Problem> toSaveByKey = new LinkedHashMap<>();

        for (CfProblem p : batch) {
            if (p.contestId() == null || p.index() == null) {
                continue;
            }

            Set<Tag> resolvedTags = new HashSet<>();
            if (p.tags() != null) {
                for (String tagName : p.tags()) {
                    Tag tag = tagsByName.get(tagName);
                    if (tag == null) {
                        tag = tagRepository.save(new Tag(tagName));
                        tagsByName.put(tagName, tag);
                    }
                    resolvedTags.add(tag);
                }
            }

            String key = p.contestId() + ":" + p.index();
            String url = "https://codeforces.com/problemset/problem/" + p.contestId() + "/" + p.index();

            Problem problem = problemsByKey.get(key);
            boolean isNew = (problem == null);

            if (isNew) {
                problem = new Problem();
                problem.setContestId(p.contestId());
                problem.setProblemIndex(p.index());
                problemsByKey.put(key, problem);
                inserted++;
            } else {
                if (!toSaveByKey.containsKey(key)) {
                    updated++;
                }
            }

            problem.setName(p.name());
            problem.setRating(p.rating());
            problem.setUrl(url);
            problem.setTags(resolvedTags);

            toSaveByKey.put(key, problem);
        }

        problemRepository.saveAll(toSaveByKey.values());
        return new SyncResult(inserted, updated, 0, 0);
    }
}

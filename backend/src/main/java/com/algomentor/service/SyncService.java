package com.algomentor.service;

import com.algomentor.domain.User;
import com.algomentor.dto.SyncResult;
import com.algomentor.service.CodeforcesService.CfProblem;
import com.algomentor.service.CodeforcesService.CfSubmission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);
    private static final int SUBMISSIONS_PAGE_SIZE = 500;

    private final CodeforcesService codeforcesService;
    private final UpsertService upsertService;

    public SyncService(CodeforcesService codeforcesService, UpsertService upsertService) {
        this.codeforcesService = codeforcesService;
        this.upsertService = upsertService;
    }

    /**
     * Fetches the entire problemset from Codeforces and performs idempotent upsert.
     */
    public SyncResult syncProblemset() {
        log.info("[Sync] Starting Codeforces problemset sync");
        List<CfProblem> problems = codeforcesService.fetchProblemset();
        SyncResult result = upsertService.upsertProblems(problems);
        log.info("[Sync] Problemset sync completed: {} inserted, {} updated",
                result.problemsInserted(), result.problemsUpdated());
        return result;
    }

    /**
     * Fetches user submissions page by page (newest-first) and performs idempotent upsert.
     * Stops early when a page produces zero new inserts (stop-at-known optimization).
     *
     * @throws IllegalStateException if user has no CF handle linked
     */
    public SyncResult syncUserSubmissions(User user) {
        if (user.getCfHandle() == null || user.getCfHandle().isBlank()) {
            throw new IllegalStateException("User does not have a Codeforces handle linked");
        }

        String handle = user.getCfHandle();
        log.info("[Sync] Starting submission sync for user {} (handle: {})", user.getId(), handle);

        int from = 1;
        SyncResult total = SyncResult.EMPTY;

        while (true) {
            List<CfSubmission> page = codeforcesService.fetchSubmissions(handle, from, SUBMISSIONS_PAGE_SIZE);
            if (page == null || page.isEmpty()) {
                break;
            }

            SyncResult pageRes = upsertService.upsertSubmissions(user, page);
            total = total.add(pageRes);

            // Stop-at-known: if no new submissions were inserted in this page,
            // all older submissions were already synced on previous runs.
            if (pageRes.submissionsInserted() == 0) {
                log.info("[Sync] Stop-at-known triggered for handle {} at offset {}", handle, from);
                break;
            }

            if (page.size() < SUBMISSIONS_PAGE_SIZE) {
                break;
            }

            from += SUBMISSIONS_PAGE_SIZE;
        }

        log.info("[Sync] Submission sync completed for user {} (handle: {}): {} inserted, {} skipped",
                user.getId(), handle, total.submissionsInserted(), total.submissionsSkipped());
        return total;
    }
}

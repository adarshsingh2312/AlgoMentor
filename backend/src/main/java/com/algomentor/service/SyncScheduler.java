package com.algomentor.service;

import com.algomentor.config.SyncProperties;
import com.algomentor.domain.User;
import com.algomentor.dto.SyncResult;
import com.algomentor.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

    private final SyncProperties props;
    private final SyncService syncService;
    private final UserRepository userRepository;

    public SyncScheduler(SyncProperties props, SyncService syncService, UserRepository userRepository) {
        this.props = props;
        this.syncService = syncService;
        this.userRepository = userRepository;
    }

    @Scheduled(cron = "${app.sync.problemset-cron}")
    public void scheduledProblemsetSync() {
        if (!props.isEnabled()) {
            log.debug("[Scheduler] Sync is disabled; skipping problemset sync");
            return;
        }

        long start = System.currentTimeMillis();
        log.info("[Scheduler] Starting scheduled problemset sync");
        try {
            SyncResult result = syncService.syncProblemset();
            long duration = System.currentTimeMillis() - start;
            log.info("[Scheduler] Scheduled problemset sync completed in {}ms: {} inserted, {} updated",
                    duration, result.problemsInserted(), result.problemsUpdated());
        } catch (Exception e) {
            log.error("[Scheduler] Error during scheduled problemset sync", e);
        }
    }

    @Scheduled(cron = "${app.sync.submissions-cron}")
    public void scheduledSubmissionsSync() {
        if (!props.isEnabled()) {
            log.debug("[Scheduler] Sync is disabled; skipping submissions sync");
            return;
        }

        long start = System.currentTimeMillis();
        log.info("[Scheduler] Starting scheduled submissions sync");

        List<User> users = userRepository.findByCfHandleNotNull();
        int successCount = 0;
        int errorCount = 0;
        SyncResult totalResult = SyncResult.EMPTY;

        for (User user : users) {
            try {
                SyncResult res = syncService.syncUserSubmissions(user);
                totalResult = totalResult.add(res);
                successCount++;
            } catch (Exception e) {
                errorCount++;
                log.error("[Scheduler] Failed to sync submissions for user {} (handle: {}): {}",
                        user.getId(), user.getCfHandle(), e.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - start;
        log.info("[Scheduler] Scheduled submissions sync completed in {}ms: {} users synced, {} failed | Submissions: {} inserted, {} skipped",
                duration, successCount, errorCount, totalResult.submissionsInserted(), totalResult.submissionsSkipped());
    }
}

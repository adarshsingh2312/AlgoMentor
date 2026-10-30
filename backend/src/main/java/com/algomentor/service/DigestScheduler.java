package com.algomentor.service;

import com.algomentor.config.MailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DigestScheduler {

    private static final Logger log = LoggerFactory.getLogger(DigestScheduler.class);

    private final MailProperties mailProperties;
    private final DigestService digestService;

    public DigestScheduler(MailProperties mailProperties, DigestService digestService) {
        this.mailProperties = mailProperties;
        this.digestService = digestService;
    }

    @Scheduled(cron = "${app.mail.digest-cron}", zone = "${app.timezone:Asia/Kolkata}")
    public void scheduledDailyDigest() {
        if (!mailProperties.isEnabled()) {
            log.debug("[Scheduler] Email sending is disabled in config; skipping scheduled daily digest");
            return;
        }

        log.info("[Scheduler] Starting scheduled daily problem digest");
        try {
            digestService.sendDailyDigests();
        } catch (Exception e) {
            log.error("[Scheduler] Error during scheduled daily digest run", e);
        }
    }
}


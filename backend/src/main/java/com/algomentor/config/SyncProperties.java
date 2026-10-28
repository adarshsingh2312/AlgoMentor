package com.algomentor.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.sync")
@Getter
@Setter
public class SyncProperties {

    /** Master switch to enable or disable background scheduled syncs. */
    private boolean enabled = true;

    /** Cron expression for Codeforces problemset sync (default: daily at 03:00). */
    private String problemsetCron = "0 0 3 * * *";

    /** Cron expression for Codeforces user submissions sync (default: every 6 hours). */
    private String submissionsCron = "0 0 */6 * * *";
}

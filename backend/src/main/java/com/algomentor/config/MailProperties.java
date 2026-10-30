package com.algomentor.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.mail")
@Getter
@Setter
public class MailProperties {

    //  Master switch to enable or disable email sending. 
    private boolean enabled = true;

    //  From address for outbound emails. 
    private String from = "digest@algomentor.local";

    //  Cron expression for daily digest email (default: daily at 07:00).
    private String digestCron = "0 0 7 * * *";

    //  Base URL for frontend or problem links (e.g. http://localhost:5173). 
    private String baseUrl = "http://localhost:5173";
}
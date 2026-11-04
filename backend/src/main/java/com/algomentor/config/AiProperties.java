package com.algomentor.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai")
@Getter
@Setter
public class AiProperties {

    private String baseUrl = "http://localhost:8000";
    private String serviceKey; // Set from AI_SERVICE_KEY env var (no default)
    private int ingestTimeoutSeconds = 30;
    private int askTimeoutSeconds = 60;
}


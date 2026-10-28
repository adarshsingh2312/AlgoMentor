package com.algomentor.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.codeforces")
@Getter
@Setter
public class CodeforcesProperties {
    private String baseUrl = "https://codeforces.com/api";
    private int timeoutSeconds = 10;
    /** Minimum gap between any two Codeforces API calls (ms). */
    private long minIntervalMs = 2000;
    private int maxAttempts = 3;
    private long initialBackoffMs = 1000;
}

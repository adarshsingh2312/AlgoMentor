package com.algomentor.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.analytics")
@Getter
@Setter
public class AnalyticsProperties {

    /** Minimum number of distinct problem attempts required before a tag can be considered weak. */
    private int minAttempts = 5;
}

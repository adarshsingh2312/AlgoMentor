package com.algomentor.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.recommender")
@Getter
@Setter
public class RecommenderProperties {

    /** Number of problems to recommend per day. */
    private int dailyCount = 3;

    /** Default rating when the user has no rated solved problems. */
    private int defaultRating = 1200;

    /** Rating window lower bound offset from base rating. */
    private int windowLower = 100;

    /** Rating window upper bound offset from base rating. */
    private int windowUpper = 200;

    /** Days to look back to avoid recommending recently served problems. */
    private int cooldownDays = 14;
}

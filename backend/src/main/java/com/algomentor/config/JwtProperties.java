package com.algomentor.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.jwt")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JwtProperties {
    private String secret;
    private long expiryMinutes = 60;
}

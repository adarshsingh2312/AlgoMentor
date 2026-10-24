package com.algomentor.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.oauth2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2Properties {

    private String redirectUri = "http://localhost:5173/auth/callback";
}

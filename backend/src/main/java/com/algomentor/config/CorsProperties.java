package com.algomentor.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.cors")
@Getter
@Setter
public class CorsProperties {

    private List<String> allowedOrigins = List.of(
            "http://localhost:5173",
            "http://127.0.0.1:5173"
    );
}


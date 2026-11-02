package com.algomentor.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.notes")
@Getter
@Setter
public class NotesProperties {
    private int maxDocuments = 50;
}

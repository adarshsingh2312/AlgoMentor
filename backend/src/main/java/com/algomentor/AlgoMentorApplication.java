package com.algomentor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
@EnableScheduling
public class AlgoMentorApplication {

    public static void main(String[] args) {
        loadDotenv();
        SpringApplication.run(AlgoMentorApplication.class, args);
    }

    private static void loadDotenv() {
        Path envPath = Path.of(".env");
        if (!Files.exists(envPath)) {
            envPath = Path.of("../.env");
        }
        if (Files.exists(envPath)) {
            try {
                for (String line : Files.readAllLines(envPath)) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#") && line.contains("=")) {
                        int eqIdx = line.indexOf('=');
                        String key = line.substring(0, eqIdx).trim();
                        String val = line.substring(eqIdx + 1).trim();
                        if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                            val = val.substring(1, val.length() - 1);
                        }
                        if (System.getProperty(key) == null && System.getenv(key) == null) {
                            System.setProperty(key, val);
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }
    }
}

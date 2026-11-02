package com.algomentor.service;

import com.algomentor.config.DsaProperties;
import com.algomentor.domain.DifficultyLevel;
import com.algomentor.domain.DsaTopic;
import com.algomentor.repository.DsaTopicRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class DsaSeederService {

    private static final Logger log = LoggerFactory.getLogger(DsaSeederService.class);

    private final DsaTopicRepository dsaTopicRepository;
    private final DsaProperties dsaProperties;
    private final ObjectMapper objectMapper;

    @PostConstruct
    @Transactional
    public void seed() {
        if (!dsaProperties.isSeedEnabled()) {
            log.info("DSA seeder disabled — skipping");
            return;
        }
        try {
            InputStream is = new ClassPathResource("dsa-sheet.json").getInputStream();
            JsonNode sections = objectMapper.readTree(is);

            int inserted = 0, updated = 0;
            for (JsonNode sectionNode : sections) {
                String section = sectionNode.get("section").asText();
                for (JsonNode item : sectionNode.get("items")) {
                    String title      = item.get("title").asText();
                    String url        = item.get("url").asText();
                    DifficultyLevel d = DifficultyLevel.valueOf(item.get("difficulty").asText());
                    int orderIndex    = item.get("orderIndex").asInt();

                    var existing = dsaTopicRepository.findBySectionAndTitle(section, title);
                    if (existing.isPresent()) {
                        DsaTopic t = existing.get();
                        // Update mutable fields in case the sheet was corrected
                        t.setUrl(url);
                        t.setDifficulty(d);
                        t.setOrderIndex(orderIndex);
                        updated++;
                    } else {
                        DsaTopic t = new DsaTopic();
                        t.setSection(section);
                        t.setTitle(title);
                        t.setUrl(url);
                        t.setDifficulty(d);
                        t.setOrderIndex(orderIndex);
                        dsaTopicRepository.save(t);
                        inserted++;
                    }
                }
            }
            log.info("DSA seed complete — inserted={}, updated={}", inserted, updated);
        } catch (Exception e) {
            log.error("DSA seeder failed", e);
            throw new IllegalStateException("DSA seeder failed", e);
        }
    }
}


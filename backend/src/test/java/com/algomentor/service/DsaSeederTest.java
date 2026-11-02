package com.algomentor.service;

import com.algomentor.config.DsaProperties;
import com.algomentor.repository.DsaTopicRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DsaSeederTest {

    @Autowired
    private DsaTopicRepository topicRepository;

    @Test
    @DisplayName("Running seeder twice results in exact same row count (idempotent upsert)")
    void seederIsIdempotent() {
        DsaProperties properties = new DsaProperties();
        properties.setSeedEnabled(true);
        ObjectMapper mapper = new ObjectMapper();

        DsaSeederService seeder = new DsaSeederService(topicRepository, properties, mapper);

        // First run
        seeder.seed();
        long countAfterFirstRun = topicRepository.count();
        assertThat(countAfterFirstRun).isGreaterThan(0);

        // Second run
        seeder.seed();
        long countAfterSecondRun = topicRepository.count();

        assertThat(countAfterSecondRun).isEqualTo(countAfterFirstRun);
    }
}


package com.algomentor.service;

import com.algomentor.dto.RecommendationResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DigestTemplateTest {

    private SpringTemplateEngine templateEngine;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");

        templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
    }

    @Test
    @DisplayName("digest.html should render problem name and direct Codeforces link")
    void shouldRenderProblemNameAndCodeforcesLink() {
        Context context = new Context();
        context.setVariable("name", "Adarsh");
        context.setVariable("date", "October 30, 2026");
        context.setVariable("solvedLast7Days", 5L);

        RecommendationResponseDto rec = new RecommendationResponseDto(
                UUID.randomUUID(),
                "Theatre Square",
                1000,
                List.of("math"),
                "https://codeforces.com/problemset/problem/1/A",
                "Weak tag: math (1/5 solved), rating 1000",
                "SERVED"
        );
        context.setVariable("recommendations", List.of(rec));

        String html = templateEngine.process("digest", context);

        assertThat(html).isNotBlank();
        assertThat(html).contains("Hi <strong");
        assertThat(html).contains("Adarsh");
        assertThat(html).contains("Theatre Square");
        assertThat(html).contains("https://codeforces.com/problemset/problem/1/A");
        assertThat(html).contains("Rating 1000");
        assertThat(html).contains("5");
    }
}

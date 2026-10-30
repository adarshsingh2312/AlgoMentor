package com.algomentor.service;

import com.algomentor.config.MailProperties;
import com.algomentor.domain.User;
import com.algomentor.dto.RecommendationResponseDto;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender javaMailSender;
    private final TemplateEngine templateEngine;
    private final MailProperties mailProperties;
    private final Clock clock;

    public MailService(JavaMailSender javaMailSender,
                       TemplateEngine templateEngine,
                       MailProperties mailProperties,
                       Clock clock) {
        this.javaMailSender = javaMailSender;
        this.templateEngine = templateEngine;
        this.mailProperties = mailProperties;
        this.clock = clock;
    }

    /**
     * Renders and sends the daily digest email to the given user.
     */
    public void sendDigest(User user, List<RecommendationResponseDto> recommendations, long solvedLast7Days) throws MessagingException {
        if (!mailProperties.isEnabled()) {
            log.info("[Mail] Email sending is disabled in config; skipping digest for {}", user.getEmail());
            return;
        }

        LocalDate today = LocalDate.now(clock);

        Context context = new Context();
        context.setVariable("name", user.getName() != null ? user.getName() : "Coder");
        context.setVariable("date", today.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")));
        context.setVariable("recommendations", recommendations != null ? recommendations : List.of());
        context.setVariable("solvedLast7Days", solvedLast7Days);

        String htmlContent = templateEngine.process("digest", context);

        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());

        helper.setFrom(mailProperties.getFrom());
        helper.setTo(user.getEmail());
        helper.setSubject("AlgoMentor Daily Problem Digest - " + today.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
        helper.setText(htmlContent, true);

        javaMailSender.send(mimeMessage);
        log.info("[Mail] Daily digest sent successfully to {}", user.getEmail());
    }

    /**
     * Asynchronously sends the daily digest email on the dedicated mailExecutor thread pool.
     */
    @Async("mailExecutor")
    public CompletableFuture<Void> sendDigestAsync(User user, List<RecommendationResponseDto> recommendations, long solvedLast7Days) {
        try {
            sendDigest(user, recommendations, solvedLast7Days);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}


package com.algomentor.service;

import com.algomentor.config.CodeforcesProperties;
import com.algomentor.exception.CodeforcesHandleNotFoundException;
import com.algomentor.exception.CodeforcesServiceException;
import com.algomentor.exception.CodeforcesUnavailableException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class CodeforcesService {

    private static final Logger log = LoggerFactory.getLogger(CodeforcesService.class);

    private final RestClient restClient;
    private final CodeforcesProperties props;

    /** Epoch-ms timestamp of the last completed Codeforces call. */
    private final AtomicLong lastCallAt = new AtomicLong(0);

    @org.springframework.beans.factory.annotation.Autowired
    public CodeforcesService(RestClient.Builder builder, CodeforcesProperties props) {
        this(builder, props, true);
    }

    public CodeforcesService(RestClient.Builder builder, CodeforcesProperties props, boolean applyDefaultFactory) {
        this.props = props;

        if (applyDefaultFactory) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            Duration timeout = Duration.ofSeconds(props.getTimeoutSeconds());
            factory.setConnectTimeout(timeout);
            factory.setReadTimeout(timeout);
            builder.requestFactory(factory);
        }

        this.restClient = builder
                .baseUrl(props.getBaseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; AlgoMentor/1.0)")
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Verify a CF handle exists (Day 2, unchanged semantics). */
    public void verifyHandleExists(String handle) {
        throttle();
        try {
            CfEnvelope response = restClient.get()
                    .uri("/user.info?handles={h}", handle)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        throw new CodeforcesHandleNotFoundException(handle);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        throw new CodeforcesServiceException(
                                "Codeforces API returned an error (" + resp.getStatusCode() + ")");
                    })
                    .body(CfEnvelope.class);

            if (response == null || !"OK".equalsIgnoreCase(response.status())) {
                throw new CodeforcesHandleNotFoundException(handle);
            }
        } catch (ResourceAccessException e) {
            throw new CodeforcesServiceException("Codeforces API is currently unreachable", e);
        }
    }

    /**
     * Fetch the full problemset. Retried with backoff.
     * @return list of CF problem DTOs (never null, may be empty)
     */
    public List<CfProblem> fetchProblemset() {
        CfProblemsetEnvelope env = withRetry(
                () -> {
                    throttle();
                    return restClient.get()
                            .uri("/problemset.problems")
                            .retrieve()
                            .body(CfProblemsetEnvelope.class);
                },
                "fetchProblemset");

        if (env == null || env.result() == null) {
            return List.of();
        }
        return env.result().problems() != null ? env.result().problems() : List.of();
    }

    /**
     * Fetch user submissions newest-first.
     * @param handle CF handle
     * @param from   1-based offset (1 = most recent)
     * @param count  page size
     */
    public List<CfSubmission> fetchSubmissions(String handle, int from, int count) {
        CfSubmissionsEnvelope env = withRetry(
                () -> {
                    throttle();
                    return restClient.get()
                            .uri("/user.status?handle={h}&from={f}&count={c}", handle, from, count)
                            .retrieve()
                            .body(CfSubmissionsEnvelope.class);
                },
                "fetchSubmissions(" + handle + ")");

        if (env == null || env.result() == null) {
            return List.of();
        }
        return env.result();
    }


    /**
     * Throttle: block this thread until at least minIntervalMs has passed
     * since the last Codeforces call. Called before every HTTP request.
     *
     * Why throttle is NOT the same as retry/backoff:
     *   Throttle = "don't hammer CF faster than their rate limit allows" (proactive, always on).
     *   Retry    = "if a call fails, wait and try again" (reactive, only on failure).
     * You need both: throttle to stay within rate limits on success,
     * backoff to handle transient failures without making things worse.
     */
    private synchronized void throttle() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastCallAt.get();
        if (elapsed < props.getMinIntervalMs()) {
            try {
                Thread.sleep(props.getMinIntervalMs() - elapsed);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        lastCallAt.set(System.currentTimeMillis());
    }

    
    @FunctionalInterface
    private interface CfCall<T> {
        T execute() throws Exception;
    }

    /**
     * Executes {@code call}, retrying on transient failures up to maxAttempts.
     *
     * Retried:  IO errors, timeouts, HTTP 5xx, HTTP 429,
     *           CF status=FAILED with "limit exceeded" in comment.
     * NOT retried: other 4xx (e.g. handle not found), other FAILED statuses.
     *
     * Backoff: initialBackoffMs * 2^(attempt-1) — so 1 s, 2 s, 4 s by default.
     */
    private <T extends CfEnvelopeBase> T withRetry(CfCall<T> call, String label) {
        int attempt = 0;
        long backoff = props.getInitialBackoffMs();
        Exception lastEx = null;

        while (attempt < props.getMaxAttempts()) {
            attempt++;
            try {
                T result = call.execute();
                // CF-level rate limit buried in a 200 OK response
                if (result != null
                        && "FAILED".equalsIgnoreCase(result.status())
                        && result.comment() != null
                        && result.comment().toLowerCase().contains("limit exceeded")) {
                    log.warn("[CF] {} attempt {}/{}: rate-limited by CF comment, backing off {}ms",
                            label, attempt, props.getMaxAttempts(), backoff);
                    sleep(backoff);
                    backoff *= 2;
                    continue;
                }
                return result;
            } catch (CodeforcesHandleNotFoundException | CodeforcesServiceException e) {
                // Non-transient — do not retry
                throw (RuntimeException) e;
            } catch (RestClientResponseException e) {
                int code = e.getStatusCode().value();
                if (code == 429 || (code >= 500 && code < 600)) {
                    log.warn("[CF] {} attempt {}/{}: HTTP {}, backing off {}ms",
                            label, attempt, props.getMaxAttempts(), code, backoff);
                    lastEx = e;
                    sleep(backoff);
                    backoff *= 2;
                } else {
                    // 4xx other than 429 — not transient
                    throw new CodeforcesServiceException("CF returned HTTP " + code + " for " + label, e);
                }
            } catch (ResourceAccessException e) {
                log.warn("[CF] {} attempt {}/{}: IO error: {}, backing off {}ms",
                        label, attempt, props.getMaxAttempts(), e.getMessage(), backoff);
                lastEx = e;
                sleep(backoff);
                backoff *= 2;
            } catch (Exception e) {
                throw new CodeforcesServiceException("Unexpected error calling CF for " + label, e);
            }
        }

        throw new CodeforcesUnavailableException(
                "Codeforces unavailable after " + props.getMaxAttempts() + " attempts for " + label, lastEx);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    interface CfEnvelopeBase {
        String status();
        String comment();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CfEnvelope(String status, String comment)
            implements CfEnvelopeBase {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CfProblemsetEnvelope(String status, String comment, CfProblemsetResult result)
            implements CfEnvelopeBase {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CfProblemsetResult(List<CfProblem> problems) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CfProblem(
            Integer contestId,
            String index,
            String name,
            Integer rating,       // nullable — not all problems have a rating
            List<String> tags
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CfSubmissionsEnvelope(String status, String comment, List<CfSubmission> result)
            implements CfEnvelopeBase {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CfSubmission(
            Long id,
            Long creationTimeSeconds,
            String verdict,           // nullable — null means "still being judged"
            String programmingLanguage,
            CfProblem problem
    ) {}
}

package com.algomentor.service;

import com.algomentor.config.CodeforcesProperties;
import com.algomentor.exception.CodeforcesServiceException;
import com.algomentor.exception.CodeforcesUnavailableException;
import com.algomentor.service.CodeforcesService.CfProblem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CodeforcesRetryTest {

    private MockRestServiceServer mockServer;
    private CodeforcesService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();

        CodeforcesProperties props = new CodeforcesProperties();
        props.setBaseUrl("https://codeforces.com/api");
        props.setMinIntervalMs(0);
        props.setInitialBackoffMs(5); // fast backoff for unit tests
        props.setMaxAttempts(3);
        props.setTimeoutSeconds(2);

        service = new CodeforcesService(builder, props, false);
    }

    @Test
    @DisplayName("Should retry on transient 5xx and succeed on 3rd attempt")
    void shouldRetryOnServerErrorAndSucceed() {
        String jsonSuccess = """
                {
                    "status": "OK",
                    "result": {
                        "problems": [
                            {"contestId": 1, "index": "A", "name": "Theatre Square", "rating": 1000, "tags": ["math"]}
                        ]
                    }
                }
                """;

        mockServer.expect(requestTo("https://codeforces.com/api/problemset.problems"))
                .andRespond(withServerError());
        mockServer.expect(requestTo("https://codeforces.com/api/problemset.problems"))
                .andRespond(withServerError());
        mockServer.expect(requestTo("https://codeforces.com/api/problemset.problems"))
                .andRespond(withSuccess(jsonSuccess, MediaType.APPLICATION_JSON));

        List<CfProblem> problems = service.fetchProblemset();

        assertThat(problems).hasSize(1);
        assertThat(problems.get(0).name()).isEqualTo("Theatre Square");
        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw CodeforcesUnavailableException after exceeding max attempts")
    void shouldThrowUnavailableAfterMaxAttempts() {
        mockServer.expect(requestTo("https://codeforces.com/api/problemset.problems"))
                .andRespond(withServerError());
        mockServer.expect(requestTo("https://codeforces.com/api/problemset.problems"))
                .andRespond(withServerError());
        mockServer.expect(requestTo("https://codeforces.com/api/problemset.problems"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> service.fetchProblemset())
                .isInstanceOf(CodeforcesUnavailableException.class)
                .hasMessageContaining("Codeforces unavailable after 3 attempts");

        mockServer.verify();
    }

    @Test
    @DisplayName("Should immediately fail on non-retryable 4xx client errors")
    void shouldNotRetryOn4xxClientErrors() {
        mockServer.expect(requestTo("https://codeforces.com/api/problemset.problems"))
                .andRespond(withBadRequest());

        assertThatThrownBy(() -> service.fetchProblemset())
                .isInstanceOf(CodeforcesServiceException.class)
                .hasMessageContaining("HTTP 400");

        mockServer.verify();
    }
}

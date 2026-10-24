package com.algomentor.service;

import com.algomentor.exception.CodeforcesHandleNotFoundException;
import com.algomentor.exception.CodeforcesServiceException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Service
public class CodeforcesService {

    private final RestClient restClient;

    public CodeforcesService(RestClient.Builder restClientBuilder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; AlgoMentor/1.0)")
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public void verifyHandleExists(String handle) {
        try {
            CodeforcesUserResponse response = restClient.get()
                    .uri("https://codeforces.com/api/user.info?handles={handle}", handle)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        throw new CodeforcesHandleNotFoundException(handle);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        throw new CodeforcesServiceException("Codeforces API returned an error (" + resp.getStatusCode() + ")");
                    })
                    .body(CodeforcesUserResponse.class);

            if (response == null || !"OK".equalsIgnoreCase(response.status())) {
                throw new CodeforcesHandleNotFoundException(handle);
            }
        } catch (ResourceAccessException e) {
            throw new CodeforcesServiceException("Codeforces API is currently unreachable", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CodeforcesUserResponse(String status, String comment) {
    }
}

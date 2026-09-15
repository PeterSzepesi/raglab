package com.example.arxivrag.ai.foundry;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Shared pooled HTTP transport. It owns HTTP mechanics, never model payload semantics. */
@Component
@Profile("foundry")
final class FoundryHttpTransport {

    private final FoundryProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final FoundryMetrics metrics;
    private final FoundryAuthentication authentication;

    @Autowired
    FoundryHttpTransport(FoundryProperties properties, ObjectMapper objectMapper,
                         FoundryMetrics metrics, FoundryAuthentication authentication) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(requirePositive(properties.getRequestTimeout()))
                .build(), metrics, authentication);
    }

    FoundryHttpTransport(FoundryProperties properties, ObjectMapper objectMapper, HttpClient httpClient,
                         FoundryMetrics metrics, FoundryAuthentication authentication) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.metrics = metrics;
        this.authentication = authentication;
    }

    JsonResponse post(String path, String body, FoundryOperation operation) {
        var started = Instant.now();
        try {
            var response = httpClient.send(request(path, body), HttpResponse.BodyHandlers.ofString());
            var duration = Duration.between(started, Instant.now());
            record(operation, duration);
            JsonNode json = null;
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                json = objectMapper.readTree(response.body());
            }
            return new JsonResponse(response.statusCode(), response.body(), json, response.headers(), duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new FoundryClient.FoundryProviderException("Foundry request was interrupted", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof FoundryClient.FoundryProviderException providerException) {
                throw providerException;
            }
            throw FoundryClient.FoundryProviderException.network(
                    "Could not call Microsoft AI Foundry at " + baseUrl(), exception);
        }
    }

    StreamResponse stream(String path, String body, FoundryOperation operation) {
        var started = Instant.now();
        var future = httpClient.sendAsync(request(path, body), HttpResponse.BodyHandlers.ofInputStream());
        try {
            var response = future.get();
            var duration = Duration.between(started, Instant.now());
            record(operation, duration);
            return new StreamResponse(response.statusCode(), response.body(), response.headers(), duration);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new FoundryClient.FoundryProviderException("Foundry streaming request was interrupted", exception);
        } catch (ExecutionException exception) {
            var cause = exception.getCause() == null ? exception : exception.getCause();
            throw FoundryClient.FoundryProviderException.network(
                    "Could not start Microsoft AI Foundry stream", cause);
        }
    }

    private HttpRequest request(String path, String body) {
        var timeout = properties.getRequestTimeout();
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new FoundryClient.FoundryProviderException("foundry.request-timeout must be positive");
        }
        var builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path + "?api-version=" + encode(properties.getApiVersion())))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        var requestId = org.slf4j.MDC.get("requestId");
        if (requestId != null && requestId.matches("[A-Za-z0-9._-]{1,64}")) {
            builder.header("x-ms-client-request-id", requestId);
        }
        authentication.apply(builder);
        return builder.build();
    }

    private String baseUrl() {
        if (properties.getEndpoint() == null || properties.getEndpoint().isBlank()) {
            throw new FoundryClient.FoundryProviderException("foundry.endpoint must not be blank");
        }
        return properties.getEndpoint().replaceAll("/+$", "");
    }

    private void record(FoundryOperation operation, Duration duration) {
        if (metrics != null) metrics.request(operation, duration);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static Duration requirePositive(Duration configured) {
        if (configured == null || configured.isZero() || configured.isNegative()) {
            throw new FoundryClient.FoundryProviderException("foundry.request-timeout must be positive");
        }
        return configured;
    }

    record JsonResponse(int statusCode, String rawBody, JsonNode body, HttpHeaders headers, Duration duration) { }
    record StreamResponse(int statusCode, InputStream body, HttpHeaders headers, Duration duration) { }
}

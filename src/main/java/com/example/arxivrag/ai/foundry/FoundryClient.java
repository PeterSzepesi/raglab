package com.example.arxivrag.ai.foundry;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Profile("foundry")
public class FoundryClient {

    private final FoundryProperties properties;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient;

    public FoundryClient(FoundryProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(timeout(properties.getRequestTimeout()))
                .build());
    }

    FoundryClient(FoundryProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public List<Double> embed(String deployment, String input, int dimensions) {
        var body = writeJson(Map.of(
                "model", deployment,
                "input", List.of(input),
                "dimensions", dimensions
        ));
        var response = post("/openai/deployments/" + encode(deployment) + "/embeddings", body);
        var data = response.path("data");
        if (!data.isArray() || data.size() != 1 || !data.get(0).path("embedding").isArray()) {
            throw new FoundryProviderException("Foundry embedding response did not contain one embedding array");
        }

        var values = new ArrayList<Double>();
        for (var value : data.get(0).path("embedding")) {
            if (!value.isNumber() || !Double.isFinite(value.doubleValue())) {
                throw new FoundryProviderException("Foundry returned a non-finite embedding value");
            }
            values.add(value.doubleValue());
        }
        if (values.isEmpty()) {
            throw new FoundryProviderException("Foundry returned an empty embedding");
        }
        return List.copyOf(values);
    }

    public String chat(String deployment, String systemPrompt, String userMessage) {
        var messages = new ArrayList<Map<String, String>>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        var body = writeJson(Map.of(
                "model", deployment,
                "messages", messages
        ));
        var response = post("/openai/deployments/" + encode(deployment) + "/chat/completions", body);
        var content = response.path("choices").path(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            throw new FoundryProviderException("Foundry chat response did not contain message content");
        }
        return content;
    }

    private JsonNode post(String path, String body) {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path + "?api-version=" + encode(properties.getApiVersion())))
                .timeout(timeout(properties.getRequestTimeout()))
                .header("Content-Type", "application/json")
                .header("api-key", requireApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new FoundryProviderException("Foundry request failed with HTTP "
                        + response.statusCode() + ": " + truncate(response.body()));
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new FoundryProviderException("Foundry request was interrupted", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof FoundryProviderException providerException) {
                throw providerException;
            }
            throw new FoundryProviderException("Could not call Microsoft AI Foundry at " + baseUrl(), exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new FoundryProviderException("Could not serialize Foundry request", exception);
        }
    }

    private String baseUrl() {
        if (properties.getEndpoint() == null || properties.getEndpoint().isBlank()) {
            throw new FoundryProviderException("foundry.endpoint must not be blank");
        }
        return properties.getEndpoint().replaceAll("/+$", "");
    }

    private String requireApiKey() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new FoundryProviderException("AZURE_AI_FOUNDRY_API_KEY must be configured for the foundry profile");
        }
        return properties.getApiKey();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static Duration timeout(Duration configured) {
        if (configured == null || configured.isZero() || configured.isNegative()) {
            throw new FoundryProviderException("foundry.request-timeout must be positive");
        }
        return configured;
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 500 ? value : value.substring(0, 500) + "...";
    }

    public static class FoundryProviderException extends RuntimeException {

        public FoundryProviderException(String message) {
            super(message);
        }

        public FoundryProviderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

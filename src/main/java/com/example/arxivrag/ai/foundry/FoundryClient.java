package com.example.arxivrag.ai.foundry;

import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Profile("foundry")
public class FoundryClient {

    private final FoundryProperties properties;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient;

    private final FoundryQuotaLimiter quotaLimiter;

    @Autowired
    public FoundryClient(FoundryProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(timeout(properties.getRequestTimeout()))
                .build(), new FoundryQuotaLimiter(properties.getMaxRequestsPerMinute(), properties.getMaxTokensPerMinute()));
    }

    FoundryClient(FoundryProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this(properties, objectMapper, httpClient,
                new FoundryQuotaLimiter(properties.getMaxRequestsPerMinute(), properties.getMaxTokensPerMinute()));
    }

    FoundryClient(FoundryProperties properties, ObjectMapper objectMapper, HttpClient httpClient,
                  FoundryQuotaLimiter quotaLimiter) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.quotaLimiter = quotaLimiter;
    }

    public List<Double> embed(String deployment, String input, int dimensions) {
        return embed(deployment, List.of(input), dimensions).getFirst();
    }

    public List<List<Double>> embed(String deployment, List<String> inputs, int dimensions) {
        if (deployment == null || deployment.isBlank()) {
            throw new FoundryProviderException("Foundry embedding deployment must not be blank");
        }
        if (inputs == null || inputs.isEmpty()) {
            throw new FoundryProviderException("Foundry embedding requires at least one input");
        }
        if (dimensions <= 0) {
            throw new FoundryProviderException("Foundry embedding dimensions must be positive");
        }
        var body = writeJson(Map.of(
                "model", deployment,
                "input", inputs,
                "dimensions", dimensions
        ));
        quotaLimiter.acquire(inputs.stream().mapToInt(FoundryClient::estimateTokens).sum());
        var response = postWithRetry("/openai/deployments/" + encode(deployment) + "/embeddings", body);
        var data = response.path("data");
        if (!data.isArray() || data.size() != inputs.size()) {
            throw new FoundryProviderException("Foundry embedding response contained "
                    + (data.isArray() ? data.size() : 0) + " vectors for " + inputs.size() + " inputs");
        }

        var embeddings = new ArrayList<List<Double>>(java.util.Collections.nCopies(inputs.size(), null));
        var seenIndexes = new java.util.HashSet<Integer>();
        for (var position = 0; position < data.size(); position++) {
            var item = data.get(position);
            var index = item.path("index").isInt() ? item.path("index").intValue() : position;
            if (index < 0 || index >= inputs.size() || !seenIndexes.add(index)) {
                throw new FoundryProviderException("Foundry embedding response contained invalid vector indexes");
            }
            var embedding = item.path("embedding");
            if (!embedding.isArray()) {
                throw new FoundryProviderException("Foundry embedding response did not contain an embedding array");
            }
            var values = new ArrayList<Double>(embedding.size());
            for (var value : embedding) {
                if (!value.isNumber() || !Double.isFinite(value.doubleValue())) {
                    throw new FoundryProviderException("Foundry returned a non-finite embedding value");
                }
                values.add(value.doubleValue());
            }
            if (values.isEmpty()) {
                throw new FoundryProviderException("Foundry returned an empty embedding");
            }
            embeddings.set(index, List.copyOf(values));
        }
        if (embeddings.stream().anyMatch(java.util.Objects::isNull)) {
            throw new FoundryProviderException("Foundry embedding response did not contain every input index");
        }
        return List.copyOf(embeddings);
    }

    public String chat(String deployment, String systemPrompt, String userMessage) {
        var messages = new ArrayList<Map<String, Object>>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        var result = chatWithTools(deployment, new ArrayList<>(messages), List.of());
        if (result.content() == null || result.content().isBlank()) {
            throw new FoundryProviderException("Foundry chat response did not contain message content");
        }
        return result.content();
    }

    public FoundryChatResult chatWithTools(
            String deployment,
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools
    ) {
        if (deployment == null || deployment.isBlank()) {
            throw new FoundryProviderException("Foundry chat deployment must not be blank");
        }
        if (messages == null || messages.isEmpty()) {
            throw new FoundryProviderException("Foundry chat requires at least one message");
        }

        var payload = new HashMap<String, Object>();
        payload.put("model", deployment);
        payload.put("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            payload.put("tools", tools);
            payload.put("tool_choice", "auto");
        }

        quotaLimiter.acquire(messages.stream()
                .mapToInt(message -> estimateTokens(String.valueOf(message.getOrDefault("content", ""))))
                .sum());
        var response = postWithRetry("/openai/deployments/" + encode(deployment) + "/chat/completions", writeJson(payload));
        var message = response.path("choices").path(0).path("message");
        if (!message.isObject()) {
            throw new FoundryProviderException("Foundry chat response did not contain an assistant message");
        }

        var toolCalls = new ArrayList<FoundryToolCall>();
        var toolCallNodes = message.path("tool_calls");
        if (toolCallNodes.isArray()) {
            for (var toolCallNode : toolCallNodes) {
                var id = toolCallNode.path("id").asText(null);
                var function = toolCallNode.path("function");
                var name = function.path("name").asText(null);
                var arguments = function.path("arguments").asText(null);
                if (id == null || id.isBlank() || name == null || name.isBlank() || arguments == null) {
                    throw new FoundryProviderException("Foundry returned an invalid tool call");
                }
                toolCalls.add(new FoundryToolCall(id, name, parseArguments(arguments)));
            }
        }

        @SuppressWarnings("unchecked")
        var assistantMessage = (Map<String, Object>) objectMapper.convertValue(message, Map.class);
        var content = message.path("content").asText(null);
        if ((content == null || content.isBlank()) && toolCalls.isEmpty()) {
            throw new FoundryProviderException("Foundry chat response contained neither content nor tool calls");
        }
        return new FoundryChatResult(content, List.copyOf(toolCalls),
                Collections.unmodifiableMap(new LinkedHashMap<>(assistantMessage)));
    }

    private Map<String, Object> parseArguments(String arguments) {
        try {
            var node = objectMapper.readTree(arguments);
            if (!node.isObject()) {
                throw new FoundryProviderException("Foundry tool arguments must be a JSON object");
            }
            @SuppressWarnings("unchecked")
            var values = (Map<String, Object>) objectMapper.convertValue(node, Map.class);
            return Collections.unmodifiableMap(new LinkedHashMap<>(values));
        } catch (RuntimeException exception) {
            if (exception instanceof FoundryProviderException providerException) {
                throw providerException;
            }
            throw new FoundryProviderException("Foundry returned invalid tool arguments", exception);
        }
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
                var responseHeaders = response.headers();
                Duration retryAfter = null;
                if (responseHeaders != null) {
                    retryAfter = responseHeaders.firstValue("retry-after")
                            .flatMap(FoundryClient::parseRetryAfter)
                            .orElse(null);
                }
                var requestId = responseHeaders == null ? null : responseHeaders.firstValue("x-request-id").orElse(null);
                throw new FoundryProviderException(
                        "Foundry request failed with HTTP " + response.statusCode() + ": " + truncate(response.body()),
                        response.statusCode(), retryAfter, requestId
                );
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

    private JsonNode postWithRetry(String path, String body) {
        var attempt = 0;
        while (true) {
            try {
                return post(path, body);
            } catch (FoundryProviderException exception) {
                if (!exception.retryable() || attempt >= Math.max(0, properties.getMaxRetries())) {
                    throw exception;
                }
                var delay = exception.retryAfter() == null
                        ? backoff(attempt)
                        : exception.retryAfter();
                sleep(delay);
                attempt++;
            }
        }
    }

    private Duration backoff(int attempt) {
        var initial = positiveDuration(properties.getRetryInitialBackoff(), Duration.ofSeconds(1));
        var maximum = positiveDuration(properties.getRetryMaxBackoff(), Duration.ofSeconds(10));
        var multiplier = 1L << Math.min(attempt, 20);
        return initial.multipliedBy(multiplier).compareTo(maximum) > 0 ? maximum : initial.multipliedBy(multiplier);
    }

    private static Duration positiveDuration(Duration value, Duration fallback) {
        return value == null || value.isNegative() || value.isZero() ? fallback : value;
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(Math.max(1L, duration.toMillis()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new FoundryProviderException("Foundry retry wait was interrupted", exception);
        }
    }

    private static java.util.Optional<Duration> parseRetryAfter(String value) {
        try {
            return java.util.Optional.of(Duration.ofSeconds(Long.parseLong(value.trim())));
        } catch (NumberFormatException ignored) {
            try {
                var retryAt = java.time.ZonedDateTime.parse(value.trim(), java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME);
                return java.util.Optional.of(Duration.between(Instant.now(), retryAt.toInstant()).isNegative()
                        ? Duration.ZERO
                        : Duration.between(Instant.now(), retryAt.toInstant()));
            } catch (RuntimeException ignoredDate) {
                return java.util.Optional.empty();
            }
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

    private static int estimateTokens(String value) {
        return Math.max(1, (value == null ? 0 : value.length() + 3) / 4);
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 500 ? value : value.substring(0, 500) + "...";
    }

    public static class FoundryProviderException extends RuntimeException {

        private final int statusCode;

        private final Duration retryAfter;

        private final String requestId;

        public FoundryProviderException(String message) {
            this(message, null, 0, null, null);
        }

        public FoundryProviderException(String message, Throwable cause) {
            this(message, cause, 0, null, null);
        }

        private FoundryProviderException(String message, int statusCode, Duration retryAfter, String requestId) {
            this(message, null, statusCode, retryAfter, requestId);
        }

        private FoundryProviderException(String message, Throwable cause, int statusCode,
                                         Duration retryAfter, String requestId) {
            super(message, cause);
            this.statusCode = statusCode;
            this.retryAfter = retryAfter;
            this.requestId = requestId;
        }

        public int statusCode() {
            return statusCode;
        }

        public Duration retryAfter() {
            return retryAfter;
        }

        public String requestId() {
            return requestId;
        }

        public boolean retryable() {
            return statusCode == 408 || statusCode == 429 || statusCode == 500
                    || statusCode == 502 || statusCode == 503 || statusCode == 504;
        }
    }

    public record FoundryToolCall(String id, String name, Map<String, Object> arguments) {
    }

    public record FoundryChatResult(
            String content,
            List<FoundryToolCall> toolCalls,
            Map<String, Object> assistantMessage
    ) {
    }
}

package com.example.arxivrag.ai.foundry;

import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Profile("foundry")
public class FoundryClient {

    private static final Logger logger = LoggerFactory.getLogger(FoundryClient.class);

    private final FoundryProperties properties;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient;

    private final FoundryQuotaLimiterRegistry quotaLimiters;

    private final FoundryMetrics metrics;

    @Autowired
    public FoundryClient(
            FoundryProperties properties,
            ObjectMapper objectMapper,
            FoundryQuotaLimiterRegistry quotaLimiters,
            FoundryMetrics metrics
    ) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(timeout(properties.getRequestTimeout()))
                .build(), quotaLimiters, metrics);
    }

    FoundryClient(FoundryProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this(properties, objectMapper, httpClient,
                new FoundryQuotaLimiterRegistry(properties), null);
    }

    FoundryClient(FoundryProperties properties, ObjectMapper objectMapper, HttpClient httpClient,
                  FoundryQuotaLimiter quotaLimiter) {
        this(properties, objectMapper, httpClient,
                new FoundryQuotaLimiterRegistry(properties) {
                    @Override
                    public Duration acquire(String deployment, FoundryOperation operation, int estimatedTokens) {
                        return quotaLimiter.acquire(estimatedTokens, operation.background(),
                                properties.getInteractiveReservedRequestsPerMinute(),
                                properties.getInteractiveReservedTokensPerMinute(), Duration.ofDays(1));
                    }
                }, null);
    }

    FoundryClient(FoundryProperties properties, ObjectMapper objectMapper, HttpClient httpClient,
                  FoundryQuotaLimiterRegistry quotaLimiters, FoundryMetrics metrics) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.quotaLimiters = quotaLimiters;
        this.metrics = metrics;
    }

    public List<Double> embed(String deployment, String input, int dimensions) {
        return embedInternal(deployment, List.of(input), dimensions, FoundryOperation.QUERY_EMBEDDING).getFirst();
    }

    public List<List<Double>> embed(String deployment, List<String> inputs, int dimensions) {
        return embedInternal(deployment, inputs, dimensions, FoundryOperation.INGESTION_EMBEDDING);
    }

    private List<List<Double>> embedInternal(
            String deployment, List<String> inputs, int dimensions, FoundryOperation operation) {
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
        quotaLimiters.acquire(deployment, operation,
                inputs.stream().mapToInt(FoundryClient::estimateTokens).sum());
        var response = postWithRetry("/openai/deployments/" + encode(deployment) + "/embeddings", body, operation);
        var data = response.body().path("data");
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

        var result = chatWithTools(deployment, new ArrayList<>(messages), List.of(), FoundryChatOptions.defaults());
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
        return chatWithTools(deployment, messages, tools, FoundryChatOptions.defaults());
    }

    public FoundryChatResult chatWithTools(
            String deployment,
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools,
            FoundryChatOptions options
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
        payload.put("n", 1);
        applyOptions(payload, options);
        if (tools != null && !tools.isEmpty()) {
            payload.put("tools", tools);
            payload.put("tool_choice", "auto");
        }

        var quotaWait = quotaLimiters.acquire(deployment, FoundryOperation.CHAT,
                estimatedChatTokens(messages, options));
        var response = postWithRetry("/openai/deployments/" + encode(deployment) + "/chat/completions",
                writeJson(payload), FoundryOperation.CHAT);
        var message = response.body().path("choices").path(0).path("message");
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
                Collections.unmodifiableMap(new LinkedHashMap<>(assistantMessage)),
                metadata(response, quotaWait));
    }

    public FoundryStreamResult streamChat(
            String deployment,
            List<Map<String, Object>> messages,
            FoundryChatOptions options,
            Consumer<String> deltaConsumer
    ) {
        if (deployment == null || deployment.isBlank()) {
            throw new FoundryProviderException("Foundry chat deployment must not be blank");
        }
        if (messages == null || messages.isEmpty()) {
            throw new FoundryProviderException("Foundry chat requires at least one message");
        }
        if (deltaConsumer == null) {
            throw new IllegalArgumentException("deltaConsumer must not be null");
        }
        var payload = new HashMap<String, Object>();
        payload.put("model", deployment);
        payload.put("messages", messages);
        payload.put("n", 1);
        payload.put("stream", true);
        payload.put("stream_options", Map.of("include_usage", true));
        applyOptions(payload, options);

        var quotaWait = quotaLimiters.acquire(deployment, FoundryOperation.CHAT,
                estimatedChatTokens(messages, options));
        var body = writeJson(payload);
        var path = "/openai/deployments/" + encode(deployment) + "/chat/completions";
        var retryWait = Duration.ZERO;
        var providerDuration = Duration.ZERO;
        var attempt = 0;
        while (true) {
            var started = Instant.now();
            var response = sendStreaming(request(path, body));
            var requestDuration = Duration.between(started, Instant.now());
            providerDuration = providerDuration.plus(requestDuration);
            if (metrics != null) {
                metrics.request(FoundryOperation.CHAT, requestDuration);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                var failure = providerFailure(response.statusCode(), response.headers(), readBody(response.body()));
                if (!failure.retryable() || attempt >= maxRetries(FoundryOperation.CHAT)) {
                    throw failure;
                }
                var delay = failure.retryAfter() == null ? backoff(attempt) : failure.retryAfter();
                retryWait = retryWait.plus(delay);
                if (metrics != null) {
                    metrics.retry(FoundryOperation.CHAT, failure.statusCode(), delay);
                }
                logger.warn("Retrying Foundry streaming chat after status={} delay={} attempt={} requestId={}",
                        failure.statusCode(), delay, attempt + 1, failure.requestId());
                sleep(delay);
                attempt++;
                continue;
            }

            var requestId = requestId(response.headers());
            var parser = new FoundrySseParser(objectMapper, deltaConsumer);
            try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException("streaming request cancelled");
                    }
                    parser.acceptLine(line);
                }
                parser.finish();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new FoundryProviderException("Foundry streaming request was interrupted", exception);
            } catch (IOException exception) {
                throw new FoundryProviderException("Could not stream Microsoft AI Foundry response", exception);
            }
            if (parser.content().isBlank()) {
                throw new FoundryProviderException("Foundry streaming response did not contain message content");
            }
            return new FoundryStreamResult(parser.content(),
                    new ProviderMetadata(parser.promptTokens(), parser.completionTokens(), parser.totalTokens(),
                            parser.reasoningTokens(), parser.cachedPromptTokens(), requestId,
                            providerDuration, attempt, quotaWait, retryWait));
        }
    }

    private HttpResponse<InputStream> sendStreaming(HttpRequest request) {
        var future = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
        try {
            return future.get();
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new FoundryProviderException("Foundry streaming request was interrupted", exception);
        } catch (ExecutionException exception) {
            var cause = exception.getCause() == null ? exception : exception.getCause();
            throw new FoundryProviderException("Could not start Microsoft AI Foundry stream", cause);
        }
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

    private ProviderResponse post(String path, String body, FoundryOperation operation) {
        var request = request(path, body);
        var started = Instant.now();

        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            var duration = Duration.between(started, Instant.now());
            if (metrics != null) {
                metrics.request(operation, duration);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw providerFailure(response.statusCode(), response.headers(), response.body());
            }
            return new ProviderResponse(objectMapper.readTree(response.body()), response.headers(), duration, 0,
                    Duration.ZERO);
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

    private HttpRequest request(String path, String body) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path + "?api-version=" + encode(properties.getApiVersion())))
                .timeout(timeout(properties.getRequestTimeout()))
                .header("Content-Type", "application/json")
                .header("api-key", requireApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private FoundryProviderException providerFailure(int statusCode, java.net.http.HttpHeaders headers, String body) {
        var retryAfter = headers == null ? null : headers.firstValue("retry-after")
                .flatMap(FoundryClient::parseRetryAfter).orElse(null);
        return new FoundryProviderException(
                "Foundry request failed with HTTP " + statusCode + ": " + truncate(body),
                statusCode, retryAfter, requestId(headers));
    }

    private ProviderResponse postWithRetry(String path, String body, FoundryOperation operation) {
        var attempt = 0;
        var retryWait = Duration.ZERO;
        while (true) {
            try {
                var response = post(path, body, operation);
                return new ProviderResponse(response.body(), response.headers(), response.duration(), attempt, retryWait);
            } catch (FoundryProviderException exception) {
                if (!exception.retryable() || attempt >= maxRetries(operation)) {
                    throw exception;
                }
                var delay = exception.retryAfter() == null
                        ? backoff(attempt)
                        : exception.retryAfter();
                retryWait = retryWait.plus(delay);
                if (metrics != null) {
                    metrics.retry(operation, exception.statusCode(), delay);
                }
                logger.warn("Retrying Foundry {} request after status={} delay={} attempt={} requestId={}",
                        operation.metricName(), exception.statusCode(), delay, attempt + 1, exception.requestId());
                sleep(delay);
                attempt++;
            }
        }
    }

    private int maxRetries(FoundryOperation operation) {
        return Math.max(0, switch (operation) {
            case CHAT -> properties.getChatMaxRetries();
            case QUERY_EMBEDDING -> properties.getQueryEmbeddingMaxRetries();
            case INGESTION_EMBEDDING -> properties.getMaxRetries();
        });
    }

    private ProviderMetadata metadata(ProviderResponse response, Duration quotaWait) {
        var usage = response.body().path("usage");
        var promptDetails = usage.path("prompt_tokens_details");
        var completionDetails = usage.path("completion_tokens_details");
        return new ProviderMetadata(
                usage.path("prompt_tokens").asInt(-1),
                usage.path("completion_tokens").asInt(-1),
                usage.path("total_tokens").asInt(-1),
                completionDetails.path("reasoning_tokens").asInt(-1),
                promptDetails.path("cached_tokens").asInt(-1),
                requestId(response.headers()), response.duration(), response.retryCount(),
                quotaWait, response.retryWait());
    }

    private static String requestId(java.net.http.HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        return headers.firstValue("apim-request-id")
                .or(() -> headers.firstValue("x-request-id"))
                .orElse(null);
    }

    private static void applyOptions(Map<String, Object> payload, FoundryChatOptions options) {
        var effective = options == null ? FoundryChatOptions.defaults() : options;
        if (effective.maxCompletionTokens() != null) {
            payload.put("max_completion_tokens", effective.maxCompletionTokens());
        }
        if (effective.temperature() != null) {
            payload.put("temperature", effective.temperature());
        }
    }

    private static String readBody(InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return "<unreadable response body>";
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

    private static int estimatedChatTokens(List<Map<String, Object>> messages, FoundryChatOptions options) {
        var promptTokens = messages.stream()
                .mapToInt(message -> estimateTokens(String.valueOf(message.getOrDefault("content", ""))))
                .sum();
        var effective = options == null ? FoundryChatOptions.defaults() : options;
        return promptTokens + Math.max(0,
                effective.maxCompletionTokens() == null ? 0 : effective.maxCompletionTokens());
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

        public FoundryProviderException(String message, int statusCode, Duration retryAfter, String requestId) {
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
            Map<String, Object> assistantMessage,
            ProviderMetadata metadata
    ) {
        public FoundryChatResult(String content, List<FoundryToolCall> toolCalls, Map<String, Object> assistantMessage) {
            this(content, toolCalls, assistantMessage, ProviderMetadata.unknown());
        }
    }

    public record FoundryChatOptions(Integer maxCompletionTokens, Double temperature) {
        public static FoundryChatOptions defaults() {
            return new FoundryChatOptions(null, null);
        }
    }

    public record ProviderMetadata(
            int promptTokens,
            int completionTokens,
            int totalTokens,
            int reasoningTokens,
            int cachedPromptTokens,
            String requestId,
            Duration requestDuration,
            int retryCount,
            Duration quotaWait,
            Duration retryWait
    ) {
        public static ProviderMetadata unknown() {
            return new ProviderMetadata(-1, -1, -1, -1, -1, null,
                    Duration.ZERO, 0, Duration.ZERO, Duration.ZERO);
        }
    }

    public record FoundryStreamResult(String content, ProviderMetadata metadata) {
    }

    private record ProviderResponse(
            JsonNode body,
            java.net.http.HttpHeaders headers,
            Duration duration,
            int retryCount,
            Duration retryWait
    ) {
    }
}

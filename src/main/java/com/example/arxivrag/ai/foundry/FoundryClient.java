package com.example.arxivrag.ai.foundry;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Compatibility facade over the three Foundry capability clients.
 *
 * <p>HTTP mechanics, retry policy, payload parsing, and SSE handling deliberately live in the
 * capability-specific collaborators so callers can use and test only the protocol they need.</p>
 */
@Component
@Profile("foundry")
public class FoundryClient {

    private final FoundryEmbeddingClient embeddings;
    private final FoundryChatClient chat;
    private final FoundryStreamingChatClient streamingChat;

    @Autowired
    public FoundryClient(
            FoundryEmbeddingClient embeddings,
            FoundryChatClient chat,
            FoundryStreamingChatClient streamingChat
    ) {
        this.embeddings = embeddings;
        this.chat = chat;
        this.streamingChat = streamingChat;
    }

    FoundryClient(FoundryProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this(testClients(properties, objectMapper, httpClient,
                new FoundryQuotaLimiterRegistry(properties), null, apiKey(properties)));
    }

    FoundryClient(
            FoundryProperties properties,
            ObjectMapper objectMapper,
            HttpClient httpClient,
            FoundryQuotaLimiter quotaLimiter
    ) {
        this(testClients(properties, objectMapper, httpClient,
                singleLimiterRegistry(properties, quotaLimiter), null, apiKey(properties)));
    }

    FoundryClient(
            FoundryProperties properties,
            ObjectMapper objectMapper,
            HttpClient httpClient,
            FoundryQuotaLimiterRegistry quotaLimiters,
            FoundryMetrics metrics
    ) {
        this(testClients(properties, objectMapper, httpClient, quotaLimiters, metrics, apiKey(properties)));
    }

    FoundryClient(
            FoundryProperties properties,
            ObjectMapper objectMapper,
            HttpClient httpClient,
            FoundryQuotaLimiterRegistry quotaLimiters,
            FoundryMetrics metrics,
            FoundryAuthentication authentication
    ) {
        this(testClients(properties, objectMapper, httpClient, quotaLimiters, metrics, authentication));
    }

    private FoundryClient(CapabilityClients clients) {
        this(clients.embeddings(), clients.chat(), clients.streamingChat());
    }

    public List<Double> embed(String deployment, String input, int dimensions) {
        return embeddings.embed(deployment, input, dimensions);
    }

    public List<List<Double>> embed(String deployment, List<String> inputs, int dimensions) {
        return embeddings.embedBatch(deployment, inputs, dimensions);
    }

    public String chat(String deployment, String systemPrompt, String userMessage) {
        if (userMessage == null) {
            throw new FoundryProviderException("Foundry chat user message must not be null");
        }
        var messages = new ArrayList<Map<String, Object>>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userMessage));
        var result = chatWithTools(deployment, messages, List.of(), FoundryChatOptions.defaults());
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
        return chat.generate(deployment, messages, tools, options);
    }

    public FoundryStreamResult streamChat(
            String deployment,
            List<Map<String, Object>> messages,
            FoundryChatOptions options,
            Consumer<String> deltaConsumer
    ) {
        return streamingChat.stream(deployment, messages, options, deltaConsumer);
    }

    private static CapabilityClients testClients(
            FoundryProperties properties,
            ObjectMapper objectMapper,
            HttpClient httpClient,
            FoundryQuotaLimiterRegistry quotaLimiters,
            FoundryMetrics metrics,
            FoundryAuthentication authentication
    ) {
        var transport = new FoundryHttpTransport(
                properties, objectMapper, httpClient, metrics, authentication);
        var retryPolicy = new FoundryRetryPolicy(properties);
        var errorMapper = new FoundryErrorMapper();
        var requests = new FoundryRequestExecutor(transport, retryPolicy, errorMapper, metrics);
        return new CapabilityClients(
                new FoundryEmbeddingClient(objectMapper, quotaLimiters, requests),
                new FoundryChatClient(objectMapper, quotaLimiters, requests),
                new FoundryStreamingChatClient(objectMapper, quotaLimiters, transport,
                        retryPolicy, errorMapper, metrics));
    }

    private static FoundryQuotaLimiterRegistry singleLimiterRegistry(
            FoundryProperties properties,
            FoundryQuotaLimiter quotaLimiter
    ) {
        return new FoundryQuotaLimiterRegistry(properties) {
            @Override
            public Duration acquire(
                    String deployment,
                    FoundryOperation operation,
                    int estimatedTokens
            ) {
                return quotaLimiter.acquire(estimatedTokens, operation.background(),
                        properties.getInteractiveReservedRequestsPerMinute(),
                        properties.getInteractiveReservedTokensPerMinute(), Duration.ofDays(1));
            }
        };
    }

    private static FoundryAuthentication apiKey(FoundryProperties properties) {
        return request -> {
            if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
                throw new FoundryProviderException(
                        "AZURE_AI_FOUNDRY_API_KEY must be configured for the foundry profile");
            }
            request.header("api-key", properties.getApiKey());
        };
    }

    public static class FoundryProviderException extends RuntimeException {

        private final int statusCode;
        private final Duration retryAfter;
        private final String requestId;

        public FoundryProviderException(String message) {
            this(message, null, -1, null, null);
        }

        public FoundryProviderException(String message, Throwable cause) {
            this(message, cause, -1, null, null);
        }

        public FoundryProviderException(String message, int statusCode, Duration retryAfter, String requestId) {
            this(message, null, statusCode, retryAfter, requestId);
        }

        private FoundryProviderException(
                String message,
                Throwable cause,
                int statusCode,
                Duration retryAfter,
                String requestId
        ) {
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
            return statusCode == 0 || statusCode == 408 || statusCode == 429 || statusCode == 500
                    || statusCode == 502 || statusCode == 503 || statusCode == 504;
        }

        static FoundryProviderException network(String message, Throwable cause) {
            return new FoundryProviderException(message, cause, 0, null, null);
        }
    }

    public record FoundryToolCall(String id, String name, Map<String, Object> arguments) { }

    public record FoundryChatResult(
            String content,
            List<FoundryToolCall> toolCalls,
            Map<String, Object> assistantMessage,
            ProviderMetadata metadata
    ) {
        public FoundryChatResult(
                String content,
                List<FoundryToolCall> toolCalls,
                Map<String, Object> assistantMessage
        ) {
            this(content, toolCalls, assistantMessage, ProviderMetadata.unknown());
        }
    }

    public record FoundryChatOptions(
            Integer maxCompletionTokens,
            Double temperature,
            String reasoningEffort,
            String promptCacheKey
    ) {
        public FoundryChatOptions(Integer maxCompletionTokens, Double temperature) {
            this(maxCompletionTokens, temperature, null, null);
        }

        public static FoundryChatOptions defaults() {
            return new FoundryChatOptions(null, null, null, null);
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

    public record FoundryStreamResult(String content, ProviderMetadata metadata) { }

    private record CapabilityClients(
            FoundryEmbeddingClient embeddings,
            FoundryChatClient chat,
            FoundryStreamingChatClient streamingChat
    ) { }
}

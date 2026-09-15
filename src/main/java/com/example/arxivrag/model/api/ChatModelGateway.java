package com.example.arxivrag.model.api;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Provider-neutral capability used by conversation orchestration. */
public interface ChatModelGateway {

    ChatModelResult generate(String deployment, List<Map<String, Object>> messages,
                             List<Map<String, Object>> tools, ChatModelOptions options);

    ChatModelStreamResult stream(String deployment, List<Map<String, Object>> messages,
                                 ChatModelOptions options, Consumer<String> deltaConsumer);

    record ChatModelOptions(Integer maxCompletionTokens, Double temperature,
                            String reasoningEffort, String promptCacheKey) { }

    record ToolCall(String id, String name, Map<String, Object> arguments) { }

    record ChatModelResult(String content, List<ToolCall> toolCalls,
                           Map<String, Object> assistantMessage, ProviderCallMetadata metadata) {
        public ChatModelResult(String content, List<ToolCall> toolCalls, Map<String, Object> assistantMessage) {
            this(content, toolCalls, assistantMessage, ProviderCallMetadata.unknown());
        }
    }

    record ChatModelStreamResult(String content, ProviderCallMetadata metadata) { }

    record ProviderCallMetadata(
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
        public static ProviderCallMetadata unknown() {
            return new ProviderCallMetadata(-1, -1, -1, -1, -1, null,
                    Duration.ZERO, 0, Duration.ZERO, Duration.ZERO);
        }
    }

    final class ModelProviderException extends RuntimeException {
        private final int statusCode;
        private final Duration retryAfter;
        private final String providerRequestId;

        public ModelProviderException(String message, Throwable cause, int statusCode,
                                      Duration retryAfter, String providerRequestId) {
            super(message, cause);
            this.statusCode = statusCode;
            this.retryAfter = retryAfter;
            this.providerRequestId = providerRequestId;
        }

        public ModelProviderException(String message, Throwable cause) {
            this(message, cause, 0, null, null);
        }

        public int statusCode() { return statusCode; }
        public Duration retryAfter() { return retryAfter; }
        public String providerRequestId() { return providerRequestId; }
        public boolean retryable() {
            return statusCode == 0 || statusCode == 408 || statusCode == 429 || statusCode == 500
                    || statusCode == 502 || statusCode == 503 || statusCode == 504;
        }
    }
}

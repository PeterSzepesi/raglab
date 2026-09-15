package com.example.arxivrag.ai.foundry;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

/** Shared protocol helpers with no network, quota, or retry responsibility. */
final class FoundryProtocolSupport {

    private FoundryProtocolSupport() { }

    static String writeJson(ObjectMapper mapper, Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new FoundryClient.FoundryProviderException("Could not serialize Foundry request", exception);
        }
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static int estimateTokens(String value) {
        return Math.max(1, (value == null ? 0 : value.length() + 3) / 4);
    }

    static int estimatedChatTokens(
            List<Map<String, Object>> messages,
            FoundryClient.FoundryChatOptions options
    ) {
        var promptTokens = messages.stream()
                .mapToInt(message -> estimateTokens(String.valueOf(message.getOrDefault("content", ""))))
                .sum();
        var effective = options == null ? FoundryClient.FoundryChatOptions.defaults() : options;
        return promptTokens + Math.max(0,
                effective.maxCompletionTokens() == null ? 0 : effective.maxCompletionTokens());
    }

    static void applyOptions(Map<String, Object> payload, FoundryClient.FoundryChatOptions options) {
        var effective = options == null ? FoundryClient.FoundryChatOptions.defaults() : options;
        if (effective.maxCompletionTokens() != null) {
            payload.put("max_completion_tokens", effective.maxCompletionTokens());
        }
        if (effective.temperature() != null) {
            payload.put("temperature", effective.temperature());
        }
        if (effective.reasoningEffort() != null && !effective.reasoningEffort().isBlank()) {
            payload.put("reasoning_effort", effective.reasoningEffort());
        }
        if (effective.promptCacheKey() != null && !effective.promptCacheKey().isBlank()) {
            payload.put("prompt_cache_key", effective.promptCacheKey());
        }
    }

    static FoundryClient.ProviderMetadata metadata(
            FoundryRequestExecutor.ProviderResponse response,
            Duration quotaWait
    ) {
        var usage = response.body().path("usage");
        return new FoundryClient.ProviderMetadata(
                usage.path("prompt_tokens").asInt(-1),
                usage.path("completion_tokens").asInt(-1),
                usage.path("total_tokens").asInt(-1),
                usage.path("completion_tokens_details").path("reasoning_tokens").asInt(-1),
                usage.path("prompt_tokens_details").path("cached_tokens").asInt(-1),
                FoundryErrorMapper.requestId(response.headers()), response.duration(), response.retryCount(),
                quotaWait, response.retryWait());
    }

    static String readBody(InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return "<unreadable response body>";
        }
    }
}

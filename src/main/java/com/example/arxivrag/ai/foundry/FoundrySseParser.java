package com.example.arxivrag.ai.foundry;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.function.Consumer;

final class FoundrySseParser {

    private final ObjectMapper objectMapper;
    private final Consumer<String> deltaConsumer;
    private final StringBuilder frame = new StringBuilder();
    private final StringBuilder content = new StringBuilder();
    private int promptTokens = -1;
    private int completionTokens = -1;
    private int totalTokens = -1;
    private int reasoningTokens = -1;
    private int cachedPromptTokens = -1;

    FoundrySseParser(ObjectMapper objectMapper, Consumer<String> deltaConsumer) {
        this.objectMapper = objectMapper;
        this.deltaConsumer = deltaConsumer;
    }

    void acceptLine(String line) {
        if (line.isEmpty()) {
            consumeFrame();
            return;
        }
        if (line.startsWith("data:")) {
            if (!frame.isEmpty()) {
                frame.append('\n');
            }
            frame.append(line.substring(5).stripLeading());
        }
    }

    void finish() {
        consumeFrame();
    }

    private void consumeFrame() {
        if (frame.isEmpty()) {
            return;
        }
        var data = frame.toString();
        frame.setLength(0);
        if ("[DONE]".equals(data)) {
            return;
        }
        try {
            var node = objectMapper.readTree(data);
            var delta = node.path("choices").path(0).path("delta").path("content");
            if (delta.isTextual() && !delta.asText().isEmpty()) {
                var text = delta.asText();
                content.append(text);
                deltaConsumer.accept(text);
            }
            parseUsage(node.path("usage"));
        } catch (RuntimeException exception) {
            throw new FoundryClient.FoundryProviderException("Foundry returned an invalid SSE frame", exception);
        }
    }

    private void parseUsage(JsonNode usage) {
        if (!usage.isObject()) {
            return;
        }
        promptTokens = usage.path("prompt_tokens").asInt(promptTokens);
        completionTokens = usage.path("completion_tokens").asInt(completionTokens);
        totalTokens = usage.path("total_tokens").asInt(totalTokens);
        reasoningTokens = usage.path("completion_tokens_details").path("reasoning_tokens").asInt(reasoningTokens);
        cachedPromptTokens = usage.path("prompt_tokens_details").path("cached_tokens").asInt(cachedPromptTokens);
    }

    String content() { return content.toString(); }
    int promptTokens() { return promptTokens; }
    int completionTokens() { return completionTokens; }
    int totalTokens() { return totalTokens; }
    int reasoningTokens() { return reasoningTokens; }
    int cachedPromptTokens() { return cachedPromptTokens; }
}

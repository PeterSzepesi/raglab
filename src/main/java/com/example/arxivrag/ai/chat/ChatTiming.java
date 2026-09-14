package com.example.arxivrag.ai.chat;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class ChatTiming implements com.example.arxivrag.vector.SearchStageObserver {

    private final String requestId = UUID.randomUUID().toString();
    private final long startedNanos = System.nanoTime();
    private final EnumMap<ChatStage, Long> nanos = new EnumMap<>(ChatStage.class);
    private final ChatMetrics metrics;
    private int promptTokens;
    private int completionTokens;
    private int reasoningTokens;
    private int cachedPromptTokens;
    private int foundryCalls;
    private int retryCount;
    private final java.util.ArrayList<String> providerRequestIds = new java.util.ArrayList<>();

    public ChatTiming(ChatMetrics metrics) {
        this.metrics = metrics;
    }

    public <T> T measure(ChatStage stage, Supplier<T> action) {
        var started = System.nanoTime();
        try {
            return action.get();
        } finally {
            add(stage, Duration.ofNanos(System.nanoTime() - started));
        }
    }

    @Override
    public <T> T measure(com.example.arxivrag.vector.SearchStage stage, Supplier<T> action) {
        return measure(switch (stage) {
            case QUERY_EMBEDDING -> ChatStage.QUERY_EMBEDDING;
            case VECTOR_SEARCH -> ChatStage.VECTOR_SEARCH;
            case PAPER_HYDRATION -> ChatStage.PAPER_HYDRATION;
        }, action);
    }

    public void measure(ChatStage stage, Runnable action) {
        measure(stage, () -> {
            action.run();
            return null;
        });
    }

    public synchronized void add(ChatStage stage, Duration duration) {
        if (duration == null || duration.isNegative()) {
            return;
        }
        nanos.merge(stage, duration.toNanos(), Long::sum);
        if (metrics != null) {
            metrics.recordStage(stage, duration);
        }
    }

    public String requestId() {
        return requestId;
    }

    public Duration elapsed() {
        return Duration.ofNanos(System.nanoTime() - startedNanos);
    }

    public synchronized Map<String, Long> milliseconds() {
        return nanos.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                entry -> entry.getKey().metricName(),
                entry -> Duration.ofNanos(entry.getValue()).toMillis()
        ));
    }

    public String serverTimingHeader() {
        return milliseconds().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + ";dur=" + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    public synchronized void addProviderMetadata(
            com.example.arxivrag.ai.foundry.FoundryClient.ProviderMetadata metadata) {
        if (metadata == null) {
            return;
        }
        promptTokens += Math.max(0, metadata.promptTokens());
        completionTokens += Math.max(0, metadata.completionTokens());
        reasoningTokens += Math.max(0, metadata.reasoningTokens());
        cachedPromptTokens += Math.max(0, metadata.cachedPromptTokens());
        foundryCalls++;
        retryCount += Math.max(0, metadata.retryCount());
        if (metadata.requestId() != null && !metadata.requestId().isBlank()) {
            providerRequestIds.add(metadata.requestId());
        }
    }

    public synchronized int promptTokens() { return promptTokens; }
    public synchronized int completionTokens() { return completionTokens; }
    public synchronized int reasoningTokens() { return reasoningTokens; }
    public synchronized int cachedPromptTokens() { return cachedPromptTokens; }
    public synchronized int foundryCalls() { return foundryCalls; }
    public synchronized int retryCount() { return retryCount; }
    public synchronized java.util.List<String> providerRequestIds() { return java.util.List.copyOf(providerRequestIds); }
}

package com.example.arxivrag.ai.chat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.EnumMap;
import org.springframework.stereotype.Component;

@Component
public class ChatMetrics {

    private final Counter requests;
    private final Counter failures;
    private final Counter foundryCalls;
    private final Counter toolRounds;
    private final Counter cacheHits;
    private final Counter cacheMisses;
    private final Timer latency;
    private final Timer ttft;
    private final java.util.Map<String, Counter> toolCalls;
    private final EnumMap<ChatStage, Timer> stageTimers = new EnumMap<>(ChatStage.class);
    private final DistributionSummary promptTokens;
    private final DistributionSummary completionTokens;
    private final Timer retrievalCacheLoad;

    public ChatMetrics(MeterRegistry registry) {
        requests = Counter.builder("rag.chat.requests").description("Grounded chat requests").register(registry);
        failures = Counter.builder("rag.chat.failures").description("Grounded chat failures").register(registry);
        foundryCalls = Counter.builder("rag.chat.foundry.calls").description("Foundry chat calls").register(registry);
        toolRounds = Counter.builder("rag.chat.tool.rounds").description("Agentic tool rounds").register(registry);
        cacheHits = Counter.builder("rag.chat.cache").tag("result", "hit").register(registry);
        cacheMisses = Counter.builder("rag.chat.cache").tag("result", "miss").register(registry);
        latency = Timer.builder("rag.chat.latency").description("End-to-end grounded chat latency").register(registry);
        ttft = Timer.builder("rag.chat.ttft").description("Time to first streamed answer token").register(registry);
        toolCalls = java.util.Map.of(
                RetrievalToolService.SEMANTIC_SEARCH, Counter.builder("rag.chat.tool.calls").tag("tool", RetrievalToolService.SEMANTIC_SEARCH).register(registry),
                RetrievalToolService.RELATIONAL_PAPER_SEARCH, Counter.builder("rag.chat.tool.calls").tag("tool", RetrievalToolService.RELATIONAL_PAPER_SEARCH).register(registry),
                RetrievalToolService.PAPER_LOOKUP, Counter.builder("rag.chat.tool.calls").tag("tool", RetrievalToolService.PAPER_LOOKUP).register(registry),
                "hybrid_search", Counter.builder("rag.chat.tool.calls").tag("tool", "hybrid_search").register(registry));
        for (var stage : ChatStage.values()) {
            stageTimers.put(stage, Timer.builder("rag.chat.stage.latency")
                    .tag("stage", stage.metricName()).register(registry));
        }
        promptTokens = DistributionSummary.builder("rag.chat.tokens").tag("type", "prompt").register(registry);
        completionTokens = DistributionSummary.builder("rag.chat.tokens").tag("type", "completion").register(registry);
        retrievalCacheLoad = Timer.builder("rag.chat.cache.load").register(registry);
    }

    public void requestStarted() {
        requests.increment();
    }

    public void requestCompleted(Duration duration) {
        latency.record(duration);
    }

    public void failure() {
        failures.increment();
    }

    public void foundryCall() {
        foundryCalls.increment();
    }

    public void toolRound() {
        toolRounds.increment();
    }

    public void toolCall(String name) {
        var counter = toolCalls.get(name);
        if (counter != null) {
            counter.increment();
        }
    }

    public void recordTtft(Duration duration) {
        ttft.record(duration);
    }

    public void cacheHit() {
        cacheHits.increment();
    }

    public void cacheMiss() {
        cacheMisses.increment();
    }

    public void recordStage(ChatStage stage, Duration duration) {
        stageTimers.get(stage).record(duration);
    }

    public void recordTokens(int prompt, int completion) {
        if (prompt >= 0) {
            promptTokens.record(prompt);
        }
        if (completion >= 0) {
            completionTokens.record(completion);
        }
    }

    public void recordCacheLoad(Duration duration) {
        retrievalCacheLoad.record(duration);
    }
}

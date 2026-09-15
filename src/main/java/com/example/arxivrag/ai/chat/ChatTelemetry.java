package com.example.arxivrag.ai.chat;

import com.example.arxivrag.model.api.ChatModelGateway;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

/** Central low-cardinality chat metrics and provider metadata recording. */
@Component
public class ChatTelemetry {
    private final ChatMetrics metrics;
    private final io.micrometer.observation.ObservationRegistry observations;

    public ChatTelemetry(ChatMetrics metrics) {
        this(metrics, io.micrometer.observation.ObservationRegistry.NOOP);
    }

    @Autowired
    public ChatTelemetry(ChatMetrics metrics, io.micrometer.observation.ObservationRegistry observations) {
        this.metrics = metrics;
        this.observations = observations;
    }

    public ChatTiming start() {
        metrics.requestStarted();
        return new ChatTiming(metrics);
    }

    public void complete(ChatTiming timing) { metrics.requestCompleted(timing.elapsed()); }
    public void failure() { metrics.failure(); }
    public void providerCall() { metrics.foundryCall(); }
    public void toolRound() { metrics.toolRound(); }
    public void toolCall(String name) { metrics.toolCall(name); }
    public void firstToken(Duration duration) { metrics.recordTtft(duration); }

    public io.micrometer.observation.Observation operation(String route) {
        return io.micrometer.observation.Observation.createNotStarted("rag.chat.operation", observations)
                .lowCardinalityKeyValue("route", route == null ? "unknown" : route);
    }

    public void providerMetadata(ChatModelGateway.ProviderCallMetadata metadata, ChatTiming timing) {
        if (metadata == null) return;
        timing.add(ChatStage.QUOTA_WAIT, metadata.quotaWait());
        timing.add(ChatStage.RETRY_BACKOFF, metadata.retryWait());
        timing.addProviderMetadata(metadata);
        metrics.recordTokens(metadata.promptTokens(), metadata.completionTokens());
    }
}

package com.example.arxivrag.ai.foundry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;

@Component
public class FoundryMetrics {

    private final EnumMap<FoundryOperation, Counter> requests = new EnumMap<>(FoundryOperation.class);
    private final EnumMap<FoundryOperation, Timer> latency = new EnumMap<>(FoundryOperation.class);
    private final EnumMap<FoundryOperation, Timer> quotaWait = new EnumMap<>(FoundryOperation.class);
    private final MeterRegistry registry;

    public FoundryMetrics(MeterRegistry registry) {
        this.registry = registry;
        for (var operation : FoundryOperation.values()) {
            requests.put(operation, Counter.builder("rag.foundry.requests")
                    .tag("operation", operation.metricName()).register(registry));
            latency.put(operation, Timer.builder("rag.foundry.latency")
                    .tag("operation", operation.metricName()).register(registry));
            quotaWait.put(operation, Timer.builder("rag.foundry.quota.wait")
                    .tag("operation", operation.metricName()).register(registry));
        }
    }

    public void request(FoundryOperation operation, Duration duration) {
        requests.get(operation).increment();
        latency.get(operation).record(duration);
    }

    public void quotaWait(FoundryOperation operation, Duration duration) {
        quotaWait.get(operation).record(duration);
    }

    public void retry(FoundryOperation operation, int statusCode, Duration delay) {
        Counter.builder("rag.foundry.retries")
                .tag("operation", operation.metricName())
                .tag("status", retryStatus(statusCode))
                .register(registry)
                .increment();
        Timer.builder("rag.foundry.retry.backoff")
                .tag("operation", operation.metricName())
                .register(registry)
                .record(delay);
    }

    private static String retryStatus(int statusCode) {
        return switch (statusCode) {
            case 408, 429, 500, 502, 503, 504 -> Integer.toString(statusCode);
            default -> "other";
        };
    }
}

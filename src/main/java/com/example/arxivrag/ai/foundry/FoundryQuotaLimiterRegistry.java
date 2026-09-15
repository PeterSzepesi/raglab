package com.example.arxivrag.ai.foundry;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("foundry")
public class FoundryQuotaLimiterRegistry {

    private static final Logger logger = LoggerFactory.getLogger(FoundryQuotaLimiterRegistry.class);

    private final FoundryProperties properties;
    private final FoundryMetrics metrics;
    private final ConcurrentHashMap<String, FoundryQuotaLimiter> limiters = new ConcurrentHashMap<>();
    private final java.util.EnumMap<FoundryOperation, AtomicInteger> waiting =
            new java.util.EnumMap<>(FoundryOperation.class);

    @Autowired
    public FoundryQuotaLimiterRegistry(FoundryProperties properties, FoundryMetrics metrics) {
        this.properties = properties;
        this.metrics = metrics;
        initializeQueues();
    }

    FoundryQuotaLimiterRegistry(FoundryProperties properties) {
        this.properties = properties;
        this.metrics = null;
        initializeQueues();
    }

    public Duration acquire(String deployment, FoundryOperation operation, int estimatedTokens) {
        var limiter = limiters.computeIfAbsent(deployment, ignored -> new FoundryQuotaLimiter(
                maxRequests(operation), maxTokens(operation)));
        var started = System.nanoTime();
        var queue = waiting.get(operation);
        queue.incrementAndGet();
        try {
            return limiter.acquire(
                    estimatedTokens,
                    operation.background(),
                    properties.getInteractiveReservedRequestsPerMinute(),
                    properties.getInteractiveReservedTokensPerMinute(),
                    maxWait(operation)
            );
        } catch (FoundryClient.FoundryProviderException failure) {
            if (failure.statusCode() == 429 && metrics != null) metrics.capacityRejected(operation);
            throw failure;
        } finally {
            queue.decrementAndGet();
            var wait = Duration.ofNanos(System.nanoTime() - started);
            if (metrics != null) {
                metrics.quotaWait(operation, wait);
            }
            if (wait.compareTo(Duration.ofSeconds(1)) > 0) {
                logger.warn("Foundry local quota wait operation={} deployment={} duration={}",
                        operation.metricName(), deployment, wait);
            }
        }
    }

    public CapacitySnapshot capacity() {
        return new CapacitySnapshot(waiting.get(FoundryOperation.CHAT).get(),
                waiting.get(FoundryOperation.QUERY_EMBEDDING).get(),
                waiting.get(FoundryOperation.INGESTION_EMBEDDING).get(),
                properties.getInteractiveReservedRequestsPerMinute(),
                properties.getInteractiveReservedTokensPerMinute());
    }

    private void initializeQueues() {
        for (var operation : FoundryOperation.values()) {
            var value = new AtomicInteger();
            waiting.put(operation, value);
            if (metrics != null) metrics.registerQueueDepth(operation, value);
        }
    }

    public record CapacitySnapshot(int chatWaiting, int queryEmbeddingWaiting, int bulkEmbeddingWaiting,
                                   int reservedInteractiveRequestsPerMinute,
                                   int reservedInteractiveTokensPerMinute) {
        public static CapacitySnapshot offline() {
            return new CapacitySnapshot(0, 0, 0, 0, 0);
        }
    }

    private int maxRequests(FoundryOperation operation) {
        return operation == FoundryOperation.CHAT
                ? properties.getChatMaxRequestsPerMinute()
                : properties.getEmbeddingMaxRequestsPerMinute();
    }

    private int maxTokens(FoundryOperation operation) {
        return operation == FoundryOperation.CHAT
                ? properties.getChatMaxTokensPerMinute()
                : properties.getEmbeddingMaxTokensPerMinute();
    }

    private Duration maxWait(FoundryOperation operation) {
        return switch (operation) {
            case CHAT -> properties.getChatMaxQuotaWait();
            case QUERY_EMBEDDING -> properties.getQueryEmbeddingMaxQuotaWait();
            case INGESTION_EMBEDDING -> properties.getIngestionEmbeddingMaxQuotaWait();
        };
    }
}

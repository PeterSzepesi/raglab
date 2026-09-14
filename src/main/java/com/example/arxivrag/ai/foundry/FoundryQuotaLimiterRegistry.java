package com.example.arxivrag.ai.foundry;

import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Profile("foundry")
public class FoundryQuotaLimiterRegistry {

    private static final Logger logger = LoggerFactory.getLogger(FoundryQuotaLimiterRegistry.class);

    private final FoundryProperties properties;
    private final FoundryMetrics metrics;
    private final ConcurrentHashMap<String, FoundryQuotaLimiter> limiters = new ConcurrentHashMap<>();

    @Autowired
    public FoundryQuotaLimiterRegistry(FoundryProperties properties, FoundryMetrics metrics) {
        this.properties = properties;
        this.metrics = metrics;
    }

    FoundryQuotaLimiterRegistry(FoundryProperties properties) {
        this.properties = properties;
        this.metrics = null;
    }

    public Duration acquire(String deployment, FoundryOperation operation, int estimatedTokens) {
        var limiter = limiters.computeIfAbsent(deployment, ignored -> new FoundryQuotaLimiter(
                maxRequests(operation), maxTokens(operation)));
        var started = System.nanoTime();
        try {
            return limiter.acquire(
                    estimatedTokens,
                    operation.background(),
                    properties.getInteractiveReservedRequestsPerMinute(),
                    properties.getInteractiveReservedTokensPerMinute(),
                    maxWait(operation)
            );
        } finally {
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

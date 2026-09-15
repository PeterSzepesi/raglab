package com.example.arxivrag.ai.foundry;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Retry budget and bounded exponential backoff; deliberately contains no I/O. */
@Component
@Profile("foundry")
final class FoundryRetryPolicy {

    private final FoundryProperties properties;

    FoundryRetryPolicy(FoundryProperties properties) {
        this.properties = properties;
    }

    int maxRetries(FoundryOperation operation) {
        return Math.max(0, switch (operation) {
            case CHAT -> properties.getChatMaxRetries();
            case QUERY_EMBEDDING -> properties.getQueryEmbeddingMaxRetries();
            case INGESTION_EMBEDDING -> properties.getMaxRetries();
        });
    }

    Duration delay(FoundryClient.FoundryProviderException failure, int attempt) {
        return failure.retryAfter() == null ? jitter(backoff(attempt)) : failure.retryAfter();
    }

    boolean withinRequestDeadline(long startedNanos, Duration nextDelay) {
        var deadline = properties.getRequestTimeout();
        if (deadline == null || deadline.isZero() || deadline.isNegative()) return false;
        var elapsed = Duration.ofNanos(Math.max(0L, System.nanoTime() - startedNanos));
        return elapsed.plus(nextDelay).compareTo(deadline) < 0;
    }

    void await(Duration duration) {
        try {
            Thread.sleep(Math.max(1L, duration.toMillis()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new FoundryClient.FoundryProviderException("Foundry retry wait was interrupted", exception);
        }
    }

    private Duration backoff(int attempt) {
        var initial = positive(properties.getRetryInitialBackoff(), Duration.ofSeconds(1));
        var maximum = positive(properties.getRetryMaxBackoff(), Duration.ofSeconds(10));
        var multiplier = 1L << Math.min(attempt, 20);
        var candidate = initial.multipliedBy(multiplier);
        return candidate.compareTo(maximum) > 0 ? maximum : candidate;
    }

    private Duration jitter(Duration value) {
        var maximum = positive(properties.getRetryMaxBackoff(), Duration.ofSeconds(10));
        var factor = ThreadLocalRandom.current().nextDouble(0.75d, 1.25d);
        var millis = Math.max(1L, Math.round(value.toMillis() * factor));
        var jittered = Duration.ofMillis(millis);
        return jittered.compareTo(maximum) > 0 ? maximum : jittered;
    }

    private static Duration positive(Duration value, Duration fallback) {
        return value == null || value.isNegative() || value.isZero() ? fallback : value;
    }
}

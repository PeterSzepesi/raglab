package com.example.arxivrag.ai.foundry;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Small single-process sliding-window limiter for the provider quotas. It is
 * deliberately conservative: estimated tokens are used before the request,
 * then the provider's actual response remains the source of truth for errors.
 */
public final class FoundryQuotaLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final int maxRequestsPerMinute;
    private final int maxTokensPerMinute;
    private final Deque<Usage> usage = new ArrayDeque<>();

    public FoundryQuotaLimiter(int maxRequestsPerMinute, int maxTokensPerMinute) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
        this.maxTokensPerMinute = maxTokensPerMinute;
    }

    public void acquire(int estimatedTokens) {
        var boundedTokens = Math.max(1, estimatedTokens);
        if (maxRequestsPerMinute <= 0 && maxTokensPerMinute <= 0) {
            return;
        }

        boolean interrupted = false;
        try {
            while (true) {
                var now = Instant.now();
                synchronized (this) {
                    removeExpired(now);
                    var requestLimitReached = maxRequestsPerMinute > 0 && usage.size() >= maxRequestsPerMinute;
                    var tokensUsed = usage.stream().mapToLong(Usage::tokens).sum();
                    var tokenLimitReached = maxTokensPerMinute > 0
                            && boundedTokens <= maxTokensPerMinute
                            && tokensUsed + boundedTokens > maxTokensPerMinute;
                    if (!requestLimitReached && !tokenLimitReached) {
                        usage.addLast(new Usage(now, boundedTokens));
                        return;
                    }

                    var oldest = usage.peekFirst();
                    var waitMillis = oldest == null
                            ? 100L
                            : Math.max(100L, Duration.between(now, oldest.timestamp().plus(WINDOW)).toMillis());
                    try {
                        this.wait(Math.min(waitMillis, 5000L));
                    } catch (InterruptedException exception) {
                        interrupted = true;
                        throw new FoundryClient.FoundryProviderException("Foundry quota wait was interrupted", exception);
                    }
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void removeExpired(Instant now) {
        while (!usage.isEmpty() && usage.peekFirst().timestamp().plus(WINDOW).isBefore(now)) {
            usage.removeFirst();
        }
    }

    private record Usage(Instant timestamp, int tokens) {
    }
}

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
        acquire(estimatedTokens, false, 0, 0, Duration.ofDays(1));
    }

    public Duration acquire(
            int estimatedTokens,
            boolean background,
            int reservedRequests,
            int reservedTokens,
            Duration maxWait
    ) {
        var boundedTokens = Math.max(1, estimatedTokens);
        if (maxRequestsPerMinute <= 0 && maxTokensPerMinute <= 0) {
            return Duration.ZERO;
        }

        var started = Instant.now();
        var deadline = started.plus(validMaxWait(maxWait));
        var requestLimit = background && maxRequestsPerMinute > 0
                ? Math.max(1, maxRequestsPerMinute - Math.max(0, reservedRequests))
                : maxRequestsPerMinute;
        var tokenLimit = background && maxTokensPerMinute > 0
                ? Math.max(1, maxTokensPerMinute - Math.max(0, reservedTokens))
                : maxTokensPerMinute;

        boolean interrupted = false;
        try {
            while (true) {
                var now = Instant.now();
                synchronized (this) {
                    removeExpired(now);
                    var requestLimitReached = requestLimit > 0 && usage.size() >= requestLimit;
                    var tokensUsed = usage.stream().mapToLong(Usage::tokens).sum();
                    var tokenLimitReached = tokenLimit > 0
                            && boundedTokens <= tokenLimit
                            && tokensUsed + boundedTokens > tokenLimit;
                    if (!requestLimitReached && !tokenLimitReached) {
                        usage.addLast(new Usage(now, boundedTokens));
                        return Duration.between(started, now);
                    }

                    var oldest = usage.peekFirst();
                    var waitMillis = oldest == null
                            ? 100L
                            : Math.max(100L, Duration.between(now, oldest.timestamp().plus(WINDOW)).toMillis());
                    var remainingMillis = Duration.between(now, deadline).toMillis();
                    if (remainingMillis <= 0) {
                        throw new FoundryClient.FoundryProviderException(
                                "Local Foundry quota wait exceeded " + validMaxWait(maxWait), 429, null, null);
                    }
                    try {
                        this.wait(Math.min(Math.min(waitMillis, remainingMillis), 1000L));
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

    private static Duration validMaxWait(Duration maxWait) {
        return maxWait == null || maxWait.isNegative() || maxWait.isZero()
                ? Duration.ofSeconds(1)
                : maxWait;
    }

    private void removeExpired(Instant now) {
        while (!usage.isEmpty() && usage.peekFirst().timestamp().plus(WINDOW).isBefore(now)) {
            usage.removeFirst();
        }
    }

    private record Usage(Instant timestamp, int tokens) {
    }
}

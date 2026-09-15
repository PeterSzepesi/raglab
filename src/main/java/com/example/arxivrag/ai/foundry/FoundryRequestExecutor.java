package com.example.arxivrag.ai.foundry;

import java.net.http.HttpHeaders;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Owns the single retry layer for idempotent Foundry JSON requests. */
@Component
@Profile("foundry")
final class FoundryRequestExecutor {

    private static final Logger logger = LoggerFactory.getLogger(FoundryRequestExecutor.class);

    private final FoundryHttpTransport transport;
    private final FoundryRetryPolicy retryPolicy;
    private final FoundryErrorMapper errorMapper;
    private final FoundryMetrics metrics;

    FoundryRequestExecutor(
            FoundryHttpTransport transport,
            FoundryRetryPolicy retryPolicy,
            FoundryErrorMapper errorMapper,
            FoundryMetrics metrics
    ) {
        this.transport = transport;
        this.retryPolicy = retryPolicy;
        this.errorMapper = errorMapper;
        this.metrics = metrics;
    }

    ProviderResponse post(String path, String body, FoundryOperation operation) {
        var startedNanos = System.nanoTime();
        var attempt = 0;
        var retryWait = Duration.ZERO;
        while (true) {
            try {
                var response = transport.post(path, body, operation);
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw errorMapper.fromHttp(response.statusCode(), response.headers(), response.rawBody());
                }
                return new ProviderResponse(response.body(), response.headers(), response.duration(),
                        attempt, retryWait);
            } catch (FoundryClient.FoundryProviderException failure) {
                if (!failure.retryable() || attempt >= retryPolicy.maxRetries(operation)) {
                    throw failure;
                }
                var delay = retryPolicy.delay(failure, attempt);
                if (!retryPolicy.withinRequestDeadline(startedNanos, delay)) {
                    throw failure;
                }
                retryWait = retryWait.plus(delay);
                if (metrics != null) metrics.retry(operation, failure.statusCode(), delay);
                logger.warn("Retrying Foundry {} after status={} delay={} attempt={} requestId={}",
                        operation.metricName(), failure.statusCode(), delay, attempt + 1, failure.requestId());
                retryPolicy.await(delay);
                attempt++;
            }
        }
    }

    record ProviderResponse(
            JsonNode body,
            HttpHeaders headers,
            Duration duration,
            int retryCount,
            Duration retryWait
    ) { }
}

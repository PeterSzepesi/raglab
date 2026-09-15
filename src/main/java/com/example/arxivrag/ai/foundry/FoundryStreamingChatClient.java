package com.example.arxivrag.ai.foundry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Owns the streaming Microsoft AI Foundry chat protocol and SSE lifecycle. */
@Component
@Profile("foundry")
public class FoundryStreamingChatClient {

    private static final Logger logger = LoggerFactory.getLogger(FoundryStreamingChatClient.class);

    private final ObjectMapper objectMapper;
    private final FoundryQuotaLimiterRegistry quotaLimiters;
    private final FoundryHttpTransport transport;
    private final FoundryRetryPolicy retryPolicy;
    private final FoundryErrorMapper errorMapper;
    private final FoundryMetrics metrics;

    public FoundryStreamingChatClient(
            ObjectMapper objectMapper,
            FoundryQuotaLimiterRegistry quotaLimiters,
            FoundryHttpTransport transport,
            FoundryRetryPolicy retryPolicy,
            FoundryErrorMapper errorMapper,
            FoundryMetrics metrics
    ) {
        this.objectMapper = objectMapper;
        this.quotaLimiters = quotaLimiters;
        this.transport = transport;
        this.retryPolicy = retryPolicy;
        this.errorMapper = errorMapper;
        this.metrics = metrics;
    }

    public FoundryClient.FoundryStreamResult stream(
            String deployment,
            List<Map<String, Object>> messages,
            FoundryClient.FoundryChatOptions options,
            Consumer<String> deltaConsumer
    ) {
        if (deployment == null || deployment.isBlank()) {
            throw new FoundryClient.FoundryProviderException("Foundry chat deployment must not be blank");
        }
        if (messages == null || messages.isEmpty()) {
            throw new FoundryClient.FoundryProviderException("Foundry chat requires at least one message");
        }
        if (deltaConsumer == null) {
            throw new IllegalArgumentException("deltaConsumer must not be null");
        }

        var payload = new HashMap<String, Object>();
        payload.put("model", deployment);
        payload.put("messages", messages);
        payload.put("n", 1);
        payload.put("stream", true);
        payload.put("stream_options", Map.of("include_usage", true));
        FoundryProtocolSupport.applyOptions(payload, options);

        var quotaWait = quotaLimiters.acquire(deployment, FoundryOperation.CHAT,
                FoundryProtocolSupport.estimatedChatTokens(messages, options));
        var body = FoundryProtocolSupport.writeJson(objectMapper, payload);
        var path = "/openai/deployments/" + FoundryProtocolSupport.encode(deployment) + "/chat/completions";
        var retryWait = Duration.ZERO;
        var providerDuration = Duration.ZERO;
        var attempt = 0;
        var startedNanos = System.nanoTime();
        while (true) {
            var response = transport.stream(path, body, FoundryOperation.CHAT);
            providerDuration = providerDuration.plus(response.duration());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                var failure = errorMapper.fromHttp(response.statusCode(), response.headers(),
                        FoundryProtocolSupport.readBody(response.body()));
                if (!failure.retryable() || attempt >= retryPolicy.maxRetries(FoundryOperation.CHAT)) {
                    throw failure;
                }
                var delay = retryPolicy.delay(failure, attempt);
                if (!retryPolicy.withinRequestDeadline(startedNanos, delay)) {
                    throw failure;
                }
                retryWait = retryWait.plus(delay);
                if (metrics != null) {
                    metrics.retry(FoundryOperation.CHAT, failure.statusCode(), delay);
                }
                logger.warn("Retrying Foundry streaming chat after status={} delay={} attempt={} requestId={}",
                        failure.statusCode(), delay, attempt + 1, failure.requestId());
                retryPolicy.await(delay);
                attempt++;
                continue;
            }

            var parser = new FoundrySseParser(objectMapper, deltaConsumer);
            try (var reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException("streaming request cancelled");
                    }
                    parser.acceptLine(line);
                }
                parser.finish();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new FoundryClient.FoundryProviderException(
                        "Foundry streaming request was interrupted", exception);
            } catch (IOException exception) {
                throw new FoundryClient.FoundryProviderException(
                        "Could not stream Microsoft AI Foundry response", exception);
            }
            if (parser.content().isBlank()) {
                throw new FoundryClient.FoundryProviderException(
                        "Foundry streaming response did not contain message content");
            }
            return new FoundryClient.FoundryStreamResult(parser.content(),
                    new FoundryClient.ProviderMetadata(
                            parser.promptTokens(), parser.completionTokens(), parser.totalTokens(),
                            parser.reasoningTokens(), parser.cachedPromptTokens(),
                            FoundryErrorMapper.requestId(response.headers()), providerDuration, attempt,
                            quotaWait, retryWait));
        }
    }
}

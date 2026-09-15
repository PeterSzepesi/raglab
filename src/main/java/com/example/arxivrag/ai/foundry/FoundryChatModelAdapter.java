package com.example.arxivrag.ai.foundry;

import com.example.arxivrag.model.api.ChatModelGateway;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("foundry")
public class FoundryChatModelAdapter implements ChatModelGateway {

    private final FoundryChatClient client;
    private final FoundryStreamingChatClient streamingClient;
    private final ProviderCircuitBreaker circuitBreaker;
    private final FoundryOperationalState operationalState;

    public FoundryChatModelAdapter(FoundryChatClient client, FoundryStreamingChatClient streamingClient,
                                   ProviderCircuitBreaker circuitBreaker,
                                   FoundryOperationalState operationalState) {
        this.client = client;
        this.streamingClient = streamingClient;
        this.circuitBreaker = circuitBreaker;
        this.operationalState = operationalState;
    }

    @Override
    public ChatModelResult generate(String deployment, List<Map<String, Object>> messages,
                                    List<Map<String, Object>> tools, ChatModelOptions options) {
        return guarded(deployment, () -> {
            var result = client.generate(deployment, messages, tools, foundryOptions(options));
            return new ChatModelResult(result.content(), result.toolCalls().stream()
                    .map(call -> new ToolCall(call.id(), call.name(), call.arguments())).toList(),
                    result.assistantMessage(), metadata(result.metadata()));
        });
    }

    @Override
    public ChatModelStreamResult stream(String deployment, List<Map<String, Object>> messages,
                                        ChatModelOptions options,
                                        java.util.function.Consumer<String> deltaConsumer) {
        return guarded(deployment, () -> {
            var result = streamingClient.stream(deployment, messages, foundryOptions(options), deltaConsumer);
            return new ChatModelStreamResult(result.content(), metadata(result.metadata()));
        });
    }

    private static FoundryClient.FoundryChatOptions foundryOptions(ChatModelOptions options) {
        return new FoundryClient.FoundryChatOptions(options.maxCompletionTokens(), options.temperature(),
                options.reasoningEffort(), options.promptCacheKey());
    }

    private static ProviderCallMetadata metadata(FoundryClient.ProviderMetadata value) {
        return new ProviderCallMetadata(value.promptTokens(), value.completionTokens(), value.totalTokens(),
                value.reasoningTokens(), value.cachedPromptTokens(), value.requestId(), value.requestDuration(),
                value.retryCount(), value.quotaWait(), value.retryWait());
    }

    private <T> T guarded(String deployment, Supplier<T> action) {
        try (var permit = circuitBreaker.acquire(deployment, FoundryOperation.CHAT)) {
            try {
                var result = action.get();
                permit.success();
                operationalState.success("chat", requestId(result));
                return result;
            } catch (FoundryClient.FoundryProviderException exception) {
                if (exception.retryable()) permit.failure(); else permit.success();
                operationalState.failure("chat", exception.statusCode(), exception.requestId());
                throw new ModelProviderException("Model provider could not complete the request", exception,
                        exception.statusCode(), exception.retryAfter(), exception.requestId());
            }
        } catch (ProviderCircuitBreaker.CircuitOpenException exception) {
            operationalState.failure("chat", 503, null);
            throw new ModelProviderException(exception.getMessage(), exception, 503, null, null);
        }
    }

    private static String requestId(Object result) {
        if (result instanceof ChatModelResult value) {
            return value.metadata().requestId();
        }
        if (result instanceof ChatModelStreamResult value) {
            return value.metadata().requestId();
        }
        return null;
    }
}

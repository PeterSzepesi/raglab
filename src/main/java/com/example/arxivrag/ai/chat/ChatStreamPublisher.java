package com.example.arxivrag.ai.chat;

import com.example.arxivrag.config.InteractiveAdmissionController;
import com.example.arxivrag.model.api.ChatModelGateway;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Translates application-level chat events into the stable HTTP SSE contract. */
@Component
@Profile("foundry")
public class ChatStreamPublisher {

    private final ChatUseCase chat;
    private final ChatProperties properties;
    private final ExecutorService executor;

    public ChatStreamPublisher(
            ChatUseCase chat,
            ChatProperties properties,
            @Qualifier("chatStreamExecutor") ExecutorService executor
    ) {
        this.chat = chat;
        this.properties = properties;
        this.executor = executor;
    }

    public SseEmitter publish(
            ChatRequest request,
            String requestId,
            InteractiveAdmissionController.Permit permit
    ) {
        var emitter = new SseEmitter(properties.getStreamingTimeout().toMillis());
        var future = new AtomicReference<Future<?>>();
        var terminal = new AtomicBoolean();
        try {
            future.set(executor.submit(() -> run(request, requestId, permit, emitter, terminal)));
        } catch (RuntimeException exception) {
            permit.close();
            throw exception;
        }
        emitter.onTimeout(() -> cancel(future));
        emitter.onError(ignored -> cancel(future));
        emitter.onCompletion(() -> cancel(future));
        return emitter;
    }

    private void run(
            ChatRequest request,
            String requestId,
            InteractiveAdmissionController.Permit permit,
            SseEmitter emitter,
            AtomicBoolean terminal
    ) {
        org.slf4j.MDC.put("requestId", requestId);
        try {
            send(emitter, "request", Map.of("requestId", requestId));
            chat.stream(request, new ChatStreamListener() {
                @Override
                public void retrievalStarted() {
                    send(emitter, "retrieval", Map.of("status", "started"));
                }

                @Override
                public void citations(java.util.List<ChatCitation> citations) {
                    send(emitter, "citations", citations);
                }

                @Override
                public void delta(String text) {
                    send(emitter, "delta", Map.of("text", text));
                }

                @Override
                public void complete(GroundedChatResponse response, ChatTiming timing) {
                    if (terminal.compareAndSet(false, true)) {
                        send(emitter, "metrics", metrics(response, timing));
                        send(emitter, "complete", completion(response, timing));
                        emitter.complete();
                    }
                }

                @Override
                public void error(Throwable failure) {
                    terminalError(emitter, terminal, requestId, failure);
                }
            });
        } catch (RuntimeException failure) {
            terminalError(emitter, terminal, requestId, failure);
        } finally {
            org.slf4j.MDC.remove("requestId");
            permit.close();
        }
    }

    private Map<String, Object> completion(GroundedChatResponse response, ChatTiming timing) {
        return Map.of(
                "answer", response.answer(),
                "model", response.model(),
                "toolsUsed", response.toolsUsed(),
                "groundingStatus", response.groundingStatus(),
                "cacheStatus", response.cacheStatus(),
                "requestId", timing.requestId(),
                "timings", properties.isDebugTimings() ? timing.milliseconds() : Map.of()
        );
    }

    private static Map<String, Object> metrics(GroundedChatResponse response, ChatTiming timing) {
        return Map.of(
                "requestId", timing.requestId(),
                "elapsedMilliseconds", timing.elapsed().toMillis(),
                "timings", timing.milliseconds(),
                "promptTokens", timing.promptTokens(),
                "completionTokens", timing.completionTokens(),
                "reasoningTokens", timing.reasoningTokens(),
                "cachedPromptTokens", timing.cachedPromptTokens(),
                "cacheStatus", response.cacheStatus(),
                "groundingStatus", response.groundingStatus()
        );
    }

    private static void terminalError(
            SseEmitter emitter,
            AtomicBoolean terminal,
            String requestId,
            Throwable failure
    ) {
        if (terminal.compareAndSet(false, true)) {
            send(emitter, "error", Map.of(
                    "message", safeMessage(failure),
                    "retryable", retryable(failure),
                    "requestId", requestId));
            emitter.complete();
        }
    }

    private static String safeMessage(Throwable failure) {
        if (failure instanceof ChatModelGateway.ModelProviderException) {
            return "Microsoft AI Foundry could not complete the request";
        }
        var message = failure.getMessage();
        return message == null || message.isBlank() ? "Chat stream failed" : message;
    }

    private static void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException exception) {
            throw new ChatStreamDisconnectedException(exception);
        }
    }

    private static boolean retryable(Throwable failure) {
        return failure instanceof ChatModelGateway.ModelProviderException provider && provider.retryable();
    }

    private static void cancel(AtomicReference<Future<?>> future) {
        var task = future.get();
        if (task != null && !task.isDone()) {
            task.cancel(true);
        }
    }

    private static final class ChatStreamDisconnectedException extends RuntimeException {
        private ChatStreamDisconnectedException(Throwable cause) {
            super(cause);
        }
    }
}

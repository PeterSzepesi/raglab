package com.example.arxivrag.ai.chat;

import com.example.arxivrag.ai.foundry.FoundryClient;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@Profile("foundry")
@RequestMapping("/api/chat")
public class ChatController {

    private final GroundedChatService groundedChatService;
    private final ChatProperties properties;
    private final java.util.concurrent.ExecutorService streamExecutor =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("chat-stream-", 0).factory());

    @Autowired
    public ChatController(GroundedChatService groundedChatService, ChatProperties properties) {
        this.groundedChatService = groundedChatService;
        this.properties = properties;
    }

    /** Compatibility constructor for focused controller tests. */
    public ChatController(GroundedChatService groundedChatService) {
        this(groundedChatService, new ChatProperties());
    }

    @PostMapping
    public ResponseEntity<GroundedChatResponse> chat(@RequestBody ChatRequest request) {
        var execution = groundedChatService.chatDetailed(request);
        var headers = new HttpHeaders();
        headers.set("X-Request-ID", execution.timing().requestId());
        var serverTiming = execution.timing().serverTimingHeader();
        if (!serverTiming.isBlank()) {
            headers.set("Server-Timing", serverTiming);
        }
        if (properties.isDebugTimings()) {
            headers.set("X-Chat-Timings", execution.timing().milliseconds().toString());
        }
        return new ResponseEntity<>(execution.response(), headers, HttpStatus.OK);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest request) {
        var emitter = new SseEmitter(properties.getStreamingTimeout().toMillis());
        var future = new AtomicReference<Future<?>>();
        var terminal = new AtomicBoolean();
        future.set(streamExecutor.submit(() -> {
            try {
                groundedChatService.stream(request, new ChatStreamListener() {
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
                            send(emitter, "complete", Map.of(
                                    "answer", response.answer(),
                                    "model", response.model(),
                                    "toolsUsed", response.toolsUsed(),
                                    "requestId", timing.requestId(),
                                    "timings", properties.isDebugTimings() ? timing.milliseconds() : Map.of()
                            ));
                            emitter.complete();
                        }
                    }

                    @Override
                    public void error(Throwable failure) {
                        if (terminal.compareAndSet(false, true)) {
                            send(emitter, "error", Map.of(
                                    "message", failure.getMessage() == null ? "Chat stream failed" : failure.getMessage(),
                                    "retryable", retryable(failure)));
                            emitter.complete();
                        }
                    }
                });
            } catch (RuntimeException exception) {
                if (terminal.compareAndSet(false, true)) {
                    send(emitter, "error", Map.of("message", exception.getMessage(), "retryable", retryable(exception)));
                    emitter.complete();
                }
            }
        }));
        emitter.onTimeout(() -> cancel(future));
        emitter.onError(ignored -> cancel(future));
        emitter.onCompletion(() -> cancel(future));
        return emitter;
    }

    private static void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException exception) {
            throw new ChatStreamDisconnectedException(exception);
        }
    }

    private static boolean retryable(Throwable failure) {
        return failure instanceof FoundryClient.FoundryProviderException provider && provider.retryable();
    }

    private static void cancel(AtomicReference<Future<?>> future) {
        var task = future.get();
        if (task != null && !task.isDone()) {
            task.cancel(true);
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(FoundryClient.FoundryProviderException.class)
    public ResponseEntity<Map<String, String>> handleProviderFailure(FoundryClient.FoundryProviderException exception) {
        var status = exception.statusCode() == 429 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(Map.of("error", exception.getMessage()));
    }

    @PreDestroy
    void close() {
        streamExecutor.shutdownNow();
    }

    private static final class ChatStreamDisconnectedException extends RuntimeException {
        private ChatStreamDisconnectedException(Throwable cause) { super(cause); }
    }
}

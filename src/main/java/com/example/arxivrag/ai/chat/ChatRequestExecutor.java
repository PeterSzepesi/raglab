package com.example.arxivrag.ai.chat;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Applies one end-to-end deadline to the synchronous chat use case. */
@Component
@Profile("foundry")
public class ChatRequestExecutor {

    private final ChatUseCase chat;
    private final ChatProperties properties;
    private final ExecutorService executor;

    public ChatRequestExecutor(
            ChatUseCase chat,
            ChatProperties properties,
            @Qualifier("chatStreamExecutor") ExecutorService executor
    ) {
        this.chat = chat;
        this.properties = properties;
        this.executor = executor;
    }

    public ChatExecution execute(ChatRequest request) {
        var requestId = MDC.get("requestId");
        var future = executor.submit(() -> {
            if (requestId != null) MDC.put("requestId", requestId);
            try {
                return chat.chatDetailed(request);
            } finally {
                MDC.remove("requestId");
            }
        });
        try {
            return future.get(properties.getRequestDeadline().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new ChatDeadlineExceededException("Chat request exceeded its end-to-end deadline", exception);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ChatDeadlineExceededException("Chat request was interrupted", exception);
        } catch (ExecutionException exception) {
            var cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("Chat request failed", cause);
        }
    }
}

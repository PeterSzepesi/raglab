package com.example.arxivrag.ai.foundry;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/** Passive provider health derived from real application traffic. */
@Component
public class FoundryOperationalState {

    private final AtomicReference<OperationEvent> lastSuccess = new AtomicReference<>();
    private final AtomicReference<OperationEvent> lastFailure = new AtomicReference<>();

    public void success(String operation, String requestId) {
        lastSuccess.set(new OperationEvent(operation, Instant.now(), requestId, null));
    }

    public void failure(String operation, Integer statusCode, String requestId) {
        lastFailure.set(new OperationEvent(operation, Instant.now(), requestId, statusCode));
    }

    public Snapshot snapshot() {
        return new Snapshot(lastSuccess.get(), lastFailure.get());
    }

    public record Snapshot(OperationEvent lastSuccess, OperationEvent lastFailure) { }

    public record OperationEvent(String operation, Instant at, String requestId, Integer statusCode) { }
}

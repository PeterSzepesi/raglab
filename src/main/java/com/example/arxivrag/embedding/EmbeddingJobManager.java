package com.example.arxivrag.embedding;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/** Owns the one background embedding task used by the local administrative API. */
@Component
public class EmbeddingJobManager implements AutoCloseable {

    private final EmbeddingIngestionService ingestionService;
    private final ExecutorService executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();

    private Future<?> activeTask;
    private UUID operationId;

    public EmbeddingJobManager(EmbeddingIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    public synchronized UUID start(EmbeddingIngestionRequest request) {
        if (activeTask != null && !activeTask.isDone()) {
            throw new IllegalStateException("An embedding job is already running");
        }
        operationId = UUID.randomUUID();
        activeTask = executor.submit(() -> ingestionService.ingest(request));
        return operationId;
    }

    public synchronized boolean cancel() {
        return activeTask != null && !activeTask.isDone() && activeTask.cancel(true);
    }

    public synchronized boolean running() {
        return activeTask != null && !activeTask.isDone();
    }

    public synchronized UUID operationId() {
        return operationId;
    }

    @Override
    public void close() {
        executor.close();
    }
}

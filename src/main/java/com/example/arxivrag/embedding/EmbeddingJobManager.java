package com.example.arxivrag.embedding;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Coordinates execution while PostgreSQL owns job identity, request data, and lifecycle state. */
@Component
public class EmbeddingJobManager implements AutoCloseable {

    private final EmbeddingIngestionService ingestionService;
    private final EmbeddingJobRepository repository;
    private final ExecutorService executor;
    private Future<?> activeTask;
    private UUID operationId;

    public EmbeddingJobManager(
            EmbeddingIngestionService ingestionService,
            EmbeddingJobRepository repository,
            @Qualifier("embeddingJobExecutor") ExecutorService executor
    ) {
        this.ingestionService = ingestionService;
        this.repository = repository;
        this.executor = executor;
    }

    public synchronized UUID start(EmbeddingIngestionRequest request) {
        assertIdle();
        operationId = ingestionService.prepare(request);
        submit(operationId, request);
        return operationId;
    }

    public synchronized UUID resume(UUID jobId) {
        assertIdle();
        var job = repository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Embedding job not found: " + jobId));
        if (java.util.Set.of("COMPLETED", "RUNNING", "WAITING_FOR_PROVIDER_CAPACITY")
                .contains(job.status())) {
            throw new IllegalStateException("Embedding job " + jobId + " cannot be resumed from " + job.status());
        }
        var request = repository.requestFor(jobId)
                .orElseThrow(() -> new IllegalStateException("Embedding job request is unavailable: " + jobId));
        operationId = jobId;
        submit(jobId, request);
        return jobId;
    }

    private void submit(UUID jobId, EmbeddingIngestionRequest request) {
        activeTask = executor.submit(() -> ingestionService.ingest(request, jobId));
    }

    public synchronized boolean cancel(UUID jobId) {
        var accepted = repository.requestCancellation(jobId);
        if (jobId.equals(operationId) && activeTask != null && !activeTask.isDone()) {
            activeTask.cancel(true);
        }
        repository.cancelIfQueued(jobId);
        return accepted;
    }

    public synchronized boolean cancel() {
        return operationId != null && cancel(operationId);
    }

    public synchronized boolean running() {
        return activeTask != null && !activeTask.isDone();
    }

    public synchronized UUID operationId() {
        return operationId;
    }

    private void assertIdle() {
        if (activeTask != null && !activeTask.isDone()) {
            throw new IllegalStateException("An embedding job is already running in this application instance");
        }
    }

    @Override
    public void close() {
        // The Spring-managed executor owns its lifecycle.
    }
}

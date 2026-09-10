package com.example.arxivrag.embedding;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * In-process progress state for the currently running embedding job.
 *
 * <p>The state is deliberately operational rather than authoritative. Database
 * checkpoints remain the source of truth for restartability and the
 * {@code embedding_jobs} table preserves completed/failed job summaries; this
 * component fills the short live-progress gap between database updates.</p>
 */
@Component
public class EmbeddingIngestionProgress {

    private ProgressState state = ProgressState.idle();

    public synchronized void start(long totalPapers, String model, int dimension, int batchSize) {
        state = ProgressState.running(totalPapers, model, dimension, batchSize);
    }

    public synchronized void start(UUID jobId, long totalPapers, String model, int dimension, int batchSize) {
        state = ProgressState.running(jobId, totalPapers, model, dimension, batchSize);
    }

    public synchronized void markScanned() {
        state.papersScanned++;
        state.documentsPrepared++;
        state.updatedAt = Instant.now();
    }

    public synchronized void markSkipped() {
        state.skipped++;
        state.updatedAt = Instant.now();
    }

    public synchronized void markEmbedded() {
        state.embedded++;
        state.updatedAt = Instant.now();
    }

    public synchronized void providerRequestStarted() {
        state.providerRequests++;
        state.updatedAt = Instant.now();
    }

    public synchronized void providerRequestCompleted(long latencyMillis) {
        state.lastProviderLatencyMillis = latencyMillis;
        state.updatedAt = Instant.now();
    }

    public synchronized void providerRequestFailed() {
        state.providerFailures++;
        state.updatedAt = Instant.now();
    }

    public synchronized void markLastPaper(Long paperId) {
        state.lastPaperId = paperId;
        state.updatedAt = Instant.now();
    }

    public synchronized void complete() {
        state.status = "COMPLETED";
        state.completedAt = Instant.now();
        state.updatedAt = state.completedAt;
    }

    public synchronized void fail(Throwable exception) {
        state.status = "FAILED";
        state.failures++;
        state.lastError = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        state.completedAt = Instant.now();
        state.updatedAt = state.completedAt;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                state.status,
                state.jobId,
                state.totalPapers,
                state.papersScanned,
                state.documentsPrepared,
                state.embedded,
                state.skipped,
                state.failures,
                state.model,
                state.dimension,
                state.batchSize,
                state.startedAt,
                state.updatedAt,
                state.completedAt,
                state.lastError,
                state.providerRequests,
                state.providerFailures,
                state.lastPaperId,
                state.lastProviderLatencyMillis
        );
    }

    public record Snapshot(
            String status,
            UUID jobId,
            long totalPapers,
            long papersScanned,
            long documentsPrepared,
            long embedded,
            long skipped,
            long failures,
            String embeddingModel,
            int embeddingDimension,
            int batchSize,
            Instant startedAt,
            Instant updatedAt,
            Instant completedAt,
            String lastError,
            long providerRequests,
            long providerFailures,
            Long lastPaperId,
            long lastProviderLatencyMillis
    ) {
    }

    private static final class ProgressState {

        private String status;
        private UUID jobId;
        private long totalPapers;
        private long papersScanned;
        private long documentsPrepared;
        private long embedded;
        private long skipped;
        private long failures;
        private String model;
        private int dimension;
        private int batchSize;
        private Instant startedAt;
        private Instant updatedAt;
        private Instant completedAt;
        private String lastError;
        private long providerRequests;
        private long providerFailures;
        private Long lastPaperId;
        private long lastProviderLatencyMillis;

        private static ProgressState idle() {
            var state = new ProgressState();
            state.status = "IDLE";
            state.updatedAt = Instant.now();
            return state;
        }

        private static ProgressState running(long totalPapers, String model, int dimension, int batchSize) {
            return running(null, totalPapers, model, dimension, batchSize);
        }

        private static ProgressState running(UUID jobId, long totalPapers, String model, int dimension, int batchSize) {
            var now = Instant.now();
            var state = new ProgressState();
            state.status = "RUNNING";
            state.jobId = jobId;
            state.totalPapers = totalPapers;
            state.model = model;
            state.dimension = dimension;
            state.batchSize = batchSize;
            state.startedAt = now;
            state.updatedAt = now;
            return state;
        }
    }
}

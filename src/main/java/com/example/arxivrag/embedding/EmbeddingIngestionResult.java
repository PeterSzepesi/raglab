package com.example.arxivrag.embedding;

import java.time.Duration;
import java.util.UUID;

public record EmbeddingIngestionResult(
        long papersScanned,
        long documentsPrepared,
        long embedded,
        long skipped,
        String embeddingModel,
        int embeddingDimension,
        Duration elapsed,
        UUID jobId,
        long providerRequests,
        long failures
) {

    public EmbeddingIngestionResult(
            long papersScanned,
            long documentsPrepared,
            long embedded,
            long skipped,
            String embeddingModel,
            int embeddingDimension,
            Duration elapsed
    ) {
        this(papersScanned, documentsPrepared, embedded, skipped, embeddingModel, embeddingDimension,
                elapsed, null, 0, 0);
    }

    public String formatSummary() {
        return """
                Embedding ingestion complete

                Papers scanned:       %d
                Documents prepared:   %d
                Embedded:             %d
                Skipped unchanged:    %d
                Embedding model:      %s
                Embedding dimension:  %d
                Provider requests:    %d
                Failures:              %d
                Job ID:                %s
                Elapsed:              %.3f s
                """.formatted(
                papersScanned,
                documentsPrepared,
                embedded,
                skipped,
                embeddingModel,
                embeddingDimension,
                providerRequests,
                failures,
                jobId == null ? "n/a" : jobId,
                elapsed.toMillis() / 1000.0
        );
    }
}

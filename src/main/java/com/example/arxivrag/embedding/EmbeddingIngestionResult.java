package com.example.arxivrag.embedding;

import java.time.Duration;

public record EmbeddingIngestionResult(
        long papersScanned,
        long documentsPrepared,
        long embedded,
        long skipped,
        String embeddingModel,
        int embeddingDimension,
        Duration elapsed
) {

    public String formatSummary() {
        return """
                Embedding ingestion complete

                Papers scanned:       %d
                Documents prepared:   %d
                Embedded:             %d
                Skipped unchanged:    %d
                Embedding model:      %s
                Embedding dimension:  %d
                Elapsed:              %.3f s
                """.formatted(
                papersScanned,
                documentsPrepared,
                embedded,
                skipped,
                embeddingModel,
                embeddingDimension,
                elapsed.toMillis() / 1000.0
        );
    }
}

package com.example.arxivrag.embedding;

import java.time.Instant;
import java.util.UUID;

public record EmbeddingJob(
        UUID jobId,
        String status,
        String embeddingModel,
        int embeddingDimension,
        String documentSchemaVersion,
        long totalPapers,
        long papersScanned,
        long documentsPrepared,
        long embedded,
        long skipped,
        long failures,
        long providerRequests,
        Long lastPaperId,
        Instant startedAt,
        Instant updatedAt,
        Instant completedAt,
        String lastError,
        boolean cancelRequested,
        Long requestedFromPaperId,
        Long requestedToPaperId,
        String requestedCategory,
        java.time.LocalDate requestedFromDate,
        java.time.LocalDate requestedToDate,
        Integer requestedMaxPapers,
        boolean dryRun
) {
}

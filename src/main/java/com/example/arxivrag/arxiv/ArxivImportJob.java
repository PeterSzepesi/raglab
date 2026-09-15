package com.example.arxivrag.arxiv;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ArxivImportJob(
        UUID jobId, String status, String sourcePath, String sourceSha256,
        List<String> categories, LocalDate fromDate, Integer maxRecords,
        long recordsRead, long accepted, long imported, long updated, long skipped, long invalidRecords,
        boolean cancelRequested, Instant startedAt, Instant updatedAt, Instant completedAt, String lastError,
        List<Long> malformedLineSamples
) { }

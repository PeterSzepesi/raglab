package com.example.arxivrag.vector;

import java.time.LocalDate;
import java.util.List;

public record VectorSearchResult(
        Long paperId,
        String arxivId,
        String chunkId,
        String contentHash,
        String embeddingModel,
        double score,
        String text,
        String title,
        String abstractText,
        List<String> authors,
        List<String> categories,
        LocalDate submittedDate
) {
    public VectorSearchResult(Long paperId, String arxivId, String chunkId, String contentHash,
                              String embeddingModel, double score, String text) {
        this(paperId, arxivId, chunkId, contentHash, embeddingModel, score, text,
                null, null, List.of(), List.of(), null);
    }

    public boolean hasPaperProjection() {
        return title != null && abstractText != null;
    }
}

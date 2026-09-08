package com.example.arxivrag.vector;

public record VectorSearchResult(
        Long paperId,
        String arxivId,
        String chunkId,
        String contentHash,
        String embeddingModel,
        double score,
        String text
) {
}

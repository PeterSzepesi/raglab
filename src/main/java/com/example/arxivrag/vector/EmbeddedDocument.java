package com.example.arxivrag.vector;

import java.time.LocalDate;
import java.util.List;

public record EmbeddedDocument(
        Long paperId,
        String arxivId,
        String chunkId,
        String contentHash,
        String embeddingModel,
        int embeddingDimension,
        String text,
        List<String> categories,
        LocalDate submittedDate,
        List<Double> embedding
) {

    public EmbeddedDocument {
        if (paperId == null) {
            throw new IllegalArgumentException("paperId must not be null");
        }
        if (embeddingModel == null || embeddingModel.isBlank()) {
            throw new IllegalArgumentException("embeddingModel must not be blank");
        }
        if (embeddingDimension <= 0) {
            throw new IllegalArgumentException("embeddingDimension must be greater than zero");
        }
        if (embedding == null || embedding.size() != embeddingDimension) {
            throw new IllegalArgumentException("embedding size must match embeddingDimension");
        }
        if (embedding.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new IllegalArgumentException("embedding values must be finite numbers");
        }
        categories = categories == null ? List.of() : List.copyOf(categories);
        embedding = List.copyOf(embedding);
    }
}

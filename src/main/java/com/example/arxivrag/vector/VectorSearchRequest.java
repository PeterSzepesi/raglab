package com.example.arxivrag.vector;

import java.util.List;

public record VectorSearchRequest(
        String embeddingModel,
        List<Double> embedding,
        int topK
) {

    public VectorSearchRequest {
        if (embeddingModel == null || embeddingModel.isBlank()) {
            throw new IllegalArgumentException("embeddingModel must not be blank");
        }
        if (embedding == null || embedding.isEmpty()) {
            throw new IllegalArgumentException("embedding must not be empty");
        }
        if (embedding.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new IllegalArgumentException("embedding values must be finite numbers");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be greater than zero");
        }
        embedding = List.copyOf(embedding);
    }
}

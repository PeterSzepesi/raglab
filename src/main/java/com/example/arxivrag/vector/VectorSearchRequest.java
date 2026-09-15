package com.example.arxivrag.vector;

import java.time.LocalDate;
import java.util.List;

public record VectorSearchRequest(
        String embeddingModel,
        List<Double> embedding,
        int topK,
        String category,
        LocalDate fromDate,
        LocalDate toDate
) {

    public VectorSearchRequest(String embeddingModel, List<Double> embedding, int topK) {
        this(embeddingModel, embedding, topK, null, null, null);
    }

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
        if (category != null && category.isBlank()) {
            throw new IllegalArgumentException("category must not be blank");
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must not be after toDate");
        }
        embedding = List.copyOf(embedding);
    }
}

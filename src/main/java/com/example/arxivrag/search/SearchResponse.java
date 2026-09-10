package com.example.arxivrag.search;

import com.example.arxivrag.vector.SemanticSearchResult;

import java.util.List;

public record SearchResponse(
        String query,
        int topK,
        String embeddingModel,
        List<SemanticSearchResult> results,
        String mode
) {

    public SearchResponse(String query, int topK, String embeddingModel, List<SemanticSearchResult> results) {
        this(query, topK, embeddingModel, results, "semantic");
    }
}

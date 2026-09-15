package com.example.arxivrag.search;

import com.example.arxivrag.vector.SemanticSearchResult;

import java.util.List;

public record SearchResponse(
        String query,
        int topK,
        String embeddingModel,
        List<SemanticSearchResult> results,
        String mode,
        SearchDiagnostics diagnostics
) {

    public SearchResponse(String query, int topK, String embeddingModel, List<SemanticSearchResult> results,
                          String mode) {
        this(query, topK, embeddingModel, results, mode, SearchDiagnostics.empty());
    }

    public SearchResponse(String query, int topK, String embeddingModel, List<SemanticSearchResult> results) {
        this(query, topK, embeddingModel, results, "semantic", SearchDiagnostics.empty());
    }

    public record SearchDiagnostics(long elapsedMilliseconds, java.util.Map<String, Long> stages,
                                    boolean degraded, String explanation) {
        public static SearchDiagnostics empty() { return new SearchDiagnostics(0, java.util.Map.of(), false, null); }
    }
}

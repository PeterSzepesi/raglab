package com.example.arxivrag.embedding;

import java.util.ArrayList;
import java.util.List;

/** Builds provider batches using both item count and a conservative token estimate. */
final class EmbeddingBatchPlanner {

    private EmbeddingBatchPlanner() {
    }

    static List<List<RetrievalDocument>> plan(List<RetrievalDocument> documents, int maxItems,
                                              int maxEstimatedTokens, int maxEstimatedDocumentTokens) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        if (maxItems <= 0 || maxEstimatedTokens <= 0 || maxEstimatedDocumentTokens <= 0) {
            throw new IllegalArgumentException("embedding provider batch limits must be greater than zero");
        }

        var batches = new ArrayList<List<RetrievalDocument>>();
        var current = new ArrayList<RetrievalDocument>();
        var currentTokens = 0;
        for (var document : documents) {
            var documentTokens = estimateTokens(document.text());
            if (documentTokens > maxEstimatedDocumentTokens) {
                throw new IllegalArgumentException("Retrieval document for " + document.arxivId()
                        + " exceeds the configured per-document provider token estimate limit");
            }
            if (!current.isEmpty() && (current.size() >= maxItems || currentTokens + documentTokens > maxEstimatedTokens)) {
                batches.add(List.copyOf(current));
                current.clear();
                currentTokens = 0;
            }
            current.add(document);
            currentTokens += documentTokens;
        }
        if (!current.isEmpty()) {
            batches.add(List.copyOf(current));
        }
        return List.copyOf(batches);
    }

    static int estimateTokens(String text) {
        return Math.max(1, (text == null ? 0 : text.length() + 3) / 4);
    }
}

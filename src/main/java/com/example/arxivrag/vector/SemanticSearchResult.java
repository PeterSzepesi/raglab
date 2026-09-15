package com.example.arxivrag.vector;

import java.time.LocalDate;
import java.util.List;

public record SemanticSearchResult(
        Long paperId,
        String arxivId,
        String title,
        String abstractText,
        List<String> authors,
        List<String> categories,
        LocalDate submittedDate,
        double score,
        String retrievalText,
        String retrievalSource
) {

    public SemanticSearchResult {
        authors = authors == null ? List.of() : List.copyOf(authors);
        categories = categories == null ? List.of() : List.copyOf(categories);
        retrievalSource = retrievalSource == null || retrievalSource.isBlank() ? "unknown" : retrievalSource;
    }

    public SemanticSearchResult(Long paperId, String arxivId, String title, String abstractText,
                                List<String> authors, List<String> categories, LocalDate submittedDate,
                                double score, String retrievalText) {
        this(paperId, arxivId, title, abstractText, authors, categories, submittedDate, score, retrievalText,
                "semantic");
    }
}

package com.example.arxivrag.ai.chat;

import com.example.arxivrag.paper.ArxivId;

public record ChatCitation(
        Long paperId,
        String arxivId,
        String title,
        Double score,
        String retrievalSource,
        String url
) {

    public ChatCitation(Long paperId, String arxivId, String title, Double score) {
        this(paperId, arxivId, title, score, "unknown", arxivUrlFor(arxivId));
    }

    public ChatCitation(Long paperId, String arxivId, String title, Double score, String retrievalSource) {
        this(paperId, arxivId, title, score, retrievalSource, arxivUrlFor(arxivId));
    }

    public ChatCitation {
        retrievalSource = retrievalSource == null || retrievalSource.isBlank() ? "unknown" : retrievalSource;
        url = arxivUrlFor(arxivId);
    }

    public static String arxivUrlFor(String arxivId) {
        return ArxivId.abstractUrlOrNull(arxivId);
    }
}

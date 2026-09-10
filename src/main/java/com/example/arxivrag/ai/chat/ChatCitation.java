package com.example.arxivrag.ai.chat;

import java.util.regex.Pattern;

public record ChatCitation(
        Long paperId,
        String arxivId,
        String title,
        Double score,
        String url
) {

    private static final String ARXIV_ABSTRACT_BASE_URL = "https://arxiv.org/abs/";

    private static final Pattern ARXIV_ID_PATTERN = Pattern.compile(
            "(?:\\d{4}\\.\\d{4,5}|[A-Za-z][A-Za-z0-9.-]*/\\d{7})(?:v\\d+)?"
    );

    public ChatCitation(Long paperId, String arxivId, String title, Double score) {
        this(paperId, arxivId, title, score, arxivUrlFor(arxivId));
    }

    public ChatCitation {
        url = arxivUrlFor(arxivId);
    }

    public static String arxivUrlFor(String arxivId) {
        if (arxivId == null) {
            return null;
        }
        var normalized = arxivId.trim();
        if (!ARXIV_ID_PATTERN.matcher(normalized).matches()) {
            return null;
        }
        return ARXIV_ABSTRACT_BASE_URL + normalized;
    }
}

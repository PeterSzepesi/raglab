package com.example.arxivrag.ai.chat;

public record ChatCitation(
        Long paperId,
        String arxivId,
        String title,
        Double score
) {
}

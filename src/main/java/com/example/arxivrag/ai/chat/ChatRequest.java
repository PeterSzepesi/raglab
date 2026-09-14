package com.example.arxivrag.ai.chat;

import java.time.LocalDate;

public record ChatRequest(
        String message,
        String retrievalMode,
        String category,
        LocalDate fromDate,
        LocalDate toDate,
        Boolean agentic
) {
    public ChatRequest(String message) {
        this(message, null, null, null, null, null);
    }
}

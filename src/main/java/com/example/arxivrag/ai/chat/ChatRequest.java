package com.example.arxivrag.ai.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ChatRequest(
        @NotBlank @Size(max = 8000) String message,
        String retrievalMode,
        @Size(max = 64) String category,
        LocalDate fromDate,
        LocalDate toDate,
        Boolean agentic
) {
    public ChatRequest(String message) {
        this(message, null, null, null, null, null);
    }
}

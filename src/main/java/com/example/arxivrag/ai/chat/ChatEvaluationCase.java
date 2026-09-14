package com.example.arxivrag.ai.chat;

import java.time.LocalDate;
import java.util.List;

public record ChatEvaluationCase(
        String id,
        String question,
        String retrievalMode,
        String category,
        LocalDate fromDate,
        LocalDate toDate,
        List<String> expectedArxivIds,
        boolean expectNoEvidence,
        boolean agentic
) {
    public ChatEvaluationCase {
        expectedArxivIds = expectedArxivIds == null ? List.of() : List.copyOf(expectedArxivIds);
    }

    public ChatRequest request(boolean forceAgentic) {
        return new ChatRequest(question, retrievalMode, category, fromDate, toDate,
                forceAgentic || agentic ? Boolean.TRUE : Boolean.FALSE);
    }
}

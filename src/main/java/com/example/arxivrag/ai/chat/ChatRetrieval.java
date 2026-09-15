package com.example.arxivrag.ai.chat;

import com.example.arxivrag.vector.SemanticSearchResult;

import java.util.List;

public record ChatRetrieval(
        List<SemanticSearchResult> results,
        List<String> toolsUsed,
        ChatRetrievalOutcome outcome,
        RetrievalPolicy policy
) {
    public ChatRetrieval {
        results = results == null ? List.of() : List.copyOf(results);
        toolsUsed = toolsUsed == null ? List.of() : List.copyOf(toolsUsed);
        outcome = outcome == null
                ? (results.isEmpty() ? ChatRetrievalOutcome.NO_EVIDENCE : ChatRetrievalOutcome.EVIDENCE)
                : outcome;
        policy = policy == null ? RetrievalPolicy.legacyDefaults() : policy;
    }

    public ChatRetrieval(List<SemanticSearchResult> results, List<String> toolsUsed) {
        this(results, toolsUsed, results == null || results.isEmpty()
                ? ChatRetrievalOutcome.NO_EVIDENCE
                : ChatRetrievalOutcome.EVIDENCE, RetrievalPolicy.legacyDefaults());
    }

    public ChatRetrieval(List<SemanticSearchResult> results, List<String> toolsUsed,
                         ChatRetrievalOutcome outcome) {
        this(results, toolsUsed, outcome, RetrievalPolicy.legacyDefaults());
    }
}

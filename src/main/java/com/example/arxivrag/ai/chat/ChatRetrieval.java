package com.example.arxivrag.ai.chat;

import com.example.arxivrag.vector.SemanticSearchResult;

import java.util.List;

public record ChatRetrieval(List<SemanticSearchResult> results, List<String> toolsUsed) {
    public ChatRetrieval {
        results = results == null ? List.of() : List.copyOf(results);
        toolsUsed = toolsUsed == null ? List.of() : List.copyOf(toolsUsed);
    }
}

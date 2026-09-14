package com.example.arxivrag.ai.chat;

import java.util.List;

public record GroundedContext(String evidenceJson, List<ChatCitation> citations, List<String> toolsUsed) {
    public GroundedContext {
        citations = List.copyOf(citations);
        toolsUsed = List.copyOf(toolsUsed);
    }
}

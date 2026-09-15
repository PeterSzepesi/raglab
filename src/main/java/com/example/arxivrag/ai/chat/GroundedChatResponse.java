package com.example.arxivrag.ai.chat;

import java.util.List;

public record GroundedChatResponse(
        String answer,
        String model,
        List<ChatCitation> citations,
        List<String> toolsUsed,
        String groundingStatus,
        String cacheStatus
) {
    public GroundedChatResponse(String answer, String model, List<ChatCitation> citations, List<String> toolsUsed) {
        this(answer, model, citations, toolsUsed, "GROUNDED", "BYPASS");
    }

    public GroundedChatResponse(String answer, String model, List<ChatCitation> citations,
                                List<String> toolsUsed, String groundingStatus) {
        this(answer, model, citations, toolsUsed, groundingStatus, "BYPASS");
    }
}

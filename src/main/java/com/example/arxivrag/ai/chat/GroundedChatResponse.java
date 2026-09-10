package com.example.arxivrag.ai.chat;

import java.util.List;

public record GroundedChatResponse(
        String answer,
        String model,
        List<ChatCitation> citations,
        List<String> toolsUsed
) {
}

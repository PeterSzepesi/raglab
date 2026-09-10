package com.example.arxivrag.ai.chat;

import java.util.List;

public record ToolExecution(
        String toolName,
        Object result,
        List<ChatCitation> citations
) {
}

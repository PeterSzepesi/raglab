package com.example.arxivrag.ai.chat;

import java.util.Locale;

public enum ChatOrchestrationMode {
    PRE_RETRIEVAL,
    AGENTIC;

    public static ChatOrchestrationMode parse(String value) {
        if (value == null || value.isBlank()) {
            return PRE_RETRIEVAL;
        }
        try {
            return valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("chat.orchestration-mode must be pre-retrieval or agentic");
        }
    }
}

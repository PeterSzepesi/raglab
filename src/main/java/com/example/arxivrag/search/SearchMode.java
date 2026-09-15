package com.example.arxivrag.search;

import java.util.Locale;

public enum SearchMode {
    AUTO,
    SEMANTIC,
    LEXICAL,
    HYBRID;

    public static SearchMode parse(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("mode must be one of auto, semantic, lexical, or hybrid");
        }
    }
}

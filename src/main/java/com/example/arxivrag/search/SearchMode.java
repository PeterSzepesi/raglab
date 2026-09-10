package com.example.arxivrag.search;

import java.util.Locale;

public enum SearchMode {
    SEMANTIC,
    LEXICAL,
    HYBRID;

    public static SearchMode parse(String value) {
        if (value == null || value.isBlank()) {
            return SEMANTIC;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("mode must be one of semantic, lexical, or hybrid");
        }
    }
}

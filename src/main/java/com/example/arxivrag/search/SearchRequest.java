package com.example.arxivrag.search;

import java.time.LocalDate;

public record SearchRequest(
        String query,
        Integer topK,
        String category,
        LocalDate fromDate,
        LocalDate toDate,
        String mode
) {

    public SearchRequest(String query, Integer topK, String category, LocalDate fromDate, LocalDate toDate) {
        this(query, topK, category, fromDate, toDate, null);
    }
}

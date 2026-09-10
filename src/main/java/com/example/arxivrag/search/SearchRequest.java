package com.example.arxivrag.search;

import java.time.LocalDate;

public record SearchRequest(
        String query,
        Integer topK,
        String category,
        LocalDate fromDate,
        LocalDate toDate
) {
}

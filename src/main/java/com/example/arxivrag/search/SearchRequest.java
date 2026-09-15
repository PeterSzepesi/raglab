package com.example.arxivrag.search;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record SearchRequest(
        @NotBlank @Size(max = 4000) String query,
        @Min(1) @Max(50) Integer topK,
        @Size(max = 64) String category,
        LocalDate fromDate,
        LocalDate toDate,
        String mode
) {

    public SearchRequest(String query, Integer topK, String category, LocalDate fromDate, LocalDate toDate) {
        this(query, topK, category, fromDate, toDate, null);
    }
}

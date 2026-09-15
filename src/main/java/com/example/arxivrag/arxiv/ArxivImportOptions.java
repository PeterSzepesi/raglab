package com.example.arxivrag.arxiv;

import java.time.LocalDate;
import java.util.List;

public record ArxivImportOptions(List<String> categories, LocalDate fromDate, Integer maxRecords) {
    public ArxivImportOptions {
        categories = categories == null ? List.of() : categories.stream()
                .filter(value -> value != null && !value.isBlank()).map(String::trim).distinct().toList();
        if (maxRecords != null && maxRecords < 1) {
            throw new IllegalArgumentException("maxRecords must be positive");
        }
    }

    static ArxivImportOptions from(ArxivImportProperties properties) {
        return new ArxivImportOptions(properties.getCategories(), properties.getFromDate(), properties.getMaxRecords());
    }
}

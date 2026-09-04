package com.example.arxivrag.arxiv;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Component
public class ArxivRecordFilter {

    public boolean accepts(ArxivRecord record, ArxivImportProperties properties) {
        return categoryMatches(record, properties.getCategories())
                && submittedDateMatches(record, properties.getFromDate());
    }

    private boolean categoryMatches(ArxivRecord record, Iterable<String> allowedCategories) {
        var recordCategories = new HashSet<>(PaperMapper.splitSpaceSeparated(record.categories()));
        for (var category : allowedCategories) {
            if (recordCategories.contains(category)) {
                return true;
            }
        }
        return false;
    }

    private boolean submittedDateMatches(ArxivRecord record, LocalDate fromDate) {
        if (fromDate == null) {
            return true;
        }
        var submittedDate = PaperMapper.submittedDate(record);
        return submittedDate != null && !submittedDate.isBefore(fromDate);
    }
}

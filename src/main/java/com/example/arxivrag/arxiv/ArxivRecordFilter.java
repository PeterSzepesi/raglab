package com.example.arxivrag.arxiv;

import java.time.LocalDate;
import java.util.HashSet;
import org.springframework.stereotype.Component;

@Component
public class ArxivRecordFilter {

    public boolean accepts(ArxivRecord record, ArxivImportProperties properties) {
        return accepts(record, properties.getCategories(), properties.getFromDate());
    }

    public boolean accepts(ArxivRecord record, Iterable<String> categories, LocalDate fromDate) {
        return categoryMatches(record, categories) && submittedDateMatches(record, fromDate);
    }

    private boolean categoryMatches(ArxivRecord record, Iterable<String> allowedCategories) {
        if (!allowedCategories.iterator().hasNext()) return true;
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

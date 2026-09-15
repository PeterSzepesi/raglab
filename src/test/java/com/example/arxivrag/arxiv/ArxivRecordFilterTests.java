package com.example.arxivrag.arxiv;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArxivRecordFilterTests {

    private final ArxivRecordFilter filter = new ArxivRecordFilter();

    @Test
    void acceptsMatchingCategoryOnBoundaryDate() {
        assertThat(filter.accepts(record("cs.CL cs.AI", "Sat, 1 Jan 2022 00:00:00 GMT"), properties())).isTrue();
    }

    @Test
    void rejectsNonMatchingCategory() {
        assertThat(filter.accepts(record("math.CO", "Tue, 2 Jan 2024 00:00:00 GMT"), properties())).isFalse();
    }

    @Test
    void rejectsDateBeforeLowerBound() {
        assertThat(filter.accepts(record("cs.CL", "Fri, 31 Dec 2021 00:00:00 GMT"), properties())).isFalse();
    }

    private static ArxivImportProperties properties() {
        var properties = new ArxivImportProperties();
        properties.setCategories(List.of("cs.CL", "cs.AI"));
        properties.setFromDate(LocalDate.of(2022, 1, 1));
        return properties;
    }

    private static ArxivRecord record(String categories, String created) {
        return new ArxivRecord(
                "2401.00001",
                "Title",
                "Author",
                categories,
                null,
                null,
                "Abstract",
                LocalDate.of(2024, 1, 3),
                List.of(new ArxivRecord.Version("v1", created))
        );
    }
}

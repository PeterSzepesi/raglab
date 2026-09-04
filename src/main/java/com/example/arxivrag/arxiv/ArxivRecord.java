package com.example.arxivrag.arxiv;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ArxivRecord(
        String id,
        String title,
        String authors,
        String categories,
        String doi,
        @JsonProperty("journal-ref")
        String journalRef,
        @JsonProperty("abstract")
        String abstractText,
        @JsonProperty("update_date")
        LocalDate updateDate,
        List<Version> versions
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Version(
            String version,
            String created
    ) {
    }
}

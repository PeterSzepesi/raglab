package com.example.arxivrag.arxiv;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "arxiv.import")
@Validated
public class ArxivImportProperties {

    private List<String> categories = new ArrayList<>(List.of("cs.CL", "cs.AI", "cs.LG", "cs.CV"));

    private LocalDate fromDate = LocalDate.of(2022, 1, 1);

    @Positive
    private Integer maxRecords = 50_000;

    @Positive
    private int batchSize = 1_000;

    @NotNull
    private MalformedRecordPolicy malformedRecordPolicy = MalformedRecordPolicy.WARN_AND_SKIP;

    public List<String> getCategories() {
        return List.copyOf(categories);
    }

    public void setCategories(List<String> categories) {
        this.categories = new ArrayList<>(categories);
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public Integer getMaxRecords() {
        return maxRecords;
    }

    public void setMaxRecords(Integer maxRecords) {
        this.maxRecords = maxRecords;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public MalformedRecordPolicy getMalformedRecordPolicy() {
        return malformedRecordPolicy;
    }

    public void setMalformedRecordPolicy(MalformedRecordPolicy malformedRecordPolicy) {
        this.malformedRecordPolicy = malformedRecordPolicy;
    }

    public enum MalformedRecordPolicy {
        WARN_AND_SKIP,
        FAIL_FAST
    }
}

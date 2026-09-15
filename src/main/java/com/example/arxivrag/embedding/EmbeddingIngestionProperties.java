package com.example.arxivrag.embedding;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "embedding.ingestion")
@Validated
public class EmbeddingIngestionProperties {

    @Positive
    private int batchSize = 100;

    @NotBlank
    private String modelName = "offline-deterministic-1536";

    @Positive
    private int dimensions = 1536;

    @Positive
    private int progressLogInterval = 10;

    @Positive
    private int providerBatchSize = 32;

    @Positive
    private int providerMaxEstimatedTokens = 100_000;

    @Positive
    private int providerMaxEstimatedDocumentTokens = 7_000;

    private Integer maxPapers;

    private Long fromPaperId;

    private Long toPaperId;

    private String category;

    private java.time.LocalDate fromDate;

    private java.time.LocalDate toDate;

    private boolean dryRun;

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public int getDimensions() {
        return dimensions;
    }

    public void setDimensions(int dimensions) {
        this.dimensions = dimensions;
    }

    public int getProgressLogInterval() {
        return progressLogInterval;
    }

    public void setProgressLogInterval(int progressLogInterval) {
        this.progressLogInterval = progressLogInterval;
    }

    public int getProviderBatchSize() {
        return providerBatchSize;
    }

    public void setProviderBatchSize(int providerBatchSize) {
        this.providerBatchSize = providerBatchSize;
    }

    public int getProviderMaxEstimatedTokens() {
        return providerMaxEstimatedTokens;
    }

    public void setProviderMaxEstimatedTokens(int providerMaxEstimatedTokens) {
        this.providerMaxEstimatedTokens = providerMaxEstimatedTokens;
    }

    public int getProviderMaxEstimatedDocumentTokens() {
        return providerMaxEstimatedDocumentTokens;
    }

    public void setProviderMaxEstimatedDocumentTokens(int providerMaxEstimatedDocumentTokens) {
        this.providerMaxEstimatedDocumentTokens = providerMaxEstimatedDocumentTokens;
    }

    public Integer getMaxPapers() {
        return maxPapers;
    }

    public void setMaxPapers(Integer maxPapers) {
        this.maxPapers = maxPapers;
    }

    public Long getFromPaperId() {
        return fromPaperId;
    }

    public void setFromPaperId(Long fromPaperId) {
        this.fromPaperId = fromPaperId;
    }

    public Long getToPaperId() {
        return toPaperId;
    }

    public void setToPaperId(Long toPaperId) {
        this.toPaperId = toPaperId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public java.time.LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(java.time.LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public java.time.LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(java.time.LocalDate toDate) {
        this.toDate = toDate;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }
}

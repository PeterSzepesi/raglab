package com.example.arxivrag.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "embedding.ingestion")
public class EmbeddingIngestionProperties {

    private int batchSize = 100;

    private String modelName = "offline-deterministic-1536";

    private int dimensions = 1536;

    private int progressLogInterval = 10;

    private int providerBatchSize = 32;

    private int providerMaxEstimatedTokens = 100_000;

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

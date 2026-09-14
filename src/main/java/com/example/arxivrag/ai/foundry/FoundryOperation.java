package com.example.arxivrag.ai.foundry;

public enum FoundryOperation {
    CHAT("chat", false),
    QUERY_EMBEDDING("query-embedding", false),
    INGESTION_EMBEDDING("ingestion-embedding", true);

    private final String metricName;
    private final boolean background;

    FoundryOperation(String metricName, boolean background) {
        this.metricName = metricName;
        this.background = background;
    }

    public String metricName() {
        return metricName;
    }

    public boolean background() {
        return background;
    }
}

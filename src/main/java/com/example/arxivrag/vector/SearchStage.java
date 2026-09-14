package com.example.arxivrag.vector;

public enum SearchStage {
    QUERY_EMBEDDING("query-embedding"),
    VECTOR_SEARCH("vector-search"),
    PAPER_HYDRATION("paper-hydration");

    private final String metricName;
    SearchStage(String metricName) { this.metricName = metricName; }
    public String metricName() { return metricName; }
}

package com.example.arxivrag.ai.chat;

public enum ChatStage {
    TOOL_SELECTION_MODEL("tool-selection-model"),
    QUERY_EMBEDDING("query-embedding"),
    VECTOR_SEARCH("vector-search"),
    PAPER_HYDRATION("paper-hydration"),
    RETRIEVAL("retrieval"),
    TOOL_EXECUTION("tool-execution"),
    ANSWER_MODEL("answer-model"),
    FIRST_TOKEN("first-token"),
    QUOTA_WAIT("quota-wait"),
    RETRY_BACKOFF("retry-backoff");

    private final String metricName;

    ChatStage(String metricName) {
        this.metricName = metricName;
    }

    public String metricName() {
        return metricName;
    }
}

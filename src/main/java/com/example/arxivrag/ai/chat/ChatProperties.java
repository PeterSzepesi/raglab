package com.example.arxivrag.ai.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "chat")
public class ChatProperties {

    private String orchestrationMode = "pre-retrieval";
    private int maxToolRounds = 1;
    private int retrievalTopK = 4;
    private int maxContextCharactersPerPaper = 800;
    private int maxContextCharactersTotal = 4000;
    private Integer maxCompletionTokens = 400;
    private Double temperature;
    private boolean streamingEnabled = true;
    private Duration streamingTimeout = Duration.ofSeconds(90);
    private boolean cacheEnabled = true;
    private Duration cacheTtl = Duration.ofMinutes(10);
    private long cacheMaximumSize = 1000;
    private int maxParallelToolCalls = 3;
    private boolean debugTimings;
    private String candidateChatDeployment = "";
    private Double benchmarkInputPricePerMillionTokens;
    private Double benchmarkCachedInputPricePerMillionTokens;
    private Double benchmarkOutputPricePerMillionTokens;

    public ChatOrchestrationMode mode() {
        return ChatOrchestrationMode.parse(orchestrationMode);
    }

    public String getOrchestrationMode() {
        return orchestrationMode;
    }

    public void setOrchestrationMode(String orchestrationMode) {
        this.orchestrationMode = orchestrationMode;
    }

    public int getMaxToolRounds() {
        return maxToolRounds;
    }

    public void setMaxToolRounds(int maxToolRounds) {
        this.maxToolRounds = maxToolRounds;
    }

    public int getRetrievalTopK() {
        return retrievalTopK;
    }

    public void setRetrievalTopK(int retrievalTopK) {
        this.retrievalTopK = retrievalTopK;
    }

    public int getMaxContextCharactersPerPaper() {
        return maxContextCharactersPerPaper;
    }

    public void setMaxContextCharactersPerPaper(int maxContextCharactersPerPaper) {
        this.maxContextCharactersPerPaper = maxContextCharactersPerPaper;
    }

    public int getMaxContextCharactersTotal() {
        return maxContextCharactersTotal;
    }

    public void setMaxContextCharactersTotal(int maxContextCharactersTotal) {
        this.maxContextCharactersTotal = maxContextCharactersTotal;
    }

    public Integer getMaxCompletionTokens() {
        return maxCompletionTokens;
    }

    public void setMaxCompletionTokens(Integer maxCompletionTokens) {
        this.maxCompletionTokens = maxCompletionTokens;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public boolean isStreamingEnabled() {
        return streamingEnabled;
    }

    public void setStreamingEnabled(boolean streamingEnabled) {
        this.streamingEnabled = streamingEnabled;
    }

    public Duration getStreamingTimeout() {
        return streamingTimeout;
    }

    public void setStreamingTimeout(Duration streamingTimeout) {
        this.streamingTimeout = streamingTimeout;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
    }

    public long getCacheMaximumSize() {
        return cacheMaximumSize;
    }

    public void setCacheMaximumSize(long cacheMaximumSize) {
        this.cacheMaximumSize = cacheMaximumSize;
    }

    public int getMaxParallelToolCalls() {
        return maxParallelToolCalls;
    }

    public void setMaxParallelToolCalls(int maxParallelToolCalls) {
        this.maxParallelToolCalls = maxParallelToolCalls;
    }

    public boolean isDebugTimings() {
        return debugTimings;
    }

    public void setDebugTimings(boolean debugTimings) {
        this.debugTimings = debugTimings;
    }

    public String getCandidateChatDeployment() {
        return candidateChatDeployment;
    }

    public void setCandidateChatDeployment(String candidateChatDeployment) {
        this.candidateChatDeployment = candidateChatDeployment;
    }

    public Double getBenchmarkInputPricePerMillionTokens() {
        return benchmarkInputPricePerMillionTokens;
    }

    public void setBenchmarkInputPricePerMillionTokens(Double value) {
        this.benchmarkInputPricePerMillionTokens = value;
    }

    public Double getBenchmarkCachedInputPricePerMillionTokens() {
        return benchmarkCachedInputPricePerMillionTokens;
    }

    public void setBenchmarkCachedInputPricePerMillionTokens(Double value) {
        this.benchmarkCachedInputPricePerMillionTokens = value;
    }

    public Double getBenchmarkOutputPricePerMillionTokens() {
        return benchmarkOutputPricePerMillionTokens;
    }

    public void setBenchmarkOutputPricePerMillionTokens(Double value) {
        this.benchmarkOutputPricePerMillionTokens = value;
    }

    public void validate() {
        if (maxToolRounds < 1 || maxToolRounds > 5) {
            throw new IllegalStateException("chat.max-tool-rounds must be between 1 and 5");
        }
        if (retrievalTopK < 1 || retrievalTopK > 20) {
            throw new IllegalStateException("chat.retrieval-top-k must be between 1 and 20");
        }
        if (maxContextCharactersPerPaper < 100 || maxContextCharactersTotal < maxContextCharactersPerPaper) {
            throw new IllegalStateException("chat context character limits are invalid");
        }
        if (maxCompletionTokens != null && maxCompletionTokens <= 0) {
            throw new IllegalStateException("chat.max-completion-tokens must be positive");
        }
        if (temperature != null && (temperature < 0 || temperature > 2)) {
            throw new IllegalStateException("chat.temperature must be between 0 and 2");
        }
        if (streamingTimeout == null || streamingTimeout.isNegative() || streamingTimeout.isZero()) {
            throw new IllegalStateException("chat.streaming-timeout must be positive");
        }
        if (cacheTtl == null || cacheTtl.isNegative() || cacheTtl.isZero() || cacheMaximumSize < 1) {
            throw new IllegalStateException("chat cache settings are invalid");
        }
        if (maxParallelToolCalls < 1 || maxParallelToolCalls > 8) {
            throw new IllegalStateException("chat.max-parallel-tool-calls must be between 1 and 8");
        }
        if (negative(benchmarkInputPricePerMillionTokens)
                || negative(benchmarkCachedInputPricePerMillionTokens)
                || negative(benchmarkOutputPricePerMillionTokens)) {
            throw new IllegalStateException("chat benchmark token prices must not be negative");
        }
        mode();
    }

    private static boolean negative(Double value) {
        return value != null && value < 0;
    }
}

package com.example.arxivrag.ai.chat;

import java.time.Duration;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "chat")
@Validated
public class ChatProperties {

    @NotBlank
    private String orchestrationMode = "pre-retrieval";
    @Min(1) @Max(5)
    private int maxToolRounds = 1;
    @Min(1) @Max(20)
    private int retrievalTopK = 4;
    @Min(100)
    private int maxContextCharactersPerPaper = 800;
    @Min(100)
    private int maxContextCharactersTotal = 4000;
    @Positive
    private Integer maxCompletionTokens = 400;
    @DecimalMin("0.0") @DecimalMax("2.0")
    private Double temperature;
    private boolean streamingEnabled = true;
    @NotNull
    private Duration streamingTimeout = Duration.ofSeconds(90);
    @NotNull
    private Duration requestDeadline = Duration.ofSeconds(90);
    private boolean cacheEnabled = true;
    @NotNull
    private Duration cacheTtl = Duration.ofMinutes(10);
    @NotNull
    private Duration negativeCacheTtl = Duration.ofSeconds(45);
    @Positive
    private long cacheMaximumSize = 1000;
    @NotBlank
    private String retrievalPolicyVersion = "retrieval-v1";
    @NotBlank
    private String promptVersion = "grounded-v1";
    private String reasoningEffort = "none";
    private boolean providerPromptCacheEnabled;
    private boolean answerCacheEnabled;
    @NotNull
    private Duration answerCacheTtl = Duration.ofMinutes(5);
    @Positive
    private long answerCacheMaximumSize = 250;
    @Min(1) @Max(8)
    private int maxParallelToolCalls = 3;
    private boolean debugTimings;
    @PositiveOrZero
    private Double benchmarkInputPricePerMillionTokens;
    @PositiveOrZero
    private Double benchmarkCachedInputPricePerMillionTokens;
    @PositiveOrZero
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

    public Duration getRequestDeadline() {
        return requestDeadline;
    }

    public void setRequestDeadline(Duration requestDeadline) {
        this.requestDeadline = requestDeadline;
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

    public Duration getNegativeCacheTtl() {
        return negativeCacheTtl;
    }

    public void setNegativeCacheTtl(Duration negativeCacheTtl) {
        this.negativeCacheTtl = negativeCacheTtl;
    }

    public String getRetrievalPolicyVersion() {
        return retrievalPolicyVersion;
    }

    public void setRetrievalPolicyVersion(String retrievalPolicyVersion) {
        this.retrievalPolicyVersion = retrievalPolicyVersion;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public String getReasoningEffort() {
        return reasoningEffort;
    }

    public void setReasoningEffort(String reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
    }

    public boolean isProviderPromptCacheEnabled() { return providerPromptCacheEnabled; }
    public void setProviderPromptCacheEnabled(boolean value) { this.providerPromptCacheEnabled = value; }
    public boolean isAnswerCacheEnabled() { return answerCacheEnabled; }
    public void setAnswerCacheEnabled(boolean value) { this.answerCacheEnabled = value; }
    public Duration getAnswerCacheTtl() { return answerCacheTtl; }
    public void setAnswerCacheTtl(Duration value) { this.answerCacheTtl = value; }
    public long getAnswerCacheMaximumSize() { return answerCacheMaximumSize; }
    public void setAnswerCacheMaximumSize(long value) { this.answerCacheMaximumSize = value; }

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
        if (requestDeadline == null || requestDeadline.isNegative() || requestDeadline.isZero()) {
            throw new IllegalStateException("chat.request-deadline must be positive");
        }
        if (cacheTtl == null || cacheTtl.isNegative() || cacheTtl.isZero()
                || negativeCacheTtl == null || negativeCacheTtl.isNegative() || negativeCacheTtl.isZero()
                || cacheMaximumSize < 1) {
            throw new IllegalStateException("chat cache settings are invalid");
        }
        if (answerCacheEnabled && (answerCacheTtl == null || answerCacheTtl.isNegative()
                || answerCacheTtl.isZero() || answerCacheMaximumSize < 1)) {
            throw new IllegalStateException("chat answer cache settings are invalid");
        }
        if (retrievalPolicyVersion == null || retrievalPolicyVersion.isBlank()
                || promptVersion == null || promptVersion.isBlank()) {
            throw new IllegalStateException("chat retrieval and prompt versions must not be blank");
        }
        if (reasoningEffort != null && !reasoningEffort.isBlank()
                && !java.util.Set.of("none", "minimal", "low", "medium", "high", "xhigh")
                .contains(reasoningEffort.toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalStateException("chat.reasoning-effort is invalid");
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

package com.example.arxivrag.ai.foundry;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "foundry")
@Validated
public class FoundryProperties {

    private String endpoint = "";

    private String apiKey = "";

    private String authenticationMode = "api-key";

    private String tokenScope = "https://ai.azure.com/.default";

    private String apiVersion = "2024-10-21";

    private String chatDeployment = "gpt-5.6-luna";

    private String candidateChatDeployment = "";

    private String embeddingDeployment = "text-embedding-3-large";

    @NotNull
    private Duration requestTimeout = Duration.ofSeconds(120);

    @Positive
    private int maxRequestsPerMinute = 150;

    @Positive
    private int maxTokensPerMinute = 150_000;

    @Positive
    private int chatMaxRequestsPerMinute = 150;

    @Positive
    private int chatMaxTokensPerMinute = 150_000;

    @Positive
    private int embeddingMaxRequestsPerMinute = 150;

    @Positive
    private int embeddingMaxTokensPerMinute = 150_000;

    @PositiveOrZero
    private int maxRetries = 3;

    @PositiveOrZero
    private int chatMaxRetries = 1;

    @PositiveOrZero
    private int queryEmbeddingMaxRetries = 1;

    @PositiveOrZero
    private int interactiveReservedRequestsPerMinute = 10;

    @PositiveOrZero
    private int interactiveReservedTokensPerMinute = 10_000;

    @NotNull
    private Duration chatMaxQuotaWait = Duration.ofSeconds(2);

    @NotNull
    private Duration queryEmbeddingMaxQuotaWait = Duration.ofSeconds(2);

    @NotNull
    private Duration ingestionEmbeddingMaxQuotaWait = Duration.ofSeconds(60);

    @NotNull
    private Duration retryInitialBackoff = Duration.ofSeconds(1);

    @NotNull
    private Duration retryMaxBackoff = Duration.ofSeconds(10);

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getAuthenticationMode() { return authenticationMode; }
    public void setAuthenticationMode(String authenticationMode) { this.authenticationMode = authenticationMode; }
    public String getTokenScope() { return tokenScope; }
    public void setTokenScope(String tokenScope) { this.tokenScope = tokenScope; }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getChatDeployment() {
        return chatDeployment;
    }

    public void setChatDeployment(String chatDeployment) {
        this.chatDeployment = chatDeployment;
    }

    public String getCandidateChatDeployment() {
        return candidateChatDeployment;
    }

    public void setCandidateChatDeployment(String value) {
        this.candidateChatDeployment = value;
    }

    public String getEmbeddingDeployment() {
        return embeddingDeployment;
    }

    public void setEmbeddingDeployment(String embeddingDeployment) {
        this.embeddingDeployment = embeddingDeployment;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public int getMaxRequestsPerMinute() {
        return maxRequestsPerMinute;
    }

    public void setMaxRequestsPerMinute(int maxRequestsPerMinute) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    public int getMaxTokensPerMinute() {
        return maxTokensPerMinute;
    }

    public void setMaxTokensPerMinute(int maxTokensPerMinute) {
        this.maxTokensPerMinute = maxTokensPerMinute;
    }

    public int getChatMaxRequestsPerMinute() {
        return chatMaxRequestsPerMinute;
    }

    public void setChatMaxRequestsPerMinute(int value) {
        this.chatMaxRequestsPerMinute = value;
    }

    public int getChatMaxTokensPerMinute() {
        return chatMaxTokensPerMinute;
    }

    public void setChatMaxTokensPerMinute(int value) {
        this.chatMaxTokensPerMinute = value;
    }

    public int getEmbeddingMaxRequestsPerMinute() {
        return embeddingMaxRequestsPerMinute;
    }

    public void setEmbeddingMaxRequestsPerMinute(int value) {
        this.embeddingMaxRequestsPerMinute = value;
    }

    public int getEmbeddingMaxTokensPerMinute() {
        return embeddingMaxTokensPerMinute;
    }

    public void setEmbeddingMaxTokensPerMinute(int value) {
        this.embeddingMaxTokensPerMinute = value;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int getChatMaxRetries() {
        return chatMaxRetries;
    }

    public void setChatMaxRetries(int chatMaxRetries) {
        this.chatMaxRetries = chatMaxRetries;
    }

    public int getQueryEmbeddingMaxRetries() {
        return queryEmbeddingMaxRetries;
    }

    public void setQueryEmbeddingMaxRetries(int queryEmbeddingMaxRetries) {
        this.queryEmbeddingMaxRetries = queryEmbeddingMaxRetries;
    }

    public int getInteractiveReservedRequestsPerMinute() {
        return interactiveReservedRequestsPerMinute;
    }

    public void setInteractiveReservedRequestsPerMinute(int value) {
        this.interactiveReservedRequestsPerMinute = value;
    }

    public int getInteractiveReservedTokensPerMinute() {
        return interactiveReservedTokensPerMinute;
    }

    public void setInteractiveReservedTokensPerMinute(int value) {
        this.interactiveReservedTokensPerMinute = value;
    }

    public Duration getChatMaxQuotaWait() {
        return chatMaxQuotaWait;
    }

    public void setChatMaxQuotaWait(Duration chatMaxQuotaWait) {
        this.chatMaxQuotaWait = chatMaxQuotaWait;
    }

    public Duration getQueryEmbeddingMaxQuotaWait() {
        return queryEmbeddingMaxQuotaWait;
    }

    public void setQueryEmbeddingMaxQuotaWait(Duration queryEmbeddingMaxQuotaWait) {
        this.queryEmbeddingMaxQuotaWait = queryEmbeddingMaxQuotaWait;
    }

    public Duration getIngestionEmbeddingMaxQuotaWait() {
        return ingestionEmbeddingMaxQuotaWait;
    }

    public void setIngestionEmbeddingMaxQuotaWait(Duration ingestionEmbeddingMaxQuotaWait) {
        this.ingestionEmbeddingMaxQuotaWait = ingestionEmbeddingMaxQuotaWait;
    }

    public Duration getRetryInitialBackoff() {
        return retryInitialBackoff;
    }

    public void setRetryInitialBackoff(Duration retryInitialBackoff) {
        this.retryInitialBackoff = retryInitialBackoff;
    }

    public Duration getRetryMaxBackoff() {
        return retryMaxBackoff;
    }

    public void setRetryMaxBackoff(Duration retryMaxBackoff) {
        this.retryMaxBackoff = retryMaxBackoff;
    }
}

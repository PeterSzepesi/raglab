package com.example.arxivrag.ai.foundry;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "foundry")
public class FoundryProperties {

    private String endpoint = "";

    private String apiKey = "";

    private String apiVersion = "2024-10-21";

    private String chatDeployment = "gpt-5.6-luna";

    private String embeddingDeployment = "text-embedding-3-large";

    private Duration requestTimeout = Duration.ofSeconds(120);

    private int maxRequestsPerMinute = 150;

    private int maxTokensPerMinute = 150_000;

    private int maxRetries = 3;

    private Duration retryInitialBackoff = Duration.ofSeconds(1);

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

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
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

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
}

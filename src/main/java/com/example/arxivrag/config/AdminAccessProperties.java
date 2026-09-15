package com.example.arxivrag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rag.admin")
public class AdminAccessProperties {
    private boolean localOnly = true;
    private String token = "";

    public boolean isLocalOnly() { return localOnly; }
    public void setLocalOnly(boolean localOnly) { this.localOnly = localOnly; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token == null ? "" : token; }
    public boolean tokenConfigured() { return !token.isBlank(); }
}

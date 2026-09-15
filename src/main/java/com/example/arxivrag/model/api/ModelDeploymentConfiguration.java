package com.example.arxivrag.model.api;

@FunctionalInterface
public interface ModelDeploymentConfiguration {
    String chatDeployment();
    default String candidateChatDeployment() { return ""; }
}

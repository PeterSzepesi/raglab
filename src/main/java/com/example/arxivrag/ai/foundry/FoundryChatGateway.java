package com.example.arxivrag.ai.foundry;

import com.example.arxivrag.ai.GenerationGateway;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("foundry")
public class FoundryChatGateway implements GenerationGateway {

    private final FoundryClient foundryClient;

    private final FoundryProperties properties;

    public FoundryChatGateway(FoundryClient foundryClient, FoundryProperties properties) {
        this.foundryClient = foundryClient;
        this.properties = properties;
    }

    @Override
    public String modelName() {
        return properties.getChatDeployment();
    }

    @Override
    public String generate(String systemPrompt, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("userMessage must not be blank");
        }
        return foundryClient.chat(modelName(), systemPrompt, userMessage);
    }
}

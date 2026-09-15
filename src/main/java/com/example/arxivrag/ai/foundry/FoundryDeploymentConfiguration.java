package com.example.arxivrag.ai.foundry;

import com.example.arxivrag.model.api.ModelDeploymentConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("foundry")
public class FoundryDeploymentConfiguration implements ModelDeploymentConfiguration {
    private final FoundryProperties foundry;

    public FoundryDeploymentConfiguration(FoundryProperties foundry) {
        this.foundry = foundry;
    }

    @Override public String chatDeployment() { return foundry.getChatDeployment(); }
    @Override public String candidateChatDeployment() { return foundry.getCandidateChatDeployment(); }
}

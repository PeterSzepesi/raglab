package com.example.arxivrag.ai.foundry;

import java.net.URI;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("foundry")
public class FoundryConfigurationValidator implements org.springframework.beans.factory.InitializingBean {

    private final FoundryProperties properties;

    public FoundryConfigurationValidator(FoundryProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        require(properties.getEndpoint(), "foundry.endpoint");
        var authMode = properties.getAuthenticationMode() == null ? "" :
                properties.getAuthenticationMode().trim().toLowerCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("api-key", "managed-identity", "entra").contains(authMode)) {
            throw new IllegalStateException("foundry.authentication-mode must be api-key, managed-identity, or entra");
        }
        if ("api-key".equals(authMode)) require(properties.getApiKey(), "AZURE_AI_FOUNDRY_API_KEY");
        else require(properties.getTokenScope(), "foundry.token-scope");
        require(properties.getApiVersion(), "foundry.api-version");
        require(properties.getChatDeployment(), "foundry.chat-deployment");
        require(properties.getEmbeddingDeployment(), "foundry.embedding-deployment");
        var endpoint = URI.create(properties.getEndpoint());
        if (!"https".equalsIgnoreCase(endpoint.getScheme()) || endpoint.getHost() == null) {
            throw new IllegalStateException("foundry.endpoint must be a valid HTTPS URL");
        }
        if (properties.getRetryInitialBackoff().compareTo(properties.getRetryMaxBackoff()) > 0) {
            throw new IllegalStateException("foundry.retry-initial-backoff must not exceed retry-max-backoff");
        }
        if (properties.getInteractiveReservedRequestsPerMinute()
                >= properties.getEmbeddingMaxRequestsPerMinute()) {
            throw new IllegalStateException("Foundry interactive reserved RPM must be below embedding RPM");
        }
        if (properties.getInteractiveReservedTokensPerMinute()
                >= properties.getEmbeddingMaxTokensPerMinute()) {
            throw new IllegalStateException("Foundry interactive reserved TPM must be below embedding TPM");
        }
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured for the foundry profile");
        }
    }
}

package com.example.arxivrag.ai.foundry;

import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.DefaultAzureCredentialBuilder;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("foundry")
class FoundryAuthenticationConfiguration {

    @Bean
    FoundryAuthentication foundryAuthentication(FoundryProperties properties) {
        return switch (properties.getAuthenticationMode().trim().toLowerCase(Locale.ROOT)) {
            case "api-key" -> request -> request.header("api-key", required(properties.getApiKey(),
                    "AZURE_AI_FOUNDRY_API_KEY"));
            case "managed-identity", "entra" -> {
                var credential = new DefaultAzureCredentialBuilder().build();
                var context = new TokenRequestContext().addScopes(properties.getTokenScope());
                yield request -> {
                    var token = credential.getToken(context).block();
                    if (token == null || token.getToken() == null || token.getToken().isBlank()) {
                        throw new FoundryClient.FoundryProviderException("Microsoft Entra ID returned no access token");
                    }
                    request.header("Authorization", "Bearer " + token.getToken());
                };
            }
            default -> throw new IllegalStateException(
                    "foundry.authentication-mode must be api-key, managed-identity, or entra");
        };
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be configured");
        return value;
    }
}

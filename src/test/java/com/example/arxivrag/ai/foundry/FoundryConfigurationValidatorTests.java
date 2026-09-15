package com.example.arxivrag.ai.foundry;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class FoundryConfigurationValidatorTests {

    @Test
    void acceptsManagedIdentityWithoutApiKey() {
        var properties = validProperties();
        properties.setAuthenticationMode("managed-identity");
        properties.setApiKey("");

        assertThatCode(() -> new FoundryConfigurationValidator(properties).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    void apiKeyModeFailsFastWithoutKey() {
        var properties = validProperties();
        properties.setApiKey("");

        assertThatThrownBy(() -> new FoundryConfigurationValidator(properties).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AZURE_AI_FOUNDRY_API_KEY");
    }

    @Test
    void rejectsInsecureEndpointAndInvalidRetryBudget() {
        var properties = validProperties();
        properties.setEndpoint("http://example.test");
        assertThatThrownBy(() -> new FoundryConfigurationValidator(properties).afterPropertiesSet())
                .hasMessageContaining("HTTPS");

        properties.setEndpoint("https://example.test");
        properties.setRetryInitialBackoff(Duration.ofSeconds(11));
        properties.setRetryMaxBackoff(Duration.ofSeconds(10));
        assertThatThrownBy(() -> new FoundryConfigurationValidator(properties).afterPropertiesSet())
                .hasMessageContaining("retry-initial-backoff");
    }

    private static FoundryProperties validProperties() {
        var properties = new FoundryProperties();
        properties.setEndpoint("https://example.test");
        properties.setApiKey("configured");
        return properties;
    }
}

package com.example.arxivrag.ai.foundry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FoundryChatGatewayTests {

    @Test
    void delegatesToConfiguredChatDeployment() {
        var client = mock(FoundryClient.class);
        var properties = new FoundryProperties();
        properties.setChatDeployment("gpt-5.6-luna");
        when(client.chat("gpt-5.6-luna", "system", "question")).thenReturn("answer");

        var gateway = new FoundryChatGateway(client, properties);

        assertThat(gateway.modelName()).isEqualTo("gpt-5.6-luna");
        assertThat(gateway.generate("system", "question")).isEqualTo("answer");
        verify(client).chat("gpt-5.6-luna", "system", "question");
    }

    @Test
    void rejectsBlankUserMessagesBeforeCallingProvider() {
        var client = mock(FoundryClient.class);
        var gateway = new FoundryChatGateway(client, new FoundryProperties());

        assertThatThrownBy(() -> gateway.generate("system", "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userMessage must not be blank");
    }
}

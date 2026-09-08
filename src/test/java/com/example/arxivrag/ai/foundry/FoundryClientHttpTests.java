package com.example.arxivrag.ai.foundry;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FoundryClientHttpTests {

    @Test
    void embeddingUsesConfiguredDeploymentRouteAndApiKey() throws Exception {
        var properties = properties();
        var httpClient = mock(HttpClient.class);
        var response = response(200, "{\"data\":[{\"embedding\":[0.1,0.2,0.3]}]}");
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any());

        var embedding = new FoundryClient(properties, new ObjectMapper(), httpClient)
                .embed("text-embedding-3-large", "Title: Test", 3);

        assertThat(embedding).containsExactly(0.1, 0.2, 0.3);
        var request = requestFrom(httpClient);
        assertThat(request.uri()).hasToString(
                "https://models-for-peter-resource.services.ai.azure.com/openai/deployments/"
                        + "text-embedding-3-large/embeddings?api-version=2024-10-21");
        assertThat(request.headers().firstValue("api-key")).hasValue("rotated-test-key");
    }

    @Test
    void chatExtractsAssistantContent() throws Exception {
        var httpClient = mock(HttpClient.class);
        doReturn(response(200, "{\"choices\":[{\"message\":{\"content\":\"Grounded later.\"}}]}")
        ).when(httpClient).send(any(HttpRequest.class), any());

        var answer = new FoundryClient(properties(), new ObjectMapper(), httpClient)
                .chat("gpt-5.6-luna", "system", "question");

        assertThat(answer).isEqualTo("Grounded later.");
        assertThat(requestFrom(httpClient).uri()).hasToString(
                "https://models-for-peter-resource.services.ai.azure.com/openai/deployments/"
                        + "gpt-5.6-luna/chat/completions?api-version=2024-10-21");
    }

    @Test
    void nonSuccessfulResponsesBecomeProviderErrorsWithoutRequestContent() throws Exception {
        var httpClient = mock(HttpClient.class);
        doReturn(response(401, "invalid key: do not expose this request")).when(httpClient)
                .send(any(HttpRequest.class), any());

        assertThatThrownBy(() -> new FoundryClient(properties(), new ObjectMapper(), httpClient)
                .chat("gpt-5.6-luna", "secret system prompt", "secret question"))
                .isInstanceOf(FoundryClient.FoundryProviderException.class)
                .hasMessageContaining("HTTP 401")
                .hasMessageNotContaining("secret question")
                .hasMessageNotContaining("secret system prompt");
    }

    private static FoundryProperties properties() {
        var properties = new FoundryProperties();
        properties.setEndpoint("https://models-for-peter-resource.services.ai.azure.com");
        properties.setApiKey("rotated-test-key");
        properties.setApiVersion("2024-10-21");
        return properties;
    }

    private static HttpResponse<String> response(int status, String body) {
        var response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }

    private static HttpRequest requestFrom(HttpClient httpClient) throws Exception {
        var captor = ArgumentCaptor.forClass(HttpRequest.class);
        org.mockito.Mockito.verify(httpClient).send(captor.capture(), any());
        return captor.getValue();
    }
}

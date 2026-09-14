package com.example.arxivrag.ai.foundry;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpHeaders;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.Map;

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
    void embeddingBatchUsesReturnedIndexesToPreserveInputOrder() throws Exception {
        var httpClient = mock(HttpClient.class);
        doReturn(response(200, "{\"data\":["
                + "{\"index\":1,\"embedding\":[0.4,0.5]},"
                + "{\"index\":0,\"embedding\":[0.1,0.2]}]}"))
                .when(httpClient).send(any(HttpRequest.class), any());

        var embeddings = new FoundryClient(properties(), new ObjectMapper(), httpClient)
                .embed("text-embedding-3-large", List.of("first", "second"), 2);

        assertThat(embeddings).containsExactly(List.of(0.1, 0.2), List.of(0.4, 0.5));
    }

    @Test
    void parsesToolCallsAndKeepsAssistantMessageForTheFollowUpRequest() throws Exception {
        var httpClient = mock(HttpClient.class);
        doReturn(response(200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"semantic_search\",\"arguments\":\"{\\\"query\\\":\\\"neural retrieval\\\",\\\"topK\\\":5}\"}}]}}]}")
        ).when(httpClient).send(any(HttpRequest.class), any());

        var result = new FoundryClient(properties(), new ObjectMapper(), httpClient)
                .chatWithTools("gpt-5.6-luna", List.of(Map.of("role", "user", "content", "question")), List.of(Map.of("type", "function")));

        assertThat(result.content()).isNull();
        assertThat(result.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.id()).isEqualTo("call-1");
            assertThat(call.name()).isEqualTo("semantic_search");
            assertThat(call.arguments()).containsEntry("query", "neural retrieval");
            assertThat(call.arguments()).containsEntry("topK", 5);
        });
        assertThat(result.assistantMessage()).containsEntry("role", "assistant");
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

    @Test
    void parsesUsageAndPrefersApimRequestId() throws Exception {
        var httpClient = mock(HttpClient.class);
        var response = response(200, "{\"choices\":[{\"message\":{\"content\":\"answer\"}}],"
                + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":4,\"total_tokens\":14,"
                + "\"prompt_tokens_details\":{\"cached_tokens\":2},"
                + "\"completion_tokens_details\":{\"reasoning_tokens\":1}}}");
        when(response.headers()).thenReturn(HttpHeaders.of(
                Map.of("apim-request-id", List.of("apim-123"), "x-request-id", List.of("x-ignored")),
                (name, value) -> true));
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any());

        var result = new FoundryClient(properties(), new ObjectMapper(), httpClient)
                .chatWithTools("gpt-5.6-luna", List.of(Map.of("role", "user", "content", "question")), List.of());

        assertThat(result.metadata().promptTokens()).isEqualTo(10);
        assertThat(result.metadata().completionTokens()).isEqualTo(4);
        assertThat(result.metadata().reasoningTokens()).isEqualTo(1);
        assertThat(result.metadata().cachedPromptTokens()).isEqualTo(2);
        assertThat(result.metadata().requestId()).isEqualTo("apim-123");
    }

    @Test
    void streamingEmitsDeltasAndParsesFinalUsageAsynchronously() {
        var httpClient = mock(HttpClient.class);
        var stream = "data: {\"choices\":[{\"delta\":{\"content\":\"Grounded \"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\"answer.\"}}]}\n\n"
                + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":9,\"completion_tokens\":2,"
                + "\"total_tokens\":11}}\n\ndata: [DONE]\n\n";
        var response = streamResponse(200, stream, Map.of("apim-request-id", List.of("stream-123")));
        doReturn(CompletableFuture.completedFuture(response)).when(httpClient)
                .sendAsync(any(HttpRequest.class), any());
        var emitted = new AtomicReference<>("");

        var result = new FoundryClient(properties(), new ObjectMapper(), httpClient)
                .streamChat("gpt-5.6-luna", List.of(Map.of("role", "user", "content", "question")),
                        new FoundryClient.FoundryChatOptions(100, null),
                        delta -> emitted.updateAndGet(value -> value + delta));

        assertThat(emitted.get()).isEqualTo("Grounded answer.");
        assertThat(result.content()).isEqualTo("Grounded answer.");
        assertThat(result.metadata().promptTokens()).isEqualTo(9);
        assertThat(result.metadata().completionTokens()).isEqualTo(2);
        assertThat(result.metadata().requestId()).isEqualTo("stream-123");
    }

    @Test
    void streamingRetriesThrottleBeforeAnyDeltaIsEmitted() {
        var httpClient = mock(HttpClient.class);
        var throttled = streamResponse(429, "busy", Map.of("retry-after", List.of("0")));
        var success = streamResponse(200,
                "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}\n\n"
                        + "data: [DONE]\n\n", Map.of());
        doReturn(CompletableFuture.completedFuture(throttled), CompletableFuture.completedFuture(success))
                .when(httpClient).sendAsync(any(HttpRequest.class), any());

        var result = new FoundryClient(properties(), new ObjectMapper(), httpClient)
                .streamChat("gpt-5.6-luna", List.of(Map.of("role", "user", "content", "question")),
                        FoundryClient.FoundryChatOptions.defaults(), ignored -> { });

        assertThat(result.content()).isEqualTo("ok");
        assertThat(result.metadata().retryCount()).isEqualTo(1);
    }

    @Test
    void cancellingStreamingTaskCancelsRequestBeforeResponseHeadersArrive() throws Exception {
        var httpClient = mock(HttpClient.class);
        var providerFuture = new CompletableFuture<HttpResponse<java.io.InputStream>>();
        var providerCancelled = new CountDownLatch(1);
        providerFuture.whenComplete((ignored, failure) -> {
            if (providerFuture.isCancelled()) {
                providerCancelled.countDown();
            }
        });
        var requestStarted = new CountDownLatch(1);
        when(httpClient.sendAsync(any(HttpRequest.class), any())).thenAnswer(invocation -> {
            requestStarted.countDown();
            return providerFuture;
        });
        var executor = Executors.newSingleThreadExecutor();
        try {
            var task = executor.submit(() -> new FoundryClient(properties(), new ObjectMapper(), httpClient)
                    .streamChat("gpt-5.6-luna", List.of(Map.of("role", "user", "content", "question")),
                            FoundryClient.FoundryChatOptions.defaults(), ignored -> { }));
            assertThat(requestStarted.await(1, TimeUnit.SECONDS)).isTrue();

            task.cancel(true);

            assertThat(providerCancelled.await(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }
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

    private static HttpResponse<java.io.InputStream> streamResponse(
            int status, String body, Map<String, List<String>> headers) {
        var response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        when(response.headers()).thenReturn(HttpHeaders.of(headers, (name, value) -> true));
        return response;
    }

    private static HttpRequest requestFrom(HttpClient httpClient) throws Exception {
        var captor = ArgumentCaptor.forClass(HttpRequest.class);
        org.mockito.Mockito.verify(httpClient).send(captor.capture(), any());
        return captor.getValue();
    }
}

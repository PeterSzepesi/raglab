package com.example.arxivrag.ai.chat;

import com.example.arxivrag.ai.foundry.FoundryClient;
import com.example.arxivrag.ai.foundry.FoundryProperties;
import com.example.arxivrag.vector.SemanticSearchResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GroundedChatPreRetrievalTests {

    @Test
    void ordinaryQuestionUsesOneAnswerOnlyFoundryCall() {
        var client = mock(FoundryClient.class);
        var planner = mock(ChatRetrievalPlanner.class);
        var properties = new FoundryProperties();
        properties.setChatDeployment("gpt-5.6-luna");
        var chatProperties = new ChatProperties();
        var result = new SemanticSearchResult(1L, "2401.00001", "Paper", "Evidence", List.of(),
                List.of("cs.AI"), LocalDate.of(2024, 1, 1), 0.9, "Evidence");
        when(planner.retrieve(any(ChatRequest.class), any(ChatTiming.class)))
                .thenReturn(new ChatRetrieval(List.of(result), List.of("semantic_search")));
        when(client.chatWithTools(eq("gpt-5.6-luna"), anyList(), eq(List.of()), any()))
                .thenReturn(new FoundryClient.FoundryChatResult(
                        "Grounded [arXiv:2401.00001].", List.of(), Map.of("role", "assistant")));
        var registry = new SimpleMeterRegistry();
        var service = new GroundedChatService(client, properties, mock(RetrievalToolService.class),
                new ObjectMapper(), chatProperties, planner,
                new GroundedContextBuilder(chatProperties, new ObjectMapper()),
                new GroundedAnswerSanitizer(), new ChatMetrics(registry));

        var response = service.chat(new ChatRequest("What is the evidence?"));

        assertThat(response.answer()).contains("2401.00001");
        assertThat(response.citations()).extracting(ChatCitation::arxivId).containsExactly("2401.00001");
        verify(client, times(1)).chatWithTools(eq("gpt-5.6-luna"), anyList(), eq(List.of()), any());
        service.close();
    }

    @Test
    void streamingPublishesRetrievalAndCitationsBeforeAnswerDeltas() {
        var client = mock(FoundryClient.class);
        var planner = mock(ChatRetrievalPlanner.class);
        var foundryProperties = new FoundryProperties();
        foundryProperties.setChatDeployment("gpt-5.6-luna");
        var chatProperties = new ChatProperties();
        var result = new SemanticSearchResult(1L, "2401.00001", "Paper", "Evidence", List.of(),
                List.of("cs.AI"), LocalDate.of(2024, 1, 1), 0.9, "Evidence");
        when(planner.retrieve(any(ChatRequest.class), any(ChatTiming.class)))
                .thenReturn(new ChatRetrieval(List.of(result), List.of("semantic_search")));
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            var consumer = (java.util.function.Consumer<String>) invocation.getArgument(3);
            consumer.accept("Grounded ");
            consumer.accept("[arXiv:2401.00001].");
            return new FoundryClient.FoundryStreamResult(
                    "Grounded [arXiv:2401.00001].", FoundryClient.ProviderMetadata.unknown());
        }).when(client).streamChat(eq("gpt-5.6-luna"), anyList(), any(), any());
        var service = new GroundedChatService(client, foundryProperties, mock(RetrievalToolService.class),
                new ObjectMapper(), chatProperties, planner,
                new GroundedContextBuilder(chatProperties, new ObjectMapper()),
                new GroundedAnswerSanitizer(), new ChatMetrics(new SimpleMeterRegistry()));
        var events = new ArrayList<String>();

        service.stream(new ChatRequest("What is the evidence?"), new ChatStreamListener() {
            @Override public void retrievalStarted() { events.add("retrieval"); }
            @Override public void citations(List<ChatCitation> citations) { events.add("citations"); }
            @Override public void delta(String text) { events.add("delta:" + text); }
            @Override public void complete(GroundedChatResponse response, ChatTiming timing) { events.add("complete"); }
        });

        assertThat(events).containsExactly(
                "retrieval", "citations", "delta:Grounded ", "delta:[arXiv:2401.00001].", "complete");
        service.close();
    }
}

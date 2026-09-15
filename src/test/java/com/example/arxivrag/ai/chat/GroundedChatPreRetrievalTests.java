package com.example.arxivrag.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.arxivrag.ai.foundry.FoundryProperties;
import com.example.arxivrag.model.api.ChatModelGateway;
import com.example.arxivrag.vector.SemanticSearchResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GroundedChatPreRetrievalTests {

    @Test
    void noEvidenceReturnsLocallyWithoutCallingFoundry() {
        var client = mock(ChatModelGateway.class);
        var planner = mock(ChatRetrievalPlanner.class);
        var properties = new FoundryProperties();
        properties.setChatDeployment("gpt-5.6-luna");
        var chatProperties = new ChatProperties();
        when(planner.retrieve(any(ChatRequest.class), any(ChatTiming.class)))
                .thenReturn(new ChatRetrieval(List.of(), List.of("semantic_search"),
                        ChatRetrievalOutcome.NO_EVIDENCE));
        var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        var service = ChatServiceFixture.create(client, properties::getChatDeployment, mock(RetrievalToolService.class),
                new ObjectMapper(), chatProperties, planner,
                new GroundedContextBuilder(chatProperties, new ObjectMapper()),
                new GroundedAnswerSanitizer(), new ChatMetrics(new SimpleMeterRegistry()), executor,
                new QuestionIntentClassifier(), answerCache(chatProperties),
                new ChatPromptFactory(chatProperties, new QuestionIntentClassifier()),
                new ChatRouteSelector(chatProperties));

        var response = service.chat(new ChatRequest("Evidence that is not in the corpus"));

        assertThat(response.groundingStatus()).isEqualTo("NO_EVIDENCE");
        assertThat(response.answer()).contains("supporting evidence");
        assertThat(response.citations()).isEmpty();
        verifyNoInteractions(client);
        executor.shutdownNow();
    }

    @Test
    void noEvidenceStreamHasSameLocalResultAndDoesNotCallFoundry() {
        var client = mock(ChatModelGateway.class);
        var planner = mock(ChatRetrievalPlanner.class);
        var properties = new FoundryProperties();
        var chatProperties = new ChatProperties();
        when(planner.retrieve(any(ChatRequest.class), any(ChatTiming.class)))
                .thenReturn(new ChatRetrieval(List.of(), List.of("semantic_search"),
                        ChatRetrievalOutcome.NO_EVIDENCE));
        var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        var service = ChatServiceFixture.create(client, properties::getChatDeployment, mock(RetrievalToolService.class),
                new ObjectMapper(), chatProperties, planner,
                new GroundedContextBuilder(chatProperties, new ObjectMapper()),
                new GroundedAnswerSanitizer(), new ChatMetrics(new SimpleMeterRegistry()), executor,
                new QuestionIntentClassifier(), answerCache(chatProperties),
                new ChatPromptFactory(chatProperties, new QuestionIntentClassifier()),
                new ChatRouteSelector(chatProperties));
        var deltas = new ArrayList<String>();
        var completed = new ArrayList<GroundedChatResponse>();

        service.stream(new ChatRequest("No matching evidence"), new ChatStreamListener() {
            @Override public void delta(String text) { deltas.add(text); }
            @Override public void complete(GroundedChatResponse response, ChatTiming timing) {
                completed.add(response);
            }
        });

        assertThat(deltas).singleElement().asString().contains("supporting evidence");
        assertThat(completed).singleElement().extracting(GroundedChatResponse::groundingStatus)
                .isEqualTo("NO_EVIDENCE");
        verifyNoInteractions(client);
        executor.shutdownNow();
    }

    @Test
    void ordinaryQuestionUsesOneAnswerOnlyFoundryCall() {
        var client = mock(ChatModelGateway.class);
        var planner = mock(ChatRetrievalPlanner.class);
        var properties = new FoundryProperties();
        properties.setChatDeployment("gpt-5.6-luna");
        var chatProperties = new ChatProperties();
        var result = new SemanticSearchResult(1L, "2401.00001", "Paper", "Evidence", List.of(),
                List.of("cs.AI"), LocalDate.of(2024, 1, 1), 0.9, "Evidence");
        when(planner.retrieve(any(ChatRequest.class), any(ChatTiming.class)))
                .thenReturn(new ChatRetrieval(List.of(result), List.of("semantic_search")));
        when(client.generate(eq("gpt-5.6-luna"), anyList(), eq(List.of()), any()))
                .thenReturn(new ChatModelGateway.ChatModelResult(
                        "Grounded [arXiv:2401.00001].", List.of(), Map.of("role", "assistant")));
        var registry = new SimpleMeterRegistry();
        var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        var service = ChatServiceFixture.create(client, properties::getChatDeployment, mock(RetrievalToolService.class),
                new ObjectMapper(), chatProperties, planner,
                new GroundedContextBuilder(chatProperties, new ObjectMapper()),
                new GroundedAnswerSanitizer(), new ChatMetrics(registry), executor,
                new QuestionIntentClassifier(), answerCache(chatProperties),
                new ChatPromptFactory(chatProperties, new QuestionIntentClassifier()),
                new ChatRouteSelector(chatProperties));

        var response = service.chat(new ChatRequest("What is the evidence?"));

        assertThat(response.answer()).contains("2401.00001");
        assertThat(response.citations()).extracting(ChatCitation::arxivId).containsExactly("2401.00001");
        verify(client, times(1)).generate(eq("gpt-5.6-luna"), anyList(), eq(List.of()), any());
        executor.shutdownNow();
    }

    @Test
    void streamingPublishesRetrievalAndCitationsBeforeAnswerDeltas() {
        var client = mock(ChatModelGateway.class);
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
            return new ChatModelGateway.ChatModelStreamResult(
                    "Grounded [arXiv:2401.00001].", ChatModelGateway.ProviderCallMetadata.unknown());
        }).when(client).stream(eq("gpt-5.6-luna"), anyList(), any(), any());
        var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        var service = ChatServiceFixture.create(client, foundryProperties::getChatDeployment, mock(RetrievalToolService.class),
                new ObjectMapper(), chatProperties, planner,
                new GroundedContextBuilder(chatProperties, new ObjectMapper()),
                new GroundedAnswerSanitizer(), new ChatMetrics(new SimpleMeterRegistry()), executor,
                new QuestionIntentClassifier(), answerCache(chatProperties),
                new ChatPromptFactory(chatProperties, new QuestionIntentClassifier()),
                new ChatRouteSelector(chatProperties));
        var events = new ArrayList<String>();

        service.stream(new ChatRequest("What is the evidence?"), new ChatStreamListener() {
            @Override public void retrievalStarted() { events.add("retrieval"); }
            @Override public void citations(List<ChatCitation> citations) { events.add("citations"); }
            @Override public void delta(String text) { events.add("delta:" + text); }
            @Override public void complete(GroundedChatResponse response, ChatTiming timing) { events.add("complete"); }
        });

        assertThat(events).containsExactly(
                "retrieval", "citations", "delta:Grounded ", "delta:[arXiv:2401.00001].", "complete");
        executor.shutdownNow();
    }

    private static ChatAnswerCache answerCache(ChatProperties properties) {
        return new ChatAnswerCache(properties, new com.example.arxivrag.search.CorpusGeneration(),
                new SimpleMeterRegistry());
    }
}

package com.example.arxivrag.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.arxivrag.ai.foundry.FoundryProperties;
import com.example.arxivrag.model.api.ChatModelGateway;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GroundedChatServiceTests {

    @Test
    void executesRequestedToolAndReturnsCitationsFromLocalTool() {
        var client = mock(ChatModelGateway.class);
        var tools = mock(RetrievalToolService.class);
        var properties = new FoundryProperties();
        properties.setChatDeployment("gpt-5.6-luna");
        when(tools.toolDefinitions()).thenReturn(List.of());
        var citation = new ChatCitation(1L, "2401.00001", "Neural retrieval", 0.91d);
        when(tools.execute("semantic_search", Map.of("query", "neural retrieval")))
                .thenReturn(new ToolExecution("semantic_search", List.of(Map.of("title", "Neural retrieval")), List.of(citation)));
        when(client.generate(any(), any(), any(), any()))
                .thenReturn(
                        new ChatModelGateway.ChatModelResult(
                                null,
                                List.of(new ChatModelGateway.ToolCall(
                                        "call-1", "semantic_search", Map.of("query", "neural retrieval"))),
                                Map.of("role", "assistant", "tool_calls", List.of())
                        ),
                        new ChatModelGateway.ChatModelResult(
                                "The evidence is about neural retrieval [arXiv:2401.00001].",
                                List.of(),
                                Map.of("role", "assistant", "content", "The evidence is about neural retrieval [arXiv:2401.00001].")
                        )
                );

        var chatProperties = new ChatProperties();
        chatProperties.setOrchestrationMode("agentic");
        chatProperties.setMaxToolRounds(3);
        var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        var service = ChatServiceFixture.create(client, properties::getChatDeployment, tools, new ObjectMapper(), chatProperties,
                mock(ChatRetrievalPlanner.class), new GroundedContextBuilder(chatProperties, new ObjectMapper()),
                new GroundedAnswerSanitizer(), new ChatMetrics(new SimpleMeterRegistry()), executor,
                new QuestionIntentClassifier(), new ChatAnswerCache(chatProperties,
                        new com.example.arxivrag.search.CorpusGeneration(), new SimpleMeterRegistry()),
                new ChatPromptFactory(chatProperties, new QuestionIntentClassifier()),
                new ChatRouteSelector(chatProperties));

        var response = service.chat("What is this about?");

        assertThat(response.answer()).contains("2401.00001");
        assertThat(response.citations()).containsExactly(citation);
        assertThat(response.citations().getFirst().url()).isEqualTo("https://arxiv.org/abs/2401.00001");
        assertThat(response.toolsUsed()).containsExactly("semantic_search");
        executor.shutdownNow();
    }
}

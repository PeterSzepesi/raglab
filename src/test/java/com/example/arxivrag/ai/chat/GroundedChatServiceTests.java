package com.example.arxivrag.ai.chat;

import com.example.arxivrag.ai.foundry.FoundryClient;
import com.example.arxivrag.ai.foundry.FoundryProperties;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GroundedChatServiceTests {

    @Test
    void executesRequestedToolAndReturnsCitationsFromLocalTool() {
        var client = mock(FoundryClient.class);
        var tools = mock(RetrievalToolService.class);
        var properties = new FoundryProperties();
        properties.setChatDeployment("gpt-5.6-luna");
        when(tools.toolDefinitions()).thenReturn(List.of());
        var citation = new ChatCitation(1L, "2401.00001", "Neural retrieval", 0.91d);
        when(tools.execute("semantic_search", Map.of("query", "neural retrieval")))
                .thenReturn(new ToolExecution("semantic_search", List.of(Map.of("title", "Neural retrieval")), List.of(citation)));
        when(client.chatWithTools(any(), any(), any()))
                .thenReturn(
                        new FoundryClient.FoundryChatResult(
                                null,
                                List.of(new FoundryClient.FoundryToolCall(
                                        "call-1", "semantic_search", Map.of("query", "neural retrieval"))),
                                Map.of("role", "assistant", "tool_calls", List.of())
                        ),
                        new FoundryClient.FoundryChatResult(
                                "The evidence is about neural retrieval [arXiv:2401.00001].",
                                List.of(),
                                Map.of("role", "assistant", "content", "The evidence is about neural retrieval [arXiv:2401.00001].")
                        )
                );

        var response = new GroundedChatService(client, properties, tools, new ObjectMapper())
                .chat("What is this about?");

        assertThat(response.answer()).contains("2401.00001");
        assertThat(response.citations()).containsExactly(citation);
        assertThat(response.citations().getFirst().url()).isEqualTo("https://arxiv.org/abs/2401.00001");
        assertThat(response.toolsUsed()).containsExactly("semantic_search");
    }
}

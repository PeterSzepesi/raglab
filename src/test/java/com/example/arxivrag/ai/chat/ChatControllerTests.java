package com.example.arxivrag.ai.chat;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatControllerTests {

    @Test
    void returnsCorrelationAndServerTimingHeaders() {
        var service = mock(GroundedChatService.class);
        var timing = new ChatTiming(null);
        timing.add(ChatStage.RETRIEVAL, Duration.ofMillis(12));
        timing.add(ChatStage.ANSWER_MODEL, Duration.ofMillis(34));
        var response = new GroundedChatResponse("answer", "model", List.of(), List.of());
        var request = new ChatRequest("question");
        when(service.chatDetailed(request)).thenReturn(new ChatExecution(response, timing));
        var controller = new ChatController(service);

        var entity = controller.chat(request);

        assertThat(entity.getHeaders().getFirst("X-Request-ID")).isEqualTo(timing.requestId());
        assertThat(entity.getHeaders().getFirst("Server-Timing"))
                .contains("retrieval;dur=12", "answer-model;dur=34");
        controller.close();
    }
}

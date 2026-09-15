package com.example.arxivrag.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.arxivrag.config.ExecutionProperties;
import com.example.arxivrag.config.InteractiveAdmissionController;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatControllerTests {

    @Test
    void returnsCorrelationAndServerTimingHeaders() {
        var requests = mock(ChatRequestExecutor.class);
        var timing = new ChatTiming(null);
        timing.add(ChatStage.RETRIEVAL, Duration.ofMillis(12));
        timing.add(ChatStage.ANSWER_MODEL, Duration.ofMillis(34));
        var response = new GroundedChatResponse("answer", "model", List.of(), List.of());
        var request = new ChatRequest("question");
        when(requests.execute(request)).thenReturn(new ChatExecution(response, timing));
        var controller = new ChatController(requests, new ChatProperties(), mock(ChatStreamPublisher.class),
                new InteractiveAdmissionController(new ExecutionProperties(), new SimpleMeterRegistry()));

        var entity = controller.chat(request);

        assertThat(entity.getHeaders().getFirst("X-Request-ID")).isEqualTo(timing.requestId());
        assertThat(entity.getHeaders().getFirst("Server-Timing"))
                .contains("retrieval;dur=12", "answer-model;dur=34");
    }
}

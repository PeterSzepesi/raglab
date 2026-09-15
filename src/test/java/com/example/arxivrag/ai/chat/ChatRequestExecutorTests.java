package com.example.arxivrag.ai.chat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class ChatRequestExecutorTests {

    @Test
    void cancelsWorkWhenTheEndToEndDeadlineExpires() {
        var useCase = mock(ChatUseCase.class);
        when(useCase.chatDetailed(any())).thenAnswer(ignored -> {
            Thread.sleep(Duration.ofSeconds(5));
            return null;
        });
        var properties = new ChatProperties();
        properties.setRequestDeadline(Duration.ofMillis(20));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var requests = new ChatRequestExecutor(useCase, properties, executor);

            assertThatThrownBy(() -> requests.execute(new ChatRequest("question")))
                    .isInstanceOf(ChatDeadlineExceededException.class)
                    .hasMessageContaining("deadline");
        }
    }
}

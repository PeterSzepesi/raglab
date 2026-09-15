package com.example.arxivrag.ai.foundry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class ProviderCircuitBreakerTests {

    @Test
    void failuresOpenOnlyTheAffectedOperationAndDeployment() {
        var breaker = new ProviderCircuitBreaker(new SimpleMeterRegistry());
        for (var index = 0; index < 5; index++) {
            try (var permit = breaker.acquire("chat-a", FoundryOperation.CHAT)) {
                permit.failure();
            }
        }

        assertThatThrownBy(() -> breaker.acquire("chat-a", FoundryOperation.CHAT))
                .isInstanceOf(ProviderCircuitBreaker.CircuitOpenException.class);
        assertThatCode(() -> {
            try (var permit = breaker.acquire("embedding-a", FoundryOperation.QUERY_EMBEDDING)) {
                permit.success();
            }
        }).doesNotThrowAnyException();
        assertThat(breaker.states()).containsEntry("chat:chat-a", ProviderCircuitBreaker.State.OPEN)
                .containsEntry("query-embedding:embedding-a", ProviderCircuitBreaker.State.CLOSED);
    }
}

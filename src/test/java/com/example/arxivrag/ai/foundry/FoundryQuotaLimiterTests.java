package com.example.arxivrag.ai.foundry;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FoundryQuotaLimiterTests {

    @Test
    void backgroundTrafficCannotConsumeReservedInteractiveRequest() {
        var limiter = new FoundryQuotaLimiter(2, 0);
        limiter.acquire(1, true, 1, 0, Duration.ofMillis(50));

        assertThatThrownBy(() -> limiter.acquire(1, true, 1, 0, Duration.ofMillis(20)))
                .isInstanceOf(FoundryClient.FoundryProviderException.class)
                .hasMessageContaining("quota wait exceeded");
        assertThatCode(() -> limiter.acquire(1, false, 1, 0, Duration.ofMillis(20)))
                .doesNotThrowAnyException();
    }
}

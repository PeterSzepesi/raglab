package com.example.arxivrag.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class InteractiveAdmissionControllerTests {

    @Test
    void rejectsWhenAllChatCapacityIsInUse() {
        var properties = new ExecutionProperties();
        properties.setMaxConcurrentChats(1);
        properties.setAdmissionWait(Duration.ofMillis(1));
        var admission = new InteractiveAdmissionController(properties, new SimpleMeterRegistry());

        try (var ignored = admission.acquireChat()) {
            assertThatThrownBy(admission::acquireChat)
                    .isInstanceOf(CapacityExceededException.class)
                    .hasMessageContaining("capacity");
        }
    }

    @Test
    void independentlyRejectsWhenAllRetrievalCapacityIsInUse() {
        var properties = new ExecutionProperties();
        properties.setMaxConcurrentChats(2);
        properties.setMaxConcurrentRetrievals(1);
        properties.setAdmissionWait(Duration.ofMillis(1));
        var admission = new InteractiveAdmissionController(properties, new SimpleMeterRegistry());

        try (var ignored = admission.acquireRetrieval()) {
            assertThatThrownBy(admission::acquireRetrieval)
                    .isInstanceOf(CapacityExceededException.class)
                    .hasMessageContaining("Search capacity");
        }

        try (var ignored = admission.acquireChat()) {
            // The search bulkhead must not consume chat permits.
        }
    }
}

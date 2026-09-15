package com.example.arxivrag.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;

class ChatRouteSelectorTests {

    @Test
    void explicitRequestOverridesConfiguredDefault() {
        var properties = new ChatProperties();
        properties.setOrchestrationMode("agentic");
        var selector = new ChatRouteSelector(properties);

        assertThat(selector.select(new ChatRequest("question", null, null, null, null, false)))
                .isEqualTo(ChatRouteSelector.Route.PRE_RETRIEVAL);
        assertThat(selector.select(new ChatRequest("question")))
                .isEqualTo(ChatRouteSelector.Route.AGENTIC);
    }

    @Test
    void contradictoryDatesSelectALocalAnswerEvenWhenAgenticWasRequested() {
        var selector = new ChatRouteSelector(new ChatProperties());

        var route = selector.select(new ChatRequest("papers", null, null,
                LocalDate.of(2024, 2, 1), LocalDate.of(2024, 1, 1), true));

        assertThat(route).isEqualTo(ChatRouteSelector.Route.LOCAL_INVALID_FILTER);
    }
}

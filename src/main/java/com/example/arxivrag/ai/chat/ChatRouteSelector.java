package com.example.arxivrag.ai.chat;

import org.springframework.stereotype.Component;

/** One deterministic routing decision shared by synchronous and streaming chat. */
@Component
public class ChatRouteSelector {

    private final ChatProperties properties;

    public ChatRouteSelector(ChatProperties properties) {
        this.properties = properties;
    }

    public Route select(ChatRequest request) {
        if (request.fromDate() != null && request.toDate() != null
                && request.fromDate().isAfter(request.toDate())) {
            return Route.LOCAL_INVALID_FILTER;
        }
        var agentic = Boolean.TRUE.equals(request.agentic())
                || request.agentic() == null && properties.mode() == ChatOrchestrationMode.AGENTIC;
        return agentic ? Route.AGENTIC : Route.PRE_RETRIEVAL;
    }

    public enum Route { LOCAL_INVALID_FILTER, PRE_RETRIEVAL, AGENTIC }
}

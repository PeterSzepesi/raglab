package com.example.arxivrag.ai.chat;

import com.example.arxivrag.ai.foundry.FoundryClient;
import com.example.arxivrag.ai.foundry.FoundryProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Profile("foundry")
public class GroundedChatService {

    private static final int MAX_TOOL_ROUNDS = 3;

    private static final String SYSTEM_PROMPT = """
            You are a research assistant for an arXiv paper corpus.
            Use the provided retrieval tools before answering any question about papers or research.
            Use semantic_search for conceptual similarity, relational_paper_search for exact metadata/category/date constraints,
            and paper_lookup when an exact arXiv identifier is mentioned.
            Treat tool output as the only evidence. Do not invent papers, facts, scores, or citations.
            Cite supporting papers inline using [arXiv:<id>]. If retrieval returns no evidence, say that clearly.
            Keep the answer concise and distinguish evidence from your own explanation.
            """;

    private final FoundryClient foundryClient;

    private final FoundryProperties foundryProperties;

    private final RetrievalToolService retrievalToolService;

    private final ObjectMapper objectMapper;

    public GroundedChatService(
            FoundryClient foundryClient,
            FoundryProperties foundryProperties,
            RetrievalToolService retrievalToolService,
            ObjectMapper objectMapper
    ) {
        this.foundryClient = foundryClient;
        this.foundryProperties = foundryProperties;
        this.retrievalToolService = retrievalToolService;
        this.objectMapper = objectMapper;
    }

    public GroundedChatResponse chat(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }

        var messages = new ArrayList<Map<String, Object>>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.add(Map.of("role", "user", "content", message.trim()));

        var citations = new LinkedHashMap<String, ChatCitation>();
        var toolsUsed = new ArrayList<String>();
        for (var round = 0; round < MAX_TOOL_ROUNDS; round++) {
            var result = foundryClient.chatWithTools(
                    foundryProperties.getChatDeployment(),
                    messages,
                    retrievalToolService.toolDefinitions()
            );
            if (result.toolCalls().isEmpty()) {
                return new GroundedChatResponse(
                        result.content(),
                        foundryProperties.getChatDeployment(),
                        List.copyOf(citations.values()),
                        List.copyOf(toolsUsed)
                );
            }

            messages.add(result.assistantMessage());
            for (var toolCall : result.toolCalls()) {
                toolsUsed.add(toolCall.name());
                String toolContent;
                try {
                    var execution = retrievalToolService.execute(toolCall.name(), toolCall.arguments());
                    execution.citations().forEach(citation -> citations.putIfAbsent(citation.arxivId(), citation));
                    toolContent = json(execution.result());
                } catch (IllegalArgumentException exception) {
                    toolContent = json(Map.of("error", exception.getMessage()));
                }
                messages.add(Map.of(
                        "role", "tool",
                        "tool_call_id", toolCall.id(),
                        "content", toolContent
                ));
            }
        }
        throw new FoundryClient.FoundryProviderException(
                "Foundry exceeded the maximum retrieval tool rounds of " + MAX_TOOL_ROUNDS);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new FoundryClient.FoundryProviderException("Could not serialize retrieval tool result", exception);
        }
    }
}

package com.example.arxivrag.ai.chat;

import com.example.arxivrag.model.api.ChatModelGateway;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Owns the stable, versioned prompt prefix and request-specific model options. */
@Component
public class ChatPromptFactory {

    private static final String AGENTIC_SYSTEM_PROMPT = """
            You are a research assistant for an arXiv paper corpus.
            Use the provided retrieval tools before answering questions about papers or research.
            Treat tool output as the only evidence. Never invent papers, facts, scores, or citations.
            Cite supporting papers inline using [arXiv:<id>]. If retrieval returns no evidence, say that clearly.
            Keep the answer concise and distinguish evidence from your own explanation.
            """;

    private static final String ANSWER_SYSTEM_PROMPT = """
            You are a concise research assistant for a local arXiv corpus.
            The EVIDENCE JSON supplied by the application is the only factual source you may use.
            Cite claims inline only as [arXiv:<id>] where that exact ID occurs in EVIDENCE.
            Never invent or alter an ID, paper, result, score, or URL.
            If EVIDENCE is empty or insufficient, clearly say the local corpus has no supporting evidence.
            """;

    private final ChatProperties properties;
    private final QuestionIntentClassifier intentClassifier;

    public ChatPromptFactory(ChatProperties properties, QuestionIntentClassifier intentClassifier) {
        this.properties = properties;
        this.intentClassifier = intentClassifier;
    }

    public List<Map<String, Object>> answerMessages(String question, String evidenceJson) {
        return List.of(
                Map.of("role", "system", "content", ANSWER_SYSTEM_PROMPT),
                Map.of("role", "user", "content", "QUESTION:\n" + question + "\n\nEVIDENCE:\n" + evidenceJson));
    }

    public List<Map<String, Object>> agenticMessages(String question) {
        return new java.util.ArrayList<>(List.of(
                Map.of("role", "system", "content", AGENTIC_SYSTEM_PROMPT),
                Map.of("role", "user", "content", question)));
    }

    public ChatModelGateway.ChatModelOptions options(String question) {
        var intent = intentClassifier.classify(question, properties.getRetrievalTopK(),
                properties.getMaxCompletionTokens());
        return options(intent.maxCompletionTokens());
    }

    public ChatModelGateway.ChatModelOptions options(String question, RetrievalPolicy policy) {
        return options(policy == null ? intentClassifier.classify(question, properties.getRetrievalTopK(),
                properties.getMaxCompletionTokens()).maxCompletionTokens() : policy.maxCompletionTokens());
    }

    private ChatModelGateway.ChatModelOptions options(int maxCompletionTokens) {
        return new ChatModelGateway.ChatModelOptions(
                maxCompletionTokens, properties.getTemperature(), properties.getReasoningEffort(),
                properties.isProviderPromptCacheEnabled() ? properties.getPromptVersion() : null);
    }
}

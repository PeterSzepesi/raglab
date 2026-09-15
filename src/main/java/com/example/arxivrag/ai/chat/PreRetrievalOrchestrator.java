package com.example.arxivrag.ai.chat;

import com.example.arxivrag.model.api.ChatModelGateway;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default one-retrieval/one-generation RAG path, shared by sync and streaming APIs. */
@Component
@ConditionalOnProperty(name = "rag.features.chat-enabled", havingValue = "true")
public class PreRetrievalOrchestrator {
    private final ChatModelGateway chatModel;
    private final ChatRetrievalPlanner retrievalPlanner;
    private final GroundedContextBuilder contextBuilder;
    private final GroundedAnswerSanitizer sanitizer;
    private final ChatProperties properties;
    private final QuestionIntentClassifier intentClassifier;
    private final ChatAnswerCache answerCache;
    private final ChatPromptFactory prompts;
    private final ChatTelemetry telemetry;

    public PreRetrievalOrchestrator(ChatModelGateway chatModel, ChatRetrievalPlanner retrievalPlanner,
                                    GroundedContextBuilder contextBuilder, GroundedAnswerSanitizer sanitizer,
                                    ChatProperties properties, QuestionIntentClassifier intentClassifier,
                                    ChatAnswerCache answerCache, ChatPromptFactory prompts,
                                    ChatTelemetry telemetry) {
        this.chatModel = chatModel;
        this.retrievalPlanner = retrievalPlanner;
        this.contextBuilder = contextBuilder;
        this.sanitizer = sanitizer;
        this.properties = properties;
        this.intentClassifier = intentClassifier;
        this.answerCache = answerCache;
        this.prompts = prompts;
        this.telemetry = telemetry;
    }

    public GroundedChatResponse execute(ChatRequest request, ChatTiming timing, String deployment) {
        var prepared = prepare(request, timing, deployment, null);
        if (prepared.terminal() != null) return prepared.terminal();
        telemetry.providerCall();
        var result = timing.measure(ChatStage.ANSWER_MODEL, () -> chatModel.generate(
                deployment, prompts.answerMessages(request.message().trim(), prepared.context().evidenceJson()),
                List.of(), prompts.options(request.message(), prepared.policy())));
        telemetry.providerMetadata(result.metadata(), timing);
        var response = grounded(result.content(), deployment, prepared.context());
        answerCache.put(prepared.answerKey(), response);
        return response;
    }

    public GroundedChatResponse stream(ChatRequest request, ChatTiming timing, String deployment,
                                       ChatStreamListener listener) {
        listener.retrievalStarted();
        var prepared = prepare(request, timing, deployment, listener);
        if (prepared.terminal() != null) {
            listener.delta(prepared.terminal().answer());
            return prepared.terminal();
        }
        telemetry.providerCall();
        var streamStarted = System.nanoTime();
        var firstTokenSeen = new AtomicBoolean();
        var result = timing.measure(ChatStage.ANSWER_MODEL, () -> chatModel.stream(
                deployment, prompts.answerMessages(request.message().trim(), prepared.context().evidenceJson()),
                prompts.options(request.message(), prepared.policy()), delta -> {
                    if (firstTokenSeen.compareAndSet(false, true)) {
                        var ttft = Duration.ofNanos(System.nanoTime() - streamStarted);
                        timing.add(ChatStage.FIRST_TOKEN, ttft);
                        telemetry.firstToken(ttft);
                    }
                    listener.delta(delta);
                }));
        telemetry.providerMetadata(result.metadata(), timing);
        var response = grounded(result.content(), deployment, prepared.context());
        answerCache.put(prepared.answerKey(), response);
        return response;
    }

    private Prepared prepare(ChatRequest request, ChatTiming timing, String deployment, ChatStreamListener listener) {
        var retrieval = timing.measure(ChatStage.RETRIEVAL, () -> retrievalPlanner.retrieve(request, timing));
        var context = contextBuilder.build(retrieval, request.message());
        if (listener != null) listener.citations(context.citations());
        if (context.citations().isEmpty()) {
            return new Prepared(context, null, localNoEvidence(retrieval, deployment, context.toolsUsed(), timing),
                    retrieval.policy());
        }
        var intent = intentClassifier.classify(request.message(), properties.getRetrievalTopK(),
                properties.getMaxCompletionTokens());
        var key = answerCache.key(request, context, deployment, intent);
        var cached = answerCache.get(key).map(value -> withCacheStatus(value, "HIT")).orElse(null);
        return new Prepared(context, key, cached, retrieval.policy());
    }

    private GroundedChatResponse grounded(String content, String deployment, GroundedContext context) {
        return new GroundedChatResponse(sanitizer.sanitize(content, context.citations()), deployment,
                context.citations(), context.toolsUsed(), "GROUNDED", answerCache.enabled() ? "MISS" : "BYPASS");
    }

    private static GroundedChatResponse withCacheStatus(GroundedChatResponse response, String status) {
        return new GroundedChatResponse(response.answer(), response.model(), response.citations(),
                response.toolsUsed(), response.groundingStatus(), status);
    }

    private static GroundedChatResponse localNoEvidence(ChatRetrieval retrieval, String deployment,
                                                        List<String> toolsUsed, ChatTiming timing) {
        var answer = timing.measure(ChatStage.LOCAL_ANSWER, () -> switch (retrieval.outcome()) {
            case PAPER_NOT_FOUND -> "That paper is not present in the indexed local arXiv corpus. "
                    + "Check the arXiv identifier or import and embed the paper first.";
            case DEGRADED_LEXICAL -> "The embedding service did not respond in time and lexical retrieval "
                    + "found no supporting evidence in the indexed local arXiv corpus.";
            case EVIDENCE, NO_EVIDENCE -> "I could not find supporting evidence in the indexed local arXiv "
                    + "corpus. Try broader terms or remove restrictive category/date filters.";
        });
        return new GroundedChatResponse(answer, deployment, List.of(), toolsUsed, retrieval.outcome().name());
    }

    private record Prepared(GroundedContext context, ChatAnswerCache.Key answerKey,
                            GroundedChatResponse terminal, RetrievalPolicy policy) { }
}

package com.example.arxivrag.ai.chat;

import com.example.arxivrag.ai.foundry.FoundryClient;
import com.example.arxivrag.ai.foundry.FoundryProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Profile("foundry")
public class GroundedChatService {

    private static final Logger logger = LoggerFactory.getLogger(GroundedChatService.class);
    private static final int MAX_MESSAGE_LENGTH = 8000;

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

    private final FoundryClient foundryClient;
    private final FoundryProperties foundryProperties;
    private final RetrievalToolService retrievalToolService;
    private final ObjectMapper objectMapper;
    private final ChatProperties chatProperties;
    private final ChatRetrievalPlanner retrievalPlanner;
    private final GroundedContextBuilder contextBuilder;
    private final GroundedAnswerSanitizer sanitizer;
    private final ChatMetrics metrics;
    private final ExecutorService toolExecutor;

    /** Compatibility constructor: existing tests exercise the legacy agentic flow. */
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
        this.chatProperties = new ChatProperties();
        this.chatProperties.setOrchestrationMode("agentic");
        this.chatProperties.setMaxToolRounds(3);
        this.retrievalPlanner = null;
        this.contextBuilder = null;
        this.sanitizer = new GroundedAnswerSanitizer();
        this.metrics = null;
        this.toolExecutor = newToolExecutor(3);
    }

    @Autowired
    public GroundedChatService(
            FoundryClient foundryClient,
            FoundryProperties foundryProperties,
            RetrievalToolService retrievalToolService,
            ObjectMapper objectMapper,
            ChatProperties chatProperties,
            ChatRetrievalPlanner retrievalPlanner,
            GroundedContextBuilder contextBuilder,
            GroundedAnswerSanitizer sanitizer,
            ChatMetrics metrics
    ) {
        chatProperties.validate();
        this.foundryClient = foundryClient;
        this.foundryProperties = foundryProperties;
        this.retrievalToolService = retrievalToolService;
        this.objectMapper = objectMapper;
        this.chatProperties = chatProperties;
        this.retrievalPlanner = retrievalPlanner;
        this.contextBuilder = contextBuilder;
        this.sanitizer = sanitizer;
        this.metrics = metrics;
        this.toolExecutor = newToolExecutor(chatProperties.getMaxParallelToolCalls());
    }

    public GroundedChatResponse chat(String message) {
        return chat(new ChatRequest(message));
    }

    public GroundedChatResponse chat(ChatRequest request) {
        return chatDetailed(request).response();
    }

    public ChatExecution chatDetailed(ChatRequest request) {
        return chatDetailedForDeployment(request, foundryProperties.getChatDeployment());
    }

    public ChatExecution chatDetailedForDeployment(ChatRequest request, String deployment) {
        validate(request);
        validateBenchmarkDeployment(deployment);
        var timing = new ChatTiming(metrics);
        if (metrics != null) {
            metrics.requestStarted();
        }
        MDC.put("chatRequestId", timing.requestId());
        try {
            var agentic = useAgentic(request);
            var response = agentic
                    ? agentic(request.message().trim(), timing, deployment)
                    : preRetrieval(request, timing, deployment);
            if (metrics != null) {
                metrics.requestCompleted(timing.elapsed());
            }
            logger.info("Grounded chat complete requestId={} mode={} elapsedMs={} stages={} providerRequestIds={}",
                    timing.requestId(), agentic ? "agentic" : "pre-retrieval",
                    timing.elapsed().toMillis(), timing.milliseconds(), timing.providerRequestIds());
            return new ChatExecution(response, timing);
        } catch (RuntimeException exception) {
            if (metrics != null) {
                metrics.failure();
            }
            logger.warn("Grounded chat failed requestId={} elapsedMs={} error={}",
                    timing.requestId(), timing.elapsed().toMillis(), exception.getMessage());
            throw exception;
        } finally {
            MDC.remove("chatRequestId");
        }
    }

    public ChatExecution stream(ChatRequest request, ChatStreamListener listener) {
        return streamForDeployment(request, foundryProperties.getChatDeployment(), listener);
    }

    public ChatExecution streamForDeployment(
            ChatRequest request, String deployment, ChatStreamListener listener) {
        validate(request);
        validateBenchmarkDeployment(deployment);
        if (listener == null) {
            throw new IllegalArgumentException("stream listener must not be null");
        }
        if (!chatProperties.isStreamingEnabled() || useAgentic(request)) {
            var execution = chatDetailedForDeployment(request, deployment);
            listener.delta(execution.response().answer());
            listener.complete(execution.response(), execution.timing());
            return execution;
        }

        var timing = new ChatTiming(metrics);
        if (metrics != null) {
            metrics.requestStarted();
        }
        MDC.put("chatRequestId", timing.requestId());
        try {
            listener.retrievalStarted();
            var retrieval = timing.measure(ChatStage.RETRIEVAL, () -> retrievalPlanner.retrieve(request, timing));
            var context = contextBuilder.build(retrieval);
            listener.citations(context.citations());
            if (metrics != null) {
                metrics.foundryCall();
            }
            var streamStarted = System.nanoTime();
            var firstTokenSeen = new java.util.concurrent.atomic.AtomicBoolean();
            var result = timing.measure(ChatStage.ANSWER_MODEL, () -> foundryClient.streamChat(
                    deployment,
                    answerMessages(request.message().trim(), context.evidenceJson()), options(), delta -> {
                        if (firstTokenSeen.compareAndSet(false, true)) {
                            var ttft = Duration.ofNanos(System.nanoTime() - streamStarted);
                            timing.add(ChatStage.FIRST_TOKEN, ttft);
                            if (metrics != null) {
                                metrics.recordTtft(ttft);
                            }
                        }
                        listener.delta(delta);
                    }));
            recordMetadata(result.metadata(), timing);
            var response = new GroundedChatResponse(sanitizer.sanitize(result.content(), context.citations()),
                    deployment, context.citations(), context.toolsUsed());
            if (metrics != null) {
                metrics.requestCompleted(timing.elapsed());
            }
            listener.complete(response, timing);
            return new ChatExecution(response, timing);
        } catch (RuntimeException exception) {
            if (metrics != null) {
                metrics.failure();
            }
            listener.error(exception);
            throw exception;
        } finally {
            MDC.remove("chatRequestId");
        }
    }

    private GroundedChatResponse preRetrieval(ChatRequest request, ChatTiming timing, String deployment) {
        var retrieval = timing.measure(ChatStage.RETRIEVAL, () -> retrievalPlanner.retrieve(request, timing));
        var context = contextBuilder.build(retrieval);
        if (metrics != null) {
            metrics.foundryCall();
        }
        var result = timing.measure(ChatStage.ANSWER_MODEL, () -> foundryClient.chatWithTools(
                deployment, answerMessages(request.message().trim(), context.evidenceJson()),
                List.of(), options()));
        recordMetadata(result.metadata(), timing);
        return new GroundedChatResponse(sanitizer.sanitize(result.content(), context.citations()),
                deployment, context.citations(), context.toolsUsed());
    }

    private GroundedChatResponse agentic(String message, ChatTiming timing, String deployment) {
        var messages = new ArrayList<Map<String, Object>>();
        messages.add(Map.of("role", "system", "content", AGENTIC_SYSTEM_PROMPT));
        messages.add(Map.of("role", "user", "content", message));
        var citations = new LinkedHashMap<String, ChatCitation>();
        var toolsUsed = new ArrayList<String>();
        var seenEvidence = new java.util.HashSet<String>();
        var remainingContext = new java.util.concurrent.atomic.AtomicInteger(
                chatProperties.getMaxContextCharactersTotal());

        for (var round = 0; round < chatProperties.getMaxToolRounds(); round++) {
            if (metrics != null) {
                metrics.foundryCall();
            }
            var selection = timing.measure(ChatStage.TOOL_SELECTION_MODEL, () -> callAgenticChat(
                    deployment, messages, retrievalToolService.toolDefinitions()));
            recordMetadata(selection.metadata(), timing);
            if (selection.toolCalls().isEmpty()) {
                return response(selection.content(), citations, toolsUsed, deployment);
            }
            if (metrics != null) {
                metrics.toolRound();
            }
            messages.add(selection.assistantMessage());
            var outcomes = timing.measure(ChatStage.TOOL_EXECUTION, () -> executeTools(selection.toolCalls()));
            for (var index = 0; index < selection.toolCalls().size(); index++) {
                var call = selection.toolCalls().get(index);
                var outcome = compact(outcomes.get(index), seenEvidence, remainingContext);
                toolsUsed.add(call.name());
                if (metrics != null) {
                    metrics.toolCall(call.name());
                }
                outcome.citations().forEach(citation -> citations.putIfAbsent(citation.arxivId(), citation));
                messages.add(Map.of("role", "tool", "tool_call_id", call.id(), "content", json(outcome.result())));
            }
        }

        if (metrics != null) {
            metrics.foundryCall();
        }
        var answer = timing.measure(ChatStage.ANSWER_MODEL, () -> callAgenticChat(deployment, messages, List.of()));
        recordMetadata(answer.metadata(), timing);
        return response(answer.content(), citations, toolsUsed, deployment);
    }

    private List<ToolOutcome> executeTools(List<FoundryClient.FoundryToolCall> calls) {
        var futures = calls.stream().limit(chatProperties.getMaxParallelToolCalls())
                .map(call -> toolExecutor.submit(() -> executeTool(call)))
                .toList();
        var outcomes = new ArrayList<ToolOutcome>(calls.size());
        for (var future : futures) {
            try {
                outcomes.add(future.get());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Retrieval tool execution was interrupted", exception);
            } catch (ExecutionException exception) {
                var message = exception.getCause() == null ? "tool failed" : exception.getCause().getMessage();
                outcomes.add(new ToolOutcome(Map.of("error", message), List.of()));
            }
        }
        for (var index = futures.size(); index < calls.size(); index++) {
            outcomes.add(new ToolOutcome(Map.of("error", "parallel tool call limit exceeded"), List.of()));
        }
        return outcomes;
    }

    private ToolOutcome executeTool(FoundryClient.FoundryToolCall call) {
        try {
            var execution = retrievalToolService.execute(call.name(), call.arguments());
            return new ToolOutcome(execution.result(), execution.citations());
        } catch (IllegalArgumentException exception) {
            return new ToolOutcome(Map.of("error", exception.getMessage()), List.of());
        }
    }

    private ToolOutcome compact(ToolOutcome outcome, java.util.Set<String> seen,
                                java.util.concurrent.atomic.AtomicInteger remaining) {
        if (!(outcome.result() instanceof List<?> rows)) {
            return outcome;
        }
        var retainedRows = new ArrayList<Object>();
        var retainedIds = new java.util.HashSet<String>();
        var hadArxivIds = false;
        for (var value : rows) {
            if (!(value instanceof Map<?, ?> source)) {
                continue;
            }
            var arxivIdValue = source.get("arxivId");
            var arxivId = arxivIdValue == null ? null : arxivIdValue.toString();
            hadArxivIds |= arxivId != null;
            if (arxivId != null && !seen.add(arxivId)) {
                continue;
            }
            var row = new LinkedHashMap<Object, Object>(source);
            var abstractValue = row.get("abstractText");
            if (abstractValue instanceof String text
                    && text.length() > chatProperties.getMaxContextCharactersPerPaper()) {
                row.put("abstractText", text.substring(0, chatProperties.getMaxContextCharactersPerPaper()) + "...");
            }
            var rowSize = json(row).length() + 1;
            if (rowSize > remaining.get()) {
                if (arxivId != null) {
                    seen.remove(arxivId);
                }
                break;
            }
            remaining.addAndGet(-rowSize);
            retainedRows.add(row);
            if (arxivId != null) {
                retainedIds.add(arxivId);
            }
        }
        var retainedCitations = hadArxivIds
                ? outcome.citations().stream().filter(citation -> retainedIds.contains(citation.arxivId())).toList()
                : retainedRows.isEmpty() ? List.<ChatCitation>of() : outcome.citations();
        return new ToolOutcome(retainedRows, retainedCitations);
    }

    private GroundedChatResponse response(String answer, LinkedHashMap<String, ChatCitation> citations,
                                          List<String> toolsUsed, String deployment) {
        var evidence = List.copyOf(citations.values());
        return new GroundedChatResponse(sanitizer.sanitize(answer, evidence), deployment,
                evidence, List.copyOf(toolsUsed));
    }

    private List<Map<String, Object>> answerMessages(String question, String evidenceJson) {
        return List.of(
                Map.of("role", "system", "content", ANSWER_SYSTEM_PROMPT),
                Map.of("role", "user", "content", "QUESTION:\n" + question + "\n\nEVIDENCE:\n" + evidenceJson)
        );
    }

    private FoundryClient.FoundryChatOptions options() {
        return new FoundryClient.FoundryChatOptions(
                chatProperties.getMaxCompletionTokens(), chatProperties.getTemperature());
    }

    private FoundryClient.FoundryChatResult callAgenticChat(
            String deployment, List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        if (retrievalPlanner == null) {
            return foundryClient.chatWithTools(deployment, messages, tools);
        }
        return foundryClient.chatWithTools(deployment, messages, tools, options());
    }

    private void validateBenchmarkDeployment(String deployment) {
        if (deployment == null || deployment.isBlank()) {
            throw new IllegalArgumentException("chat deployment must not be blank");
        }
        var candidate = chatProperties.getCandidateChatDeployment();
        if (!deployment.equals(foundryProperties.getChatDeployment())
                && (candidate == null || candidate.isBlank() || !deployment.equals(candidate))) {
            throw new IllegalArgumentException("deployment must be the configured primary or candidate chat deployment");
        }
    }

    private void recordMetadata(FoundryClient.ProviderMetadata metadata, ChatTiming timing) {
        if (metadata == null) {
            return;
        }
        timing.add(ChatStage.QUOTA_WAIT, metadata.quotaWait());
        timing.add(ChatStage.RETRY_BACKOFF, metadata.retryWait());
        timing.addProviderMetadata(metadata);
        if (metrics != null) {
            metrics.recordTokens(metadata.promptTokens(), metadata.completionTokens());
        }
    }

    private boolean useAgentic(ChatRequest request) {
        return Boolean.TRUE.equals(request.agentic())
                || (request.agentic() == null && chatProperties.mode() == ChatOrchestrationMode.AGENTIC);
    }

    private static void validate(ChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        if (request.message().length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("message must not exceed " + MAX_MESSAGE_LENGTH + " characters");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new FoundryClient.FoundryProviderException("Could not serialize retrieval tool result", exception);
        }
    }

    private static ExecutorService newToolExecutor(int parallelism) {
        return Executors.newFixedThreadPool(Math.max(1, parallelism),
                Thread.ofVirtual().name("chat-tool-", 0).factory());
    }

    @PreDestroy
    void close() {
        toolExecutor.shutdownNow();
    }

    private record ToolOutcome(Object result, List<ChatCitation> citations) {
    }
}

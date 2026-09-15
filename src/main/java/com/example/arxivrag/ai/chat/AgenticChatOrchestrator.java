package com.example.arxivrag.ai.chat;

import com.example.arxivrag.model.api.ChatModelGateway;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Optional bounded tool-call loop; the model never receives database access. */
@Component
@ConditionalOnProperty(name = "rag.features.chat-enabled", havingValue = "true")
public class AgenticChatOrchestrator {
    private final ChatModelGateway chatModel;
    private final RetrievalToolService retrievalTools;
    private final ObjectMapper objectMapper;
    private final ChatProperties properties;
    private final GroundedAnswerSanitizer sanitizer;
    private final ExecutorService toolExecutor;
    private final ChatPromptFactory prompts;
    private final ChatTelemetry telemetry;

    public AgenticChatOrchestrator(ChatModelGateway chatModel, RetrievalToolService retrievalTools,
                                   ObjectMapper objectMapper, ChatProperties properties,
                                   GroundedAnswerSanitizer sanitizer,
                                   @Qualifier("chatToolExecutor") ExecutorService toolExecutor,
                                   ChatPromptFactory prompts, ChatTelemetry telemetry) {
        this.chatModel = chatModel;
        this.retrievalTools = retrievalTools;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.sanitizer = sanitizer;
        this.toolExecutor = toolExecutor;
        this.prompts = prompts;
        this.telemetry = telemetry;
    }

    public GroundedChatResponse execute(String message, ChatTiming timing, String deployment) {
        var messages = prompts.agenticMessages(message);
        var citations = new LinkedHashMap<String, ChatCitation>();
        var toolsUsed = new ArrayList<String>();
        var seenEvidence = new java.util.HashSet<String>();
        var remainingContext = new java.util.concurrent.atomic.AtomicInteger(
                properties.getMaxContextCharactersTotal());
        for (var round = 0; round < properties.getMaxToolRounds(); round++) {
            telemetry.providerCall();
            var selection = timing.measure(ChatStage.TOOL_SELECTION_MODEL, () -> generate(
                    deployment, messages, retrievalTools.toolDefinitions(), message));
            telemetry.providerMetadata(selection.metadata(), timing);
            if (selection.toolCalls().isEmpty()) return response(selection.content(), citations, toolsUsed, deployment);
            telemetry.toolRound();
            messages.add(selection.assistantMessage());
            var outcomes = timing.measure(ChatStage.TOOL_EXECUTION, () -> executeTools(selection.toolCalls()));
            for (var index = 0; index < selection.toolCalls().size(); index++) {
                var call = selection.toolCalls().get(index);
                var outcome = compact(outcomes.get(index), seenEvidence, remainingContext);
                toolsUsed.add(call.name());
                telemetry.toolCall(call.name());
                outcome.citations().forEach(citation -> citations.putIfAbsent(citation.arxivId(), citation));
                messages.add(Map.of("role", "tool", "tool_call_id", call.id(), "content", json(outcome.result())));
            }
        }
        telemetry.providerCall();
        var answer = timing.measure(ChatStage.ANSWER_MODEL,
                () -> generate(deployment, messages, List.of(), message));
        telemetry.providerMetadata(answer.metadata(), timing);
        return response(answer.content(), citations, toolsUsed, deployment);
    }

    private List<ToolOutcome> executeTools(List<ChatModelGateway.ToolCall> calls) {
        var futures = calls.stream().limit(properties.getMaxParallelToolCalls())
                .map(call -> toolExecutor.submit(() -> executeTool(call))).toList();
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

    private ToolOutcome executeTool(ChatModelGateway.ToolCall call) {
        try {
            var execution = retrievalTools.execute(call.name(), call.arguments());
            return new ToolOutcome(execution.result(), execution.citations());
        } catch (IllegalArgumentException exception) {
            return new ToolOutcome(Map.of("error", exception.getMessage()), List.of());
        }
    }

    private ToolOutcome compact(ToolOutcome outcome, java.util.Set<String> seen,
                                java.util.concurrent.atomic.AtomicInteger remaining) {
        if (!(outcome.result() instanceof List<?> rows)) return outcome;
        var retainedRows = new ArrayList<Object>();
        var retainedIds = new java.util.HashSet<String>();
        var hadArxivIds = false;
        for (var value : rows) {
            if (!(value instanceof Map<?, ?> source)) continue;
            var idValue = source.get("arxivId");
            var arxivId = idValue == null ? null : idValue.toString();
            hadArxivIds |= arxivId != null;
            if (arxivId != null && !seen.add(arxivId)) continue;
            var row = new LinkedHashMap<Object, Object>(source);
            if (row.get("abstractText") instanceof String text
                    && text.length() > properties.getMaxContextCharactersPerPaper()) {
                row.put("abstractText", text.substring(0, properties.getMaxContextCharactersPerPaper()) + "...");
            }
            var rowSize = json(row).length() + 1;
            if (rowSize > remaining.get()) {
                if (arxivId != null) seen.remove(arxivId);
                break;
            }
            remaining.addAndGet(-rowSize);
            retainedRows.add(row);
            if (arxivId != null) retainedIds.add(arxivId);
        }
        var retainedCitations = hadArxivIds
                ? outcome.citations().stream().filter(value -> retainedIds.contains(value.arxivId())).toList()
                : retainedRows.isEmpty() ? List.<ChatCitation>of() : outcome.citations();
        return new ToolOutcome(retainedRows, retainedCitations);
    }

    private GroundedChatResponse response(String answer, LinkedHashMap<String, ChatCitation> citations,
                                          List<String> toolsUsed, String deployment) {
        var evidence = List.copyOf(citations.values());
        return new GroundedChatResponse(sanitizer.sanitize(answer, evidence), deployment, evidence,
                List.copyOf(toolsUsed), evidence.isEmpty() ? "NO_EVIDENCE" : "GROUNDED");
    }

    private ChatModelGateway.ChatModelResult generate(String deployment, List<Map<String, Object>> messages,
                                                       List<Map<String, Object>> tools, String question) {
        return chatModel.generate(deployment, messages, tools, prompts.options(question));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new ChatModelGateway.ModelProviderException("Could not serialize retrieval tool result", exception);
        }
    }

    private record ToolOutcome(Object result, List<ChatCitation> citations) { }
}

package com.example.arxivrag.ai.foundry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Owns the non-streaming Microsoft AI Foundry chat-completions protocol. */
@Component
@Profile("foundry")
public class FoundryChatClient {

    private final ObjectMapper objectMapper;
    private final FoundryQuotaLimiterRegistry quotaLimiters;
    private final FoundryRequestExecutor requests;

    public FoundryChatClient(
            ObjectMapper objectMapper,
            FoundryQuotaLimiterRegistry quotaLimiters,
            FoundryRequestExecutor requests
    ) {
        this.objectMapper = objectMapper;
        this.quotaLimiters = quotaLimiters;
        this.requests = requests;
    }

    public FoundryClient.FoundryChatResult generate(
            String deployment,
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools,
            FoundryClient.FoundryChatOptions options
    ) {
        if (deployment == null || deployment.isBlank()) {
            throw new FoundryClient.FoundryProviderException("Foundry chat deployment must not be blank");
        }
        if (messages == null || messages.isEmpty()) {
            throw new FoundryClient.FoundryProviderException("Foundry chat requires at least one message");
        }

        var payload = new HashMap<String, Object>();
        payload.put("model", deployment);
        payload.put("messages", messages);
        payload.put("n", 1);
        FoundryProtocolSupport.applyOptions(payload, options);
        if (tools != null && !tools.isEmpty()) {
            payload.put("tools", tools);
            payload.put("tool_choice", "auto");
        }

        var quotaWait = quotaLimiters.acquire(deployment, FoundryOperation.CHAT,
                FoundryProtocolSupport.estimatedChatTokens(messages, options));
        var response = requests.post(
                "/openai/deployments/" + FoundryProtocolSupport.encode(deployment) + "/chat/completions",
                FoundryProtocolSupport.writeJson(objectMapper, payload), FoundryOperation.CHAT);
        var message = response.body().path("choices").path(0).path("message");
        if (!message.isObject()) {
            throw new FoundryClient.FoundryProviderException(
                    "Foundry chat response did not contain an assistant message");
        }

        var toolCalls = new ArrayList<FoundryClient.FoundryToolCall>();
        var toolCallNodes = message.path("tool_calls");
        if (toolCallNodes.isArray()) {
            for (var toolCallNode : toolCallNodes) {
                var id = toolCallNode.path("id").asText(null);
                var function = toolCallNode.path("function");
                var name = function.path("name").asText(null);
                var arguments = function.path("arguments").asText(null);
                if (id == null || id.isBlank() || name == null || name.isBlank() || arguments == null) {
                    throw new FoundryClient.FoundryProviderException("Foundry returned an invalid tool call");
                }
                toolCalls.add(new FoundryClient.FoundryToolCall(id, name, parseArguments(arguments)));
            }
        }

        @SuppressWarnings("unchecked")
        var assistantMessage = (Map<String, Object>) objectMapper.convertValue(message, Map.class);
        var content = message.path("content").asText(null);
        if ((content == null || content.isBlank()) && toolCalls.isEmpty()) {
            throw new FoundryClient.FoundryProviderException(
                    "Foundry chat response contained neither content nor tool calls");
        }
        return new FoundryClient.FoundryChatResult(content, List.copyOf(toolCalls),
                Collections.unmodifiableMap(new LinkedHashMap<>(assistantMessage)),
                FoundryProtocolSupport.metadata(response, quotaWait));
    }

    private Map<String, Object> parseArguments(String arguments) {
        try {
            var node = objectMapper.readTree(arguments);
            if (!node.isObject()) {
                throw new FoundryClient.FoundryProviderException(
                        "Foundry tool arguments must be a JSON object");
            }
            @SuppressWarnings("unchecked")
            var values = (Map<String, Object>) objectMapper.convertValue(node, Map.class);
            return Collections.unmodifiableMap(new LinkedHashMap<>(values));
        } catch (RuntimeException exception) {
            if (exception instanceof FoundryClient.FoundryProviderException providerException) {
                throw providerException;
            }
            throw new FoundryClient.FoundryProviderException(
                    "Foundry returned invalid tool arguments", exception);
        }
    }
}

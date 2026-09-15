package com.example.arxivrag.ai.chat;

import com.example.arxivrag.model.api.ChatModelGateway;
import com.example.arxivrag.model.api.ModelDeploymentConfiguration;
import java.util.concurrent.ExecutorService;
import tools.jackson.databind.ObjectMapper;

final class ChatServiceFixture {
    private ChatServiceFixture() { }

    static GroundedChatService create(ChatModelGateway model, ModelDeploymentConfiguration deployments,
                                      RetrievalToolService tools, ObjectMapper mapper, ChatProperties properties,
                                      ChatRetrievalPlanner planner, GroundedContextBuilder context,
                                      GroundedAnswerSanitizer sanitizer, ChatMetrics metrics,
                                      ExecutorService executor, QuestionIntentClassifier intent,
                                      ChatAnswerCache answerCache, ChatPromptFactory prompts,
                                      ChatRouteSelector routes) {
        var telemetry = new ChatTelemetry(metrics);
        var preRetrieval = new PreRetrievalOrchestrator(model, planner, context, sanitizer, properties,
                intent, answerCache, prompts, telemetry);
        var agentic = new AgenticChatOrchestrator(model, tools, mapper, properties, sanitizer,
                executor, prompts, telemetry);
        return new GroundedChatService(deployments, properties, routes, preRetrieval, agentic, telemetry);
    }
}

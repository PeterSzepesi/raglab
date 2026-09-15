package com.example.arxivrag.ai.chat;

import com.example.arxivrag.model.api.ModelDeploymentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Chat use-case boundary: validation, route selection, lifecycle, and deployment policy. */
@Service
@Profile("foundry")
public class GroundedChatService implements ChatUseCase {
    private static final Logger logger = LoggerFactory.getLogger(GroundedChatService.class);
    private static final int MAX_MESSAGE_LENGTH = 8000;

    private final ModelDeploymentConfiguration deployments;
    private final ChatProperties properties;
    private final ChatRouteSelector routes;
    private final PreRetrievalOrchestrator preRetrieval;
    private final AgenticChatOrchestrator agentic;
    private final ChatTelemetry telemetry;

    public GroundedChatService(ModelDeploymentConfiguration deployments, ChatProperties properties,
                               ChatRouteSelector routes, PreRetrievalOrchestrator preRetrieval,
                               AgenticChatOrchestrator agentic, ChatTelemetry telemetry) {
        properties.validate();
        this.deployments = deployments;
        this.properties = properties;
        this.routes = routes;
        this.preRetrieval = preRetrieval;
        this.agentic = agentic;
        this.telemetry = telemetry;
    }

    public GroundedChatResponse chat(String message) { return chat(new ChatRequest(message)); }
    public GroundedChatResponse chat(ChatRequest request) { return chatDetailed(request).response(); }
    @Override
    public ChatExecution chatDetailed(ChatRequest request) {
        return chatDetailedForDeployment(request, deployments.chatDeployment());
    }

    public ChatExecution chatDetailedForDeployment(ChatRequest request, String deployment) {
        validate(request);
        validateDeployment(deployment);
        var timing = telemetry.start();
        MDC.put("chatRequestId", timing.requestId());
        var route = routes.select(request);
        var observation = telemetry.operation(route.name().toLowerCase(java.util.Locale.ROOT)).start();
        try {
            var response = switch (route) {
                case LOCAL_INVALID_FILTER -> timing.measure(ChatStage.LOCAL_ANSWER,
                        () -> invalidFilterResponse(deployment));
                case AGENTIC -> agentic.execute(request.message().trim(), timing, deployment);
                case PRE_RETRIEVAL -> preRetrieval.execute(request, timing, deployment);
            };
            telemetry.complete(timing);
            logger.info("Grounded chat complete requestId={} mode={} elapsedMs={} stages={} providerRequestIds={}",
                    timing.requestId(), route.name().toLowerCase(java.util.Locale.ROOT),
                    timing.elapsed().toMillis(), timing.milliseconds(), timing.providerRequestIds());
            return new ChatExecution(response, timing);
        } catch (RuntimeException exception) {
            observation.error(exception);
            telemetry.failure();
            logger.warn("Grounded chat failed requestId={} elapsedMs={} errorType={}",
                    timing.requestId(), timing.elapsed().toMillis(), exception.getClass().getSimpleName());
            throw exception;
        } finally {
            observation.stop();
            MDC.remove("chatRequestId");
        }
    }

    @Override
    public ChatExecution stream(ChatRequest request, ChatStreamListener listener) {
        return streamForDeployment(request, deployments.chatDeployment(), listener);
    }

    public ChatExecution streamForDeployment(ChatRequest request, String deployment, ChatStreamListener listener) {
        validate(request);
        validateDeployment(deployment);
        if (listener == null) throw new IllegalArgumentException("stream listener must not be null");
        if (!properties.isStreamingEnabled() || routes.select(request) != ChatRouteSelector.Route.PRE_RETRIEVAL) {
            var execution = chatDetailedForDeployment(request, deployment);
            listener.delta(execution.response().answer());
            listener.complete(execution.response(), execution.timing());
            return execution;
        }

        var timing = telemetry.start();
        MDC.put("chatRequestId", timing.requestId());
        var observation = telemetry.operation("streaming").start();
        try {
            var response = preRetrieval.stream(request, timing, deployment, listener);
            telemetry.complete(timing);
            listener.complete(response, timing);
            return new ChatExecution(response, timing);
        } catch (RuntimeException exception) {
            observation.error(exception);
            telemetry.failure();
            listener.error(exception);
            throw exception;
        } finally {
            observation.stop();
            MDC.remove("chatRequestId");
        }
    }

    private void validateDeployment(String deployment) {
        if (deployment == null || deployment.isBlank()) {
            throw new IllegalArgumentException("chat deployment must not be blank");
        }
        var candidate = deployments.candidateChatDeployment();
        if (!deployment.equals(deployments.chatDeployment())
                && (candidate == null || candidate.isBlank() || !deployment.equals(candidate))) {
            throw new IllegalArgumentException("deployment must be the configured primary or candidate chat deployment");
        }
    }

    private static GroundedChatResponse invalidFilterResponse(String deployment) {
        return new GroundedChatResponse("The submitted date range is contradictory: the start date is after the end "
                + "date. Correct or remove one of the date filters and try again.", deployment,
                java.util.List.of(), java.util.List.of(), "INVALID_FILTER_RANGE", "BYPASS");
    }

    private static void validate(ChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        if (request.message().length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("message must not exceed " + MAX_MESSAGE_LENGTH + " characters");
        }
    }
}

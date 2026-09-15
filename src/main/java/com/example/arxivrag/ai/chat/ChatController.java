package com.example.arxivrag.ai.chat;

import com.example.arxivrag.config.InteractiveAdmissionController;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@Profile("foundry")
@RequestMapping({"/api/chat", "/api/v1/chat"})
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "rag.features.chat-enabled", havingValue = "true")
public class ChatController {

    private final ChatRequestExecutor requests;
    private final ChatProperties properties;
    private final ChatStreamPublisher streams;
    private final InteractiveAdmissionController admission;

    public ChatController(
            ChatRequestExecutor requests,
            ChatProperties properties,
            ChatStreamPublisher streams,
            InteractiveAdmissionController admission
    ) {
        this.requests = requests;
        this.properties = properties;
        this.streams = streams;
        this.admission = admission;
    }

    @PostMapping
    public ResponseEntity<GroundedChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        ChatExecution execution;
        try (var ignored = admission.acquireChat()) {
            execution = requests.execute(request);
        }
        var headers = new HttpHeaders();
        headers.set("X-Request-ID", execution.timing().requestId());
        var serverTiming = execution.timing().serverTimingHeader();
        if (!serverTiming.isBlank()) {
            headers.set("Server-Timing", serverTiming);
        }
        if (properties.isDebugTimings()) {
            headers.set("X-Chat-Timings", execution.timing().milliseconds().toString());
        }
        return new ResponseEntity<>(execution.response(), headers, HttpStatus.OK);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatRequest request, HttpServletRequest servletRequest) {
        var permit = admission.acquireChat();
        var requestIdAttribute = servletRequest.getAttribute(
                com.example.arxivrag.config.RequestCorrelationFilter.ATTRIBUTE);
        var requestId = requestIdAttribute instanceof String value && !value.isBlank()
                ? value : java.util.UUID.randomUUID().toString();
        return streams.publish(request, requestId, permit);
    }
}

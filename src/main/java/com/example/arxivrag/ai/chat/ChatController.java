package com.example.arxivrag.ai.chat;

import com.example.arxivrag.ai.foundry.FoundryClient;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Profile("foundry")
@RequestMapping("/api/chat")
public class ChatController {

    private final GroundedChatService groundedChatService;

    public ChatController(GroundedChatService groundedChatService) {
        this.groundedChatService = groundedChatService;
    }

    @PostMapping
    public GroundedChatResponse chat(@RequestBody ChatRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("message must not be blank");
        }
        return groundedChatService.chat(request.message());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(FoundryClient.FoundryProviderException.class)
    public ResponseEntity<Map<String, String>> handleProviderFailure(FoundryClient.FoundryProviderException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", exception.getMessage()));
    }
}

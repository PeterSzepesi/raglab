package com.example.arxivrag.search;

import com.example.arxivrag.vector.SemanticSearchService;
import com.example.arxivrag.vector.VectorSearchDiagnostics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/search/diagnostics")
@ConditionalOnProperty(name = "chat.debug-timings", havingValue = "true")
public class VectorDiagnosticsController {

    private final SemanticSearchService search;

    public VectorDiagnosticsController(SemanticSearchService search) {
        this.search = search;
    }

    @PostMapping("/vector-plan")
    public VectorSearchDiagnostics explain(@RequestBody SearchRequest request) {
        if (request == null || request.query() == null || request.query().isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        var topK = request.topK() == null ? 10 : request.topK();
        if (topK < 1 || topK > 50) {
            throw new IllegalArgumentException("topK must be between 1 and 50");
        }
        return search.explain(request.query().trim(), topK, request.category(), request.fromDate(), request.toDate());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", exception.getMessage()));
    }
}

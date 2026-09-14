package com.example.arxivrag.search;

import com.example.arxivrag.ai.foundry.FoundryClient;
import com.example.arxivrag.vector.SemanticSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private static final int DEFAULT_TOP_K = 10;
    private static final int MAX_TOP_K = 50;

    private final SemanticSearchService semanticSearchService;

    private final LexicalSearchService lexicalSearchService;

    private final HybridSearchService hybridSearchService;

    public SearchController(SemanticSearchService semanticSearchService) {
        this(semanticSearchService, null, null);
    }

    @Autowired
    public SearchController(SemanticSearchService semanticSearchService, LexicalSearchService lexicalSearchService,
                            HybridSearchService hybridSearchService) {
        this.semanticSearchService = semanticSearchService;
        this.lexicalSearchService = lexicalSearchService;
        this.hybridSearchService = hybridSearchService;
    }

    @PostMapping
    public SearchResponse search(@RequestBody SearchRequest request) {
        if (request == null || request.query() == null || request.query().isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        var topK = request.topK() == null ? DEFAULT_TOP_K : request.topK();
        if (topK <= 0 || topK > MAX_TOP_K) {
            throw new IllegalArgumentException("topK must be between 1 and " + MAX_TOP_K);
        }

        var mode = SearchMode.parse(request.mode());
        var category = normalize(request.category());
        var fromDate = request.fromDate();
        var toDate = request.toDate();
        var results = switch (mode) {
            case SEMANTIC -> semanticSearchService.search(request.query().trim(), topK, category, fromDate, toDate);
            case LEXICAL -> requireLexical().search(request.query().trim(), topK, category, fromDate, toDate);
            case HYBRID -> requireHybrid().search(request.query().trim(), topK, category, fromDate, toDate);
        };

        return new SearchResponse(
                request.query().trim(),
                topK,
                mode == SearchMode.LEXICAL ? "postgresql-full-text" : semanticSearchService.embeddingModelName(),
                results,
                mode.name().toLowerCase(java.util.Locale.ROOT)
        );
    }

    private LexicalSearchService requireLexical() {
        if (lexicalSearchService == null) {
            throw new IllegalStateException("lexical search is not configured");
        }
        return lexicalSearchService;
    }

    private HybridSearchService requireHybrid() {
        if (hybridSearchService == null) {
            throw new IllegalStateException("hybrid search is not configured");
        }
        return hybridSearchService;
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

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

package com.example.arxivrag.search;

import com.example.arxivrag.config.InteractiveAdmissionController;
import com.example.arxivrag.vector.SemanticSearchService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping({"/api/search", "/api/v1/search"})
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "rag.features.search-enabled", havingValue = "true")
public class SearchController {

    private static final int DEFAULT_TOP_K = 10;
    private static final int MAX_TOP_K = 50;

    private final SemanticSearchService semanticSearchService;

    private final LexicalSearchService lexicalSearchService;

    private final HybridSearchService hybridSearchService;
    private final InteractiveAdmissionController admission;

    public SearchController(SemanticSearchService semanticSearchService) {
        this(semanticSearchService, null, null, null);
    }

    @Autowired
    public SearchController(SemanticSearchService semanticSearchService, LexicalSearchService lexicalSearchService,
                            HybridSearchService hybridSearchService, InteractiveAdmissionController admission) {
        this.semanticSearchService = semanticSearchService;
        this.lexicalSearchService = lexicalSearchService;
        this.hybridSearchService = hybridSearchService;
        this.admission = admission;
    }

    @PostMapping
    public SearchResponse search(@Valid @RequestBody SearchRequest request) {
        if (request == null || request.query() == null || request.query().isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        var topK = request.topK() == null ? DEFAULT_TOP_K : request.topK();
        if (topK <= 0 || topK > MAX_TOP_K) {
            throw new IllegalArgumentException("topK must be between 1 and " + MAX_TOP_K);
        }
        if (request.fromDate() != null && request.toDate() != null
                && request.fromDate().isAfter(request.toDate())) {
            throw new IllegalArgumentException("fromDate must not be after toDate");
        }

        try (var ignored = admission == null ? null : admission.acquireRetrieval()) {
        var requestedMode = SearchMode.parse(request.mode());
        var mode = requestedMode == SearchMode.AUTO ? automaticMode(request.query()) : requestedMode;
        if (requestedMode == SearchMode.AUTO && (lexicalSearchService == null || hybridSearchService == null)) {
            mode = SearchMode.SEMANTIC;
        }
        var category = normalize(request.category());
        var fromDate = request.fromDate();
        var toDate = request.toDate();
        var started = System.nanoTime();
        var stageTimes = new java.util.LinkedHashMap<String, Long>();
        com.example.arxivrag.vector.SearchStageObserver observer = new com.example.arxivrag.vector.SearchStageObserver() {
            @Override public <T> T measure(com.example.arxivrag.vector.SearchStage stage,
                                           java.util.function.Supplier<T> supplier) {
                var stageStarted = System.nanoTime();
                try { return supplier.get(); }
                finally { stageTimes.merge(stage.metricName(),
                        java.time.Duration.ofNanos(System.nanoTime() - stageStarted).toMillis(), Long::sum); }
            }
        };
        var results = switch (mode) {
            case AUTO -> throw new IllegalStateException("Automatic search mode was not resolved");
            case SEMANTIC -> lexicalSearchService == null && hybridSearchService == null
                    ? semanticSearchService.search(request.query().trim(), topK, category, fromDate, toDate)
                    : semanticSearchService.search(request.query().trim(), topK, category, fromDate, toDate, observer);
            case LEXICAL -> requireLexical().search(request.query().trim(), topK, category, fromDate, toDate);
            case HYBRID -> requireHybrid().search(request.query().trim(), topK, category, fromDate, toDate, observer);
        };
        var elapsed = java.time.Duration.ofNanos(System.nanoTime() - started).toMillis();

        return new SearchResponse(
                request.query().trim(),
                topK,
                mode == SearchMode.LEXICAL ? "postgresql-full-text" : semanticSearchService.embeddingModelName(),
                results,
                mode.name().toLowerCase(java.util.Locale.ROOT),
                new SearchResponse.SearchDiagnostics(elapsed, java.util.Map.copyOf(stageTimes), false,
                        requestedMode == SearchMode.AUTO ? "Automatic routing selected " + mode.name().toLowerCase(
                                java.util.Locale.ROOT) + " retrieval." : null)
        );
        }
    }

    private static SearchMode automaticMode(String query) {
        var normalized = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.matches(".*(?:title|author):.+") || normalized.matches(".*\"[^\"]{4,}\".*")
                || normalized.matches(".*(?:\\d{4}\\.\\d{4,5}|[a-z][a-z0-9.-]*/\\d{7})(?:v\\d+)?.*")) {
            return SearchMode.LEXICAL;
        }
        return SearchMode.HYBRID;
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

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

package com.example.arxivrag.vector;

import com.example.arxivrag.embedding.EmbeddingModelGateway;
import com.example.arxivrag.embedding.EmbeddingWorkClass;
import com.example.arxivrag.embedding.RetrievalDocumentFactory;
import com.example.arxivrag.paper.Paper;
import com.example.arxivrag.paper.PaperRepository;
import com.example.arxivrag.search.QueryEmbeddingCache;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SemanticSearchService {

    public static final int DEFAULT_TOP_K = 10;

    private static final int MAX_QUERY_LENGTH = 4000;

    private final EmbeddingModelGateway embeddingModel;

    private final RetrievalDocumentFactory retrievalDocumentFactory;

    private final VectorStoreGateway vectorStoreGateway;

    private final PaperRepository paperRepository;

    private final SearchMetrics metrics;

    private final QueryEmbeddingCache queryEmbeddings;

    public SemanticSearchService(
            EmbeddingModelGateway embeddingModel,
            RetrievalDocumentFactory retrievalDocumentFactory,
            VectorStoreGateway vectorStoreGateway, PaperRepository paperRepository,
            SearchMetrics metrics, QueryEmbeddingCache queryEmbeddings
    ) {
        this.embeddingModel = embeddingModel;
        this.retrievalDocumentFactory = retrievalDocumentFactory;
        this.vectorStoreGateway = vectorStoreGateway;
        this.paperRepository = paperRepository;
        this.metrics = metrics;
        this.queryEmbeddings = queryEmbeddings;
    }

    public java.util.List<SemanticSearchResult> search(String query) {
        return search(query, DEFAULT_TOP_K);
    }

    public String embeddingModelName() {
        return embeddingModel.modelName();
    }

    public java.util.List<SemanticSearchResult> search(String query, int topK) {
        return search(query, topK, null, null, null);
    }

    public java.util.List<SemanticSearchResult> search(
            String query,
            int topK,
            String category,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        return search(query, topK, category, fromDate, toDate, null);
    }

    public java.util.List<SemanticSearchResult> search(
            String query,
            int topK,
            String category,
            LocalDate fromDate,
            LocalDate toDate,
            SearchStageObserver observer
    ) {
        var timer = metrics == null ? null : metrics.start();
        try {
            var results = searchInternal(query, topK, category, fromDate, toDate, observer);
            if (metrics != null) metrics.results("semantic", results);
            return results;
        } catch (RuntimeException exception) {
            if (metrics != null) {
                metrics.failure();
            }
            throw exception;
        } finally {
            if (timer != null) {
                metrics.stop(timer);
            }
        }
    }

    private java.util.List<SemanticSearchResult> searchInternal(
            String query,
            int topK,
            String category,
            LocalDate fromDate,
            LocalDate toDate,
            SearchStageObserver observer
    ) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("query must not exceed " + MAX_QUERY_LENGTH + " characters");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be greater than zero");
        }
        if (category != null && category.isBlank()) {
            throw new IllegalArgumentException("category must not be blank");
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must not be after toDate");
        }

        var queryDocument = retrievalDocumentFactory.forQuery(query);
        var cacheKey = new QueryEmbeddingCache.Key(query.trim().toLowerCase(java.util.Locale.ROOT),
                embeddingModel.modelName(), embeddingModel.dimensions(), RetrievalDocumentFactory.DOCUMENT_SCHEMA_VERSION);
        var queryEmbedding = measure(SearchStage.QUERY_EMBEDDING, observer,
                () -> queryEmbeddings.getOrLoad(cacheKey,
                        () -> embeddingModel.embed(queryDocument, EmbeddingWorkClass.INTERACTIVE)));
        if (queryEmbedding.size() != embeddingModel.dimensions()) {
            throw new IllegalStateException("Embedding model returned " + queryEmbedding.size()
                    + " dimensions, expected " + embeddingModel.dimensions());
        }

        var retrievalLimit = category == null && fromDate == null && toDate == null
                ? topK
                : Math.min(Math.max(topK * 10, topK), 100);
        var vectorHits = measure(SearchStage.VECTOR_SEARCH, observer, () -> vectorStoreGateway.search(new VectorSearchRequest(
                embeddingModel.modelName(),
                queryEmbedding,
                retrievalLimit,
                category,
                fromDate,
                toDate
        )));
        if (vectorHits.isEmpty()) {
            return java.util.List.of();
        }

        if (vectorHits.stream().allMatch(VectorSearchResult::hasPaperProjection)) {
            return vectorHits.stream()
                    .map(SemanticSearchService::toProjectedResult)
                    .filter(result -> matches(result, category, fromDate, toDate))
                    .limit(topK)
                    .toList();
        }

        var papersById = measure(SearchStage.PAPER_HYDRATION, observer, () -> paperRepository.findAllById(vectorHits.stream()
                        .map(VectorSearchResult::paperId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(Paper::getId, Function.identity())));

        return vectorHits.stream()
                .map(hit -> toResult(hit, papersById))
                .filter(java.util.Objects::nonNull)
                .filter(result -> matches(result, category, fromDate, toDate))
                .limit(topK)
                .toList();
    }

    public VectorSearchDiagnostics explain(
            String query, int topK, String category, LocalDate fromDate, LocalDate toDate) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        var started = System.nanoTime();
        var document = retrievalDocumentFactory.forQuery(query.trim());
        var embeddingStarted = System.nanoTime();
        var embedding = embeddingModel.embed(document);
        var embeddingMs = java.time.Duration.ofNanos(System.nanoTime() - embeddingStarted).toMillis();
        var preparationMs = java.time.Duration.ofNanos(System.nanoTime() - started).toMillis() - embeddingMs;
        var planStarted = System.nanoTime();
        var plan = vectorStoreGateway.explain(new VectorSearchRequest(
                embeddingModel.modelName(), embedding, topK, category, fromDate, toDate));
        var planMs = java.time.Duration.ofNanos(System.nanoTime() - planStarted).toMillis();
        return new VectorSearchDiagnostics(embeddingModel.modelName(), embeddingMs,
                Math.max(0, preparationMs), planMs, plan);
    }

    public VectorRecallDiagnostics compareRecall(
            String query,
            int topK,
            String category,
            LocalDate fromDate,
            LocalDate toDate,
            VectorRecallOptions options
    ) {
        validateRequest(query, topK, category, fromDate, toDate);
        var queryDocument = retrievalDocumentFactory.forQuery(query.trim());
        var cacheKey = new QueryEmbeddingCache.Key(query.trim().toLowerCase(java.util.Locale.ROOT),
                embeddingModel.modelName(), embeddingModel.dimensions(), RetrievalDocumentFactory.DOCUMENT_SCHEMA_VERSION);
        var embedding = queryEmbeddings.getOrLoad(cacheKey,
                () -> embeddingModel.embed(queryDocument, EmbeddingWorkClass.INTERACTIVE));
        return vectorStoreGateway.compareRecall(new VectorSearchRequest(
                embeddingModel.modelName(), embedding, topK, category, fromDate, toDate),
                options == null ? VectorRecallOptions.defaults() : options);
    }

    private static void validateRequest(
            String query,
            int topK,
            String category,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("query must not exceed " + MAX_QUERY_LENGTH + " characters");
        }
        if (topK < 1 || topK > 50) {
            throw new IllegalArgumentException("topK must be between 1 and 50");
        }
        if (category != null && category.isBlank()) {
            throw new IllegalArgumentException("category must not be blank");
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must not be after toDate");
        }
    }

    private <T> T measure(SearchStage stage, SearchStageObserver observer,
                          java.util.function.Supplier<T> supplier) {
        java.util.function.Supplier<T> aggregate = metrics == null
                ? supplier
                : () -> metrics.measure(stage, supplier);
        return observer == null ? aggregate.get() : observer.measure(stage, aggregate);
    }

    private static boolean matches(
            SemanticSearchResult result,
            String category,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        return (category == null || result.categories().contains(category))
                && (fromDate == null || (result.submittedDate() != null && !result.submittedDate().isBefore(fromDate)))
                && (toDate == null || (result.submittedDate() != null && !result.submittedDate().isAfter(toDate)));
    }

    private static SemanticSearchResult toResult(VectorSearchResult hit, Map<Long, Paper> papersById) {
        var paper = papersById.get(hit.paperId());
        if (paper == null) {
            return null;
        }
        return new SemanticSearchResult(
                paper.getId(),
                paper.getArxivId(),
                paper.getTitle(),
                paper.getAbstractText(),
                paper.getAuthors(),
                paper.getCategories(),
                paper.getSubmittedDate(),
                hit.score(),
                hit.text(),
                "vector"
        );
    }

    private static SemanticSearchResult toProjectedResult(VectorSearchResult hit) {
        return new SemanticSearchResult(hit.paperId(), hit.arxivId(), hit.title(), hit.abstractText(),
                hit.authors(), hit.categories(), hit.submittedDate(), hit.score(), hit.text(), "vector");
    }
}

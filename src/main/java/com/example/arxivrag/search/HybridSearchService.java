package com.example.arxivrag.search;

import com.example.arxivrag.vector.SemanticSearchResult;
import com.example.arxivrag.vector.SemanticSearchService;
import com.example.arxivrag.vector.SearchMetrics;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class HybridSearchService {

    private static final int MAX_CANDIDATES = 50;
    private final SemanticSearchService semanticSearchService;
    private final LexicalSearchService lexicalSearchService;
    private final ExecutorService executor;
    private final SearchRetrievalProperties properties;
    private final SearchMetrics metrics;

    public HybridSearchService(
            SemanticSearchService semanticSearchService,
            LexicalSearchService lexicalSearchService,
            @Qualifier("hybridSearchExecutor") ExecutorService executor,
            SearchRetrievalProperties properties,
            SearchMetrics metrics
    ) {
        this.semanticSearchService = semanticSearchService;
        this.lexicalSearchService = lexicalSearchService;
        this.executor = executor;
        properties.validate();
        this.properties = properties;
        this.metrics = metrics;
    }

    public List<SemanticSearchResult> search(
            String query, int topK, String category, LocalDate fromDate, LocalDate toDate) {
        return search(query, topK, category, fromDate, toDate, null);
    }

    public List<SemanticSearchResult> search(
            String query, int topK, String category, LocalDate fromDate, LocalDate toDate,
            com.example.arxivrag.vector.SearchStageObserver observer) {
        return search(query, topK, category, fromDate, toDate, observer,
                properties.getHybridVectorWeight(), properties.getHybridLexicalWeight());
    }

    public List<SemanticSearchResult> search(
            String query, int topK, String category, LocalDate fromDate, LocalDate toDate,
            com.example.arxivrag.vector.SearchStageObserver observer,
            double vectorWeight, double lexicalWeight) {
        var candidates = Math.min(topK * 3, MAX_CANDIDATES);
        var semantic = executor.submit(() -> semanticSearchService.search(
                query, candidates, category, fromDate, toDate, observer));
        var lexical = executor.submit(() -> lexicalSearchService.search(query, candidates, category, fromDate, toDate));
        try {
            var results = fuse(semantic.get(), lexical.get(), topK, vectorWeight, lexicalWeight);
            metrics.results("hybrid", results);
            return results;
        } catch (InterruptedException exception) {
            semantic.cancel(true);
            lexical.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Hybrid retrieval was interrupted", exception);
        } catch (ExecutionException exception) {
            semantic.cancel(true);
            lexical.cancel(true);
            var cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Hybrid retrieval failed", cause);
        }
    }

    static List<SemanticSearchResult> fuse(
            List<SemanticSearchResult> semantic, List<SemanticSearchResult> lexical, int topK) {
        return fuse(semantic, lexical, topK, 0.7d, 0.3d);
    }

    static List<SemanticSearchResult> fuse(
            List<SemanticSearchResult> semantic, List<SemanticSearchResult> lexical, int topK,
            double vectorWeight, double lexicalWeight) {
        var totalWeight = vectorWeight + lexicalWeight;
        if (!Double.isFinite(totalWeight) || totalWeight <= 0d || vectorWeight < 0d || lexicalWeight < 0d) {
            throw new IllegalArgumentException("hybrid weights must be finite, non-negative, and not both zero");
        }
        var combined = new LinkedHashMap<Long, HybridHit>();
        add(combined, semantic, vectorWeight / totalWeight, "vector");
        add(combined, lexical, lexicalWeight / totalWeight, "lexical");
        return combined.values().stream()
                .sorted(java.util.Comparator.comparingDouble(HybridHit::score).reversed())
                .limit(topK)
                .map(HybridHit::result)
                .toList();
    }

    private static void add(LinkedHashMap<Long, HybridHit> combined,
                            List<SemanticSearchResult> results, double weight, String source) {
        for (var index = 0; index < results.size(); index++) {
            var result = results.get(index);
            combined.computeIfAbsent(result.paperId(), ignored -> new HybridHit(result))
                    .addScore(weight / (60 + index + 1), source);
        }
    }

    private static final class HybridHit {
        private final SemanticSearchResult result;
        private double score;
        private boolean vector;
        private boolean lexical;
        private HybridHit(SemanticSearchResult result) { this.result = result; }
        private void addScore(double value, String source) {
            score += value;
            vector |= "vector".equals(source);
            lexical |= "lexical".equals(source);
        }
        private double score() { return score; }
        private SemanticSearchResult result() {
            var source = vector && lexical ? "both" : vector ? "vector" : "lexical";
            return new SemanticSearchResult(result.paperId(), result.arxivId(), result.title(), result.abstractText(),
                    result.authors(), result.categories(), result.submittedDate(), score, result.retrievalText(), source);
        }
    }
}

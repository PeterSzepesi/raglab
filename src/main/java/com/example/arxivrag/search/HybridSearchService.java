package com.example.arxivrag.search;

import com.example.arxivrag.vector.SemanticSearchResult;
import com.example.arxivrag.vector.SemanticSearchService;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class HybridSearchService {

    private static final int MAX_CANDIDATES = 50;
    private final SemanticSearchService semanticSearchService;
    private final LexicalSearchService lexicalSearchService;
    private final ExecutorService executor = Executors.newFixedThreadPool(2,
            Thread.ofVirtual().name("hybrid-search-", 0).factory());

    public HybridSearchService(SemanticSearchService semanticSearchService, LexicalSearchService lexicalSearchService) {
        this.semanticSearchService = semanticSearchService;
        this.lexicalSearchService = lexicalSearchService;
    }

    public List<SemanticSearchResult> search(
            String query, int topK, String category, LocalDate fromDate, LocalDate toDate) {
        return search(query, topK, category, fromDate, toDate, null);
    }

    public List<SemanticSearchResult> search(
            String query, int topK, String category, LocalDate fromDate, LocalDate toDate,
            com.example.arxivrag.vector.SearchStageObserver observer) {
        var candidates = Math.min(topK * 3, MAX_CANDIDATES);
        var semantic = executor.submit(() -> semanticSearchService.search(
                query, candidates, category, fromDate, toDate, observer));
        var lexical = executor.submit(() -> lexicalSearchService.search(query, candidates, category, fromDate, toDate));
        try {
            return fuse(semantic.get(), lexical.get(), topK);
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
        var combined = new LinkedHashMap<Long, HybridHit>();
        add(combined, semantic, 0.7d);
        add(combined, lexical, 0.3d);
        return combined.values().stream()
                .sorted(java.util.Comparator.comparingDouble(HybridHit::score).reversed())
                .limit(topK)
                .map(HybridHit::result)
                .toList();
    }

    private static void add(LinkedHashMap<Long, HybridHit> combined,
                            List<SemanticSearchResult> results, double weight) {
        for (var index = 0; index < results.size(); index++) {
            var result = results.get(index);
            combined.computeIfAbsent(result.paperId(), ignored -> new HybridHit(result))
                    .addScore(weight / (60 + index + 1));
        }
    }

    @PreDestroy
    void close() {
        executor.shutdownNow();
    }

    private static final class HybridHit {
        private final SemanticSearchResult result;
        private double score;
        private HybridHit(SemanticSearchResult result) { this.result = result; }
        private void addScore(double value) { score += value; }
        private double score() { return score; }
        private SemanticSearchResult result() { return result; }
    }
}

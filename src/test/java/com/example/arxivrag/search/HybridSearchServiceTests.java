package com.example.arxivrag.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.arxivrag.vector.SemanticSearchResult;
import com.example.arxivrag.vector.SemanticSearchService;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class HybridSearchServiceTests {

    @Test
    void startsSemanticAndLexicalBranchesConcurrently() throws Exception {
        var semantic = mock(SemanticSearchService.class);
        var lexical = mock(LexicalSearchService.class);
        var bothStarted = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        when(semantic.search(anyString(), anyInt(), any(), any(), any(), any()))
                .thenAnswer(ignored -> delayed(bothStarted, release, result(1L, "2401.00001")));
        when(lexical.search(anyString(), anyInt(), any(), any(), any()))
                .thenAnswer(ignored -> delayed(bothStarted, release, result(2L, "2401.00002")));
        var hybridExecutor = Executors.newVirtualThreadPerTaskExecutor();
        var service = new HybridSearchService(semantic, lexical, hybridExecutor, new SearchRetrievalProperties(),
                new com.example.arxivrag.vector.SearchMetrics(new SimpleMeterRegistry()));
        var caller = Executors.newSingleThreadExecutor();
        try {
            var future = caller.submit(() -> service.search("query", 2, null, null, null));
            assertThat(bothStarted.await(1, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            assertThat(future.get(1, TimeUnit.SECONDS)).hasSize(2);
        } finally {
            release.countDown();
            caller.shutdownNow();
            hybridExecutor.shutdownNow();
        }
    }

    @Test
    void fusedResultReportsBothRetrievalSourcesAndTheFusedScore() {
        var hit = result(1L, "2401.00001");

        var fused = HybridSearchService.fuse(List.of(hit), List.of(hit), 1).getFirst();

        assertThat(fused.retrievalSource()).isEqualTo("both");
        assertThat(fused.score()).isCloseTo(1.0d / 61.0d,
                org.assertj.core.data.Offset.offset(0.0000001d));
    }

    @Test
    void configurableWeightsChangeTheFixedRankFusionOrder() {
        var vectorOnly = result(1L, "2401.00001");
        var lexicalOnly = result(2L, "2401.00002");

        var fused = HybridSearchService.fuse(List.of(vectorOnly), List.of(lexicalOnly), 2, 0.2d, 0.8d);

        assertThat(fused).extracting(SemanticSearchResult::arxivId)
                .containsExactly("2401.00002", "2401.00001");
    }

    private static List<SemanticSearchResult> delayed(
            CountDownLatch started, CountDownLatch release, SemanticSearchResult result) throws Exception {
        started.countDown();
        release.await(1, TimeUnit.SECONDS);
        return List.of(result);
    }

    private static SemanticSearchResult result(long paperId, String arxivId) {
        return new SemanticSearchResult(paperId, arxivId, "Title", "Abstract", List.of(), List.of("cs.AI"),
                LocalDate.of(2024, 1, 1), 0.8, "text");
    }
}

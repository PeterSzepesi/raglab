package com.example.arxivrag.search;

import com.example.arxivrag.vector.SemanticSearchResult;
import com.example.arxivrag.vector.SemanticSearchService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        var service = new HybridSearchService(semantic, lexical);
        var caller = Executors.newSingleThreadExecutor();
        try {
            var future = caller.submit(() -> service.search("query", 2, null, null, null));
            assertThat(bothStarted.await(1, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            assertThat(future.get(1, TimeUnit.SECONDS)).hasSize(2);
        } finally {
            release.countDown();
            caller.shutdownNow();
            service.close();
        }
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

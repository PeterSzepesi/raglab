package com.example.arxivrag.ai.chat;

import com.example.arxivrag.embedding.RetrievalDocumentFactory;
import com.example.arxivrag.paper.Paper;
import com.example.arxivrag.paper.PaperRepository;
import com.example.arxivrag.search.CorpusGeneration;
import com.example.arxivrag.search.HybridSearchService;
import com.example.arxivrag.search.LexicalSearchService;
import com.example.arxivrag.vector.SemanticSearchResult;
import com.example.arxivrag.vector.SemanticSearchService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatRetrievalPlannerTests {

    @Test
    void exactIdentifierBypassesEmbeddingSearch() {
        var fixture = fixture();
        when(fixture.papers.findByArxivId("2401.00001")).thenReturn(Optional.of(paper("2401.00001")));

        var result = fixture.planner.retrieve(new ChatRequest("Summarize arXiv 2401.00001."));

        assertThat(result.results()).extracting(SemanticSearchResult::arxivId).containsExactly("2401.00001");
        assertThat(result.toolsUsed()).containsExactly(RetrievalToolService.PAPER_LOOKUP);
        verify(fixture.semantic, never()).search(anyString(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void repeatedSemanticQueryUsesRetrievalCacheUntilCorpusChanges() {
        var fixture = fixture();
        var result = semanticResult("2401.00001");
        when(fixture.semantic.search(anyString(), anyInt(), any(), any(), any(), any()))
                .thenReturn(List.of(result));
        var request = new ChatRequest("neural retrieval");

        fixture.planner.retrieve(request);
        fixture.planner.retrieve(request);
        verify(fixture.semantic, times(1)).search(anyString(), anyInt(), any(), any(), any(), any());

        fixture.generation.increment();
        fixture.planner.retrieve(request);
        verify(fixture.semantic, times(2)).search(anyString(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void structuredFiltersUseRelationalLookupWithoutEmbedding() {
        var fixture = fixture();
        when(fixture.papers.searchRelational(null, "cs.AI", LocalDate.of(2024, 1, 1), null, 4))
                .thenReturn(List.of(paper("2401.00001")));

        var result = fixture.planner.retrieve(new ChatRequest(
                "Show recent AI papers", null, "cs.AI", LocalDate.of(2024, 1, 1), null, false));

        assertThat(result.results()).hasSize(1);
        verify(fixture.semantic, never()).search(anyString(), anyInt(), any(), any(), any(), any());
    }

    private static Fixture fixture() {
        var semantic = mock(SemanticSearchService.class);
        var lexical = mock(LexicalSearchService.class);
        var hybrid = mock(HybridSearchService.class);
        var papers = mock(PaperRepository.class);
        var properties = new ChatProperties();
        var generation = new CorpusGeneration();
        var registry = new SimpleMeterRegistry();
        var metrics = new ChatMetrics(registry);
        var cache = new ChatRetrievalCache(properties, metrics, registry);
        when(semantic.embeddingModelName()).thenReturn("text-embedding-3-large");
        var planner = new ChatRetrievalPlanner(semantic, lexical, hybrid, papers,
                new RetrievalDocumentFactory(), properties, generation, cache);
        return new Fixture(planner, semantic, papers, generation);
    }

    private static Paper paper(String id) {
        return new Paper(id, "Fixture title", "Fixture abstract", List.of("Ada"), List.of("cs.AI"),
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2), null, null);
    }

    private static SemanticSearchResult semanticResult(String id) {
        return new SemanticSearchResult(1L, id, "Fixture title", "Fixture abstract", List.of("Ada"),
                List.of("cs.AI"), LocalDate.of(2024, 1, 1), 0.9, "Fixture abstract");
    }

    private record Fixture(ChatRetrievalPlanner planner, SemanticSearchService semantic,
                           PaperRepository papers, CorpusGeneration generation) {
    }
}

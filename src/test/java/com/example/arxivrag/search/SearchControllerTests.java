package com.example.arxivrag.search;

import com.example.arxivrag.vector.SemanticSearchResult;
import com.example.arxivrag.vector.SemanticSearchService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchControllerTests {

    @Test
    void delegatesQueryAndFiltersToSemanticSearch() {
        var service = mock(SemanticSearchService.class);
        when(service.embeddingModelName()).thenReturn("text-embedding-3-large");
        when(service.search("neural retrieval", 5, "cs.AI", LocalDate.of(2024, 1, 1), null))
                .thenReturn(List.of());

        var response = new SearchController(service).search(new SearchRequest(
                " neural retrieval ", 5, "cs.AI", LocalDate.of(2024, 1, 1), null));

        assertThat(response.query()).isEqualTo("neural retrieval");
        assertThat(response.embeddingModel()).isEqualTo("text-embedding-3-large");
        assertThat(response.results()).isEmpty();
    }

    @Test
    void defaultsTopKAndRejectsUnsafeBounds() {
        var service = mock(SemanticSearchService.class);
        when(service.embeddingModelName()).thenReturn("test-model");
        when(service.search("query", 10, null, null, null)).thenReturn(List.of());
        var controller = new SearchController(service);

        assertThat(controller.search(new SearchRequest("query", null, null, null, null)).topK()).isEqualTo(10);
        assertThatThrownBy(() -> controller.search(new SearchRequest("query", 51, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 50");
    }
}

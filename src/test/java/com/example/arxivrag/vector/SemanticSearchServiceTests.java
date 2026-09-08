package com.example.arxivrag.vector;

import com.example.arxivrag.embedding.EmbeddingModelGateway;
import com.example.arxivrag.embedding.RetrievalDocumentFactory;
import com.example.arxivrag.paper.Paper;
import com.example.arxivrag.paper.PaperRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SemanticSearchServiceTests {

    private final EmbeddingModelGateway embeddingModel = mock(EmbeddingModelGateway.class);

    private final RetrievalDocumentFactory retrievalDocumentFactory = new RetrievalDocumentFactory();

    private final VectorStoreGateway vectorStoreGateway = mock(VectorStoreGateway.class);

    private final PaperRepository paperRepository = mock(PaperRepository.class);

    private final SemanticSearchService service = new SemanticSearchService(
            embeddingModel,
            retrievalDocumentFactory,
            vectorStoreGateway,
            paperRepository
    );

    @Test
    void embedsQuerySearchesGatewayAndResolvesCanonicalPaper() {
        var paper = mock(Paper.class);
        when(paper.getId()).thenReturn(42L);
        when(paper.getArxivId()).thenReturn("2401.00042");
        when(paper.getTitle()).thenReturn("Semantic retrieval");
        when(paper.getAbstractText()).thenReturn("A paper about semantic retrieval.");
        when(paper.getAuthors()).thenReturn(List.of("Ada Lovelace"));
        when(paper.getCategories()).thenReturn(List.of("cs.CL"));
        when(paper.getSubmittedDate()).thenReturn(LocalDate.of(2024, 1, 2));

        when(embeddingModel.modelName()).thenReturn("test-model");
        when(embeddingModel.dimensions()).thenReturn(2);
        when(embeddingModel.embed(any())).thenReturn(List.of(1.0d, 0.0d));
        when(vectorStoreGateway.search(any())).thenReturn(List.of(new VectorSearchResult(
                42L,
                "2401.00042",
                "title-abstract",
                "hash",
                "test-model",
                0.98d,
                "Title: Semantic retrieval"
        )));
        when(paperRepository.findAllById(List.of(42L))).thenReturn(List.of(paper));

        var results = service.search("semantic retrieval", 3);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.paperId()).isEqualTo(42L);
            assertThat(result.title()).isEqualTo("Semantic retrieval");
            assertThat(result.score()).isEqualTo(0.98d);
            assertThat(result.categories()).containsExactly("cs.CL");
        });
    }

    @Test
    void emptyVectorStoreProducesEmptyResult() {
        when(embeddingModel.modelName()).thenReturn("test-model");
        when(embeddingModel.dimensions()).thenReturn(2);
        when(embeddingModel.embed(any())).thenReturn(List.of(1.0d, 0.0d));
        when(vectorStoreGateway.search(any())).thenReturn(List.of());

        assertThat(service.search("no matching paper")).isEmpty();
    }

    @Test
    void rejectsBlankQueriesAndNonPositiveTopK() {
        assertThatThrownBy(() -> service.search("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("query must not be blank");
        assertThatThrownBy(() -> service.search("query", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("topK must be greater than zero");
    }
}

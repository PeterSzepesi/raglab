package com.example.arxivrag.paper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.arxivrag.embedding.EmbeddingCheckpoint;
import com.example.arxivrag.embedding.EmbeddingCheckpointId;
import com.example.arxivrag.embedding.EmbeddingCheckpointRepository;
import com.example.arxivrag.embedding.EmbeddingModelGateway;
import com.example.arxivrag.embedding.RetrievalDocument;
import com.example.arxivrag.embedding.RetrievalDocumentFactory;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PaperBrowseControllerTests {

    private final PaperRepository papers = mock(PaperRepository.class);
    private final EmbeddingCheckpointRepository checkpoints = mock(EmbeddingCheckpointRepository.class);
    private final EmbeddingModelGateway embeddingModel = mock(EmbeddingModelGateway.class);
    private final RetrievalDocumentFactory documents = mock(RetrievalDocumentFactory.class);
    private final PaperBrowseController controller =
            new PaperBrowseController(papers, checkpoints, embeddingModel, documents);

    @BeforeEach
    void configureModel() {
        when(embeddingModel.modelName()).thenReturn("test-model");
    }

    @Test
    void returnsCursorPageWithCanonicalLinksAndCurrentEmbeddingState() {
        var paper = paper(42L, "2401.00042", "A useful paper");
        var hash = "a".repeat(64);
        var retrievalDocument = document(paper, hash);
        when(papers.browse(any(), any(), any(), any(), any(), eq("cs.AI"), any(), any(), any()))
                .thenReturn(List.of(paper));
        when(documents.fromPaper(paper)).thenReturn(retrievalDocument);
        when(checkpoints.findByPaperIdsAndModel(eq("test-model"), anyCollection())).thenReturn(List.of(
                new EmbeddingCheckpoint(new EmbeddingCheckpointId(42L, RetrievalDocumentFactory.CHUNK_ID,
                        "test-model", hash, RetrievalDocumentFactory.DOCUMENT_SCHEMA_VERSION), 1536)));

        var page = controller.browse(null, null, null, null, null, "cs.AI", null, null, "current", 25);

        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.arxivId()).isEqualTo("2401.00042");
            assertThat(item.abstractUrl()).isEqualTo("https://arxiv.org/abs/2401.00042");
            assertThat(item.pdfUrl()).isEqualTo("https://arxiv.org/pdf/2401.00042");
            assertThat(item.embeddingStatus()).isEqualTo("CURRENT");
        });
    }

    @Test
    void filtersMissingEmbeddingsAndLoadsDetailOnDemand() {
        var paper = paper(7L, "hep-th/9901001", "Legacy identifier");
        var hash = "b".repeat(64);
        var retrievalDocument = document(paper, hash);
        when(papers.browse(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(paper));
        when(papers.findByArxivId("hep-th/9901001")).thenReturn(Optional.of(paper));
        when(documents.fromPaper(paper)).thenReturn(retrievalDocument);
        when(checkpoints.findByPaperIdsAndModel(eq("test-model"), anyCollection())).thenReturn(List.of());

        var page = controller.browse(null, null, null, null, null, null, null, null, "MISSING", 10);
        var detail = controller.find("hep-th/9901001");

        assertThat(page.items()).singleElement().extracting(PaperBrowseController.PaperSummary::embeddingStatus)
                .isEqualTo("MISSING");
        assertThat(detail.embedded()).isFalse();
        assertThat(detail.abstractUrl()).isEqualTo("https://arxiv.org/abs/hep-th/9901001");
    }

    @Test
    void rejectsInvalidFiltersAndReturnsNotFoundForUnknownPaper() {
        assertThatThrownBy(() -> controller.browse(null, null, null, null, null, null,
                LocalDate.of(2025, 1, 2), LocalDate.of(2025, 1, 1), null, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fromDate");
        assertThatThrownBy(() -> controller.browse(null, null, null, null, null, null,
                null, null, "obsolete", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embeddingState");
        assertThatThrownBy(() -> controller.browse(null, null, null, null, null, null,
                null, null, null, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
        when(papers.findByArxivId("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> controller.find("missing"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Paper not found");
    }

    private static Paper paper(long id, String arxivId, String title) {
        var paper = mock(Paper.class);
        when(paper.getId()).thenReturn(id);
        when(paper.getArxivId()).thenReturn(arxivId);
        when(paper.getTitle()).thenReturn(title);
        when(paper.getAbstractText()).thenReturn("An abstract used by the paper browser.");
        when(paper.getAuthors()).thenReturn(List.of("Ada Lovelace"));
        when(paper.getCategories()).thenReturn(List.of("cs.AI"));
        when(paper.getSubmittedDate()).thenReturn(LocalDate.of(2024, 1, 2));
        return paper;
    }

    private static RetrievalDocument document(Paper paper, String hash) {
        return new RetrievalDocument(paper.getId(), paper.getArxivId(), RetrievalDocumentFactory.CHUNK_ID,
                "document", hash, paper.getCategories(), paper.getSubmittedDate());
    }
}

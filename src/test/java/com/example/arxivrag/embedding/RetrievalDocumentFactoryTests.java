package com.example.arxivrag.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.arxivrag.paper.Paper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetrievalDocumentFactoryTests {

    private final RetrievalDocumentFactory factory = new RetrievalDocumentFactory();

    @Test
    void createsStableTitleAbstractDocument() {
        var document = factory.preview(
                "2401.00001",
                " Retrieval\n Testing ",
                " A concise\nabstract\tfor testing. "
        );

        assertThat(document.chunkId()).isEqualTo("title-abstract");
        assertThat(document.text()).isEqualTo("""
                Title: Retrieval Testing

                Abstract:
                A concise abstract for testing.""");
        assertThat(document.contentHash()).hasSize(64);
        assertThat(document.contentHash()).isEqualTo(factory.preview(
                "2401.00001",
                "Retrieval Testing",
                "A concise abstract for testing."
        ).contentHash());
    }

    @Test
    void preservesPaperMetadataForLaterVectorStorage() {
        var paper = new Paper(
                "2401.00001",
                "Retrieval Testing",
                "A concise abstract.",
                List.of("Ada Lovelace"),
                List.of("cs.CL", "cs.AI"),
                LocalDate.of(2024, 1, 2),
                LocalDate.of(2024, 1, 3),
                null,
                null
        );

        var document = factory.fromPaper(paper);

        assertThat(document.arxivId()).isEqualTo("2401.00001");
        assertThat(document.categories()).containsExactly("cs.CL", "cs.AI");
        assertThat(document.submittedDate()).isEqualTo(LocalDate.of(2024, 1, 2));
    }
}

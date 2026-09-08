package com.example.arxivrag.embedding;

import com.example.arxivrag.arxiv.ArxivImportService;
import com.example.arxivrag.paper.PaperRepository;
import com.example.arxivrag.vector.VectorStoreGateway;
import com.example.arxivrag.vector.InMemoryVectorStoreGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class EmbeddingIngestionServiceTests {

    @jakarta.annotation.Resource
    private ArxivImportService importService;

    @jakarta.annotation.Resource
    private EmbeddingIngestionService embeddingIngestionService;

    @jakarta.annotation.Resource
    private EmbeddingCheckpointRepository checkpointRepository;

    @jakarta.annotation.Resource
    private PaperRepository paperRepository;

    @jakarta.annotation.Resource
    private VectorStoreGateway vectorStoreGateway;

    @jakarta.annotation.Resource
    private InMemoryVectorStoreGateway inMemoryVectorStoreGateway;

    @BeforeEach
    void deleteExistingData() {
        checkpointRepository.deleteAll();
        paperRepository.deleteAll();
        inMemoryVectorStoreGateway.clear();
    }

    @Test
    void createsCheckpointsForImportedPapers() {
        importService.importDataset(Path.of("src/test/resources/fixtures/arxiv-tiny.jsonl"));

        var result = embeddingIngestionService.ingestAll();

        assertThat(result.papersScanned()).isEqualTo(2);
        assertThat(result.documentsPrepared()).isEqualTo(2);
        assertThat(result.embedded()).isEqualTo(2);
        assertThat(result.skipped()).isZero();
        assertThat(result.embeddingModel()).isEqualTo("offline-deterministic-1536");
        assertThat(result.embeddingDimension()).isEqualTo(1536);
        assertThat(checkpointRepository.count()).isEqualTo(2);
        assertThat(vectorStoreGateway.countByEmbeddingModel("offline-deterministic-1536")).isEqualTo(2);
    }

    @Test
    void skipsUnchangedDocumentsOnSecondRun() {
        importService.importDataset(Path.of("src/test/resources/fixtures/arxiv-tiny.jsonl"));
        embeddingIngestionService.ingestAll();

        var result = embeddingIngestionService.ingestAll();

        assertThat(result.embedded()).isZero();
        assertThat(result.skipped()).isEqualTo(2);
        assertThat(checkpointRepository.count()).isEqualTo(2);
        assertThat(vectorStoreGateway.countByEmbeddingModel("offline-deterministic-1536")).isEqualTo(2);
    }

    @Test
    void changedContentHashCreatesNewCheckpoint() {
        importService.importDataset(Path.of("src/test/resources/fixtures/arxiv-tiny.jsonl"));
        embeddingIngestionService.ingestAll();
        var paper = paperRepository.findByArxivId("2401.00001").orElseThrow();
        paper.setAbstractText("A changed abstract that should produce a different hash.");
        paperRepository.saveAndFlush(paper);

        var result = embeddingIngestionService.ingestAll();

        assertThat(result.embedded()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(checkpointRepository.count()).isEqualTo(3);
        assertThat(vectorStoreGateway.countByEmbeddingModel("offline-deterministic-1536")).isEqualTo(2);
    }
}

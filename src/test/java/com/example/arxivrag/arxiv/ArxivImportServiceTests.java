package com.example.arxivrag.arxiv;

import com.example.arxivrag.paper.PaperRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class ArxivImportServiceTests {

    @Autowired
    private ArxivImportService importService;

    @Autowired
    private PaperRepository paperRepository;

    @BeforeEach
    void deleteExistingPapers() {
        paperRepository.deleteAll();
    }

    @Test
    void importsTinyFixtureWithFilteringAndInvalidRecordCounts() {
        var result = importService.importDataset(Path.of("src/test/resources/fixtures/arxiv-tiny.jsonl"));

        assertThat(result.recordsRead()).isEqualTo(4);
        assertThat(result.accepted()).isEqualTo(2);
        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.updated()).isZero();
        assertThat(result.skipped()).isEqualTo(2);
        assertThat(result.invalidRecords()).isEqualTo(1);
        assertThat(paperRepository.findAll()).hasSize(2);
    }

    @Test
    void reimportUpdatesExistingRowsWithoutDuplicates() {
        importService.importDataset(Path.of("src/test/resources/fixtures/arxiv-tiny.jsonl"));

        var result = importService.importDataset(Path.of("src/test/resources/fixtures/arxiv-tiny.jsonl"));

        assertThat(result.imported()).isZero();
        assertThat(result.updated()).isEqualTo(2);
        assertThat(paperRepository.findAll()).hasSize(2);
    }
}

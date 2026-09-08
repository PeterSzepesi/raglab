package com.example.arxivrag.embedding;

import com.example.arxivrag.arxiv.ArxivImportService;
import com.example.arxivrag.paper.PaperRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EmbeddingStatusControllerTests {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @jakarta.annotation.Resource
    private ArxivImportService importService;

    @jakarta.annotation.Resource
    private EmbeddingIngestionService embeddingIngestionService;

    @jakarta.annotation.Resource
    private EmbeddingCheckpointRepository checkpointRepository;

    @jakarta.annotation.Resource
    private PaperRepository paperRepository;

    @BeforeEach
    void deleteExistingData() {
        checkpointRepository.deleteAll();
        paperRepository.deleteAll();
    }

    @Test
    void reportsEmbeddingCheckpointCoverage() throws Exception {
        importService.importDataset(Path.of("src/test/resources/fixtures/arxiv-tiny.jsonl"));
        embeddingIngestionService.ingestAll();

        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/embeddings/status"))
                .GET()
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isBetween(200, 299);
        assertThat(response.body()).contains("\"papers\":2");
        assertThat(response.body()).contains("\"checkpoints\":2");
        assertThat(response.body()).contains("\"checkpointedPapers\":2");
        assertThat(response.body()).contains("\"coveragePercent\":100.0");
        assertThat(response.body()).contains("\"embeddingModel\":\"offline-deterministic-1536\"");
    }
}

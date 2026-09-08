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
class RetrievalDocumentControllerTests {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @jakarta.annotation.Resource
    private ArxivImportService importService;

    @jakarta.annotation.Resource
    private PaperRepository paperRepository;

    @BeforeEach
    void deleteExistingPapers() {
        paperRepository.deleteAll();
    }

    @Test
    void previewsRetrievalDocumentWithoutStoredPaper() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/retrieval-documents/preview"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {
                          "arxivId": "2401.00001",
                          "title": "Retrieval Testing",
                          "abstractText": "A concise abstract."
                        }
                        """))
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isBetween(200, 299);
        assertThat(response.body()).contains("\"chunkId\":\"title-abstract\"");
        assertThat(response.body()).contains("Title: Retrieval Testing");
        assertThat(response.body()).contains("\"contentHash\"");
    }

    @Test
    void createsRetrievalDocumentFromImportedPaper() throws Exception {
        importService.importDataset(Path.of("src/test/resources/fixtures/arxiv-tiny.jsonl"));

        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/retrieval-documents/papers/2401.00001"))
                .GET()
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isBetween(200, 299);
        assertThat(response.body()).contains("\"arxivId\":\"2401.00001\"");
        assertThat(response.body()).contains("\"chunkId\":\"title-abstract\"");
        assertThat(response.body()).contains("Title: Retrieval Testing");
        assertThat(response.body()).contains("\"categories\":[\"cs.CL\",\"cs.AI\"]");
    }
}

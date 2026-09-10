package com.example.arxivrag.embedding;

import com.example.arxivrag.arxiv.ArxivImportService;
import com.example.arxivrag.paper.PaperRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
class EmbeddingStatusControllerTests {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

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
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        checkpointRepository.deleteAll();
        paperRepository.deleteAll();
    }

    @Test
    void reportsEmbeddingCheckpointCoverage() throws Exception {
        importService.importDataset(Path.of("src/test/resources/fixtures/arxiv-tiny.jsonl"));
        embeddingIngestionService.ingestAll();

        var response = mockMvc.perform(get("/api/embeddings/status"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn();

        assertThat(response.getResponse().getContentAsString()).contains("\"papers\":2");
        assertThat(response.getResponse().getContentAsString()).contains("\"checkpoints\":2");
        assertThat(response.getResponse().getContentAsString()).contains("\"checkpointedPapers\":2");
        assertThat(response.getResponse().getContentAsString()).contains("\"coveragePercent\":100.0");
        assertThat(response.getResponse().getContentAsString()).contains("\"embeddingModel\":\"offline-deterministic-1536\"");
        assertThat(response.getResponse().getContentAsString()).contains("\"jobStatus\":\"COMPLETED\"");
        assertThat(response.getResponse().getContentAsString()).contains("\"jobEmbedded\":2");
    }
}

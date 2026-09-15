package com.example.arxivrag.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.arxivrag.arxiv.ArxivImportService;
import com.example.arxivrag.paper.PaperRepository;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("test")
@SpringBootTest
class RetrievalDocumentControllerTests {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @jakarta.annotation.Resource
    private ArxivImportService importService;

    @jakarta.annotation.Resource
    private PaperRepository paperRepository;

    @BeforeEach
    void deleteExistingPapers() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        paperRepository.deleteAll();
    }

    @Test
    void previewsRetrievalDocumentWithoutStoredPaper() throws Exception {
        var response = mockMvc.perform(post("/api/retrieval-documents/preview")
                        .contentType("application/json")
                        .content("""
                        {
                          "arxivId": "2401.00001",
                          "title": "Retrieval Testing",
                          "abstractText": "A concise abstract."
                        }
                        """))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).contains("\"chunkId\":\"title-abstract\"");
        assertThat(response).contains("Title: Retrieval Testing");
        assertThat(response).contains("\"contentHash\"");
    }

    @Test
    void createsRetrievalDocumentFromImportedPaper() throws Exception {
        importService.importDataset(Path.of("src/test/resources/fixtures/arxiv-tiny.jsonl"));

        var response = mockMvc.perform(get("/api/retrieval-documents/papers/2401.00001"))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).contains("\"arxivId\":\"2401.00001\"");
        assertThat(response).contains("\"chunkId\":\"title-abstract\"");
        assertThat(response).contains("Title: Retrieval Testing");
        assertThat(response).contains("\"categories\":[\"cs.CL\",\"cs.AI\"]");
    }
}

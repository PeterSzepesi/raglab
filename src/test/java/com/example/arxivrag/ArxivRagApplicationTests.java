package com.example.arxivrag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("test")
@SpringBootTest(properties = "openai.api-key=super-secret-token")
class ArxivRagApplicationTests {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void contextLoads() {
    }

    @Test
    void servesFrontendShell() throws Exception {
        var response = performGet("/index.html");

        assertThat(response).contains("arXiv RAG Lab");
    }

    @Test
    void exposesOnlySafeRuntimeConfig() throws Exception {
        var response = performGet("/api/runtime-config");

        assertThat(response).contains("\"configuredProfile\":\"test\"");
        assertThat(response).contains("\"activeProfiles\":[\"test\"]");
        assertThat(response).contains("\"features\"");
        assertThat(response).doesNotContain("super-secret-token");
        assertThat(response).doesNotContain("\"apiKey\"");
        assertThat(response).doesNotContain("\"tokenScope\"");
        assertThat(response).doesNotContain("password");
    }

    @Test
    void exposesBackendHealth() throws Exception {
        var response = performGet("/api/health");

        assertThat(response).contains("\"status\":\"UP\"");
        assertThat(response).contains("\"applicationName\":\"arxiv-rag\"");
        assertThat(response).contains("\"message\":\"Backend services are reachable.\"");
    }

    private String performGet(String path) throws Exception {
        return mockMvc.perform(get(path))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}

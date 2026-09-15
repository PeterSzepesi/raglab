package com.example.arxivrag.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OpenApiContractTests {

    @Test
    void versionedPublicApiAndProblemContractAreDocumented() throws Exception {
        var specification = Files.readString(Path.of("docs/api/openapi.yaml"));

        assertThat(specification)
                .contains("openapi: 3.1.0", "  /health:", "  /capabilities:", "  /search:",
                        "  /chat:", "  /chat/stream:", "  /papers:", "  /embeddings/jobs:",
                        "  /imports/jobs:", "    Problem:")
                .contains("requestId:", "AdminToken:", "application/problem+json");
    }
}

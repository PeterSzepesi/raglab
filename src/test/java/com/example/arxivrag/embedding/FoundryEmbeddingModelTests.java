package com.example.arxivrag.embedding;

import com.example.arxivrag.ai.foundry.FoundryClient;
import com.example.arxivrag.ai.foundry.FoundryProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FoundryEmbeddingModelTests {

    @Test
    void usesFoundryDeploymentAndPreservesConfiguredDimension() {
        var client = mock(FoundryClient.class);
        var foundry = new FoundryProperties();
        foundry.setEmbeddingDeployment("text-embedding-3-large");
        var embedding = new EmbeddingIngestionProperties();
        embedding.setModelName("text-embedding-3-large");
        embedding.setDimensions(3);
        when(client.embed("text-embedding-3-large", "paper text", 3))
                .thenReturn(List.of(0.1, 0.2, 0.3));

        var model = new FoundryEmbeddingModel(client, foundry, embedding);

        assertThat(model.modelName()).isEqualTo("text-embedding-3-large");
        assertThat(model.dimensions()).isEqualTo(3);
        assertThat(model.embed(document())).containsExactly(0.1, 0.2, 0.3);
    }

    @Test
    void rejectsProviderDimensionMismatch() {
        var client = mock(FoundryClient.class);
        var foundry = new FoundryProperties();
        foundry.setEmbeddingDeployment("text-embedding-3-large");
        var embedding = new EmbeddingIngestionProperties();
        embedding.setModelName("text-embedding-3-large");
        embedding.setDimensions(3);
        when(client.embed("text-embedding-3-large", "paper text", 3))
                .thenReturn(List.of(0.1, 0.2));

        var model = new FoundryEmbeddingModel(client, foundry, embedding);

        assertThatThrownBy(() -> model.embed(document()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected 3");
    }

    private static RetrievalDocument document() {
        return new RetrievalDocument(1L, "1234.5678", "paper", "paper text", "hash",
                List.of("cs.AI"), LocalDate.of(2024, 1, 1));
    }
}

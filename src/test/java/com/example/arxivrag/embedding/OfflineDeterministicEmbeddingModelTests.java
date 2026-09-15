package com.example.arxivrag.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OfflineDeterministicEmbeddingModelTests {

    @Test
    void returnsStableConfiguredDimensionVector() {
        var properties = new EmbeddingIngestionProperties();
        properties.setDimensions(16);
        properties.setModelName("offline-test");
        var model = new OfflineDeterministicEmbeddingModel(properties);
        var factory = new RetrievalDocumentFactory();
        var document = factory.preview("2401.00001", "Retrieval Testing", "A concise abstract.");

        var first = model.embed(document);
        var second = model.embed(document);

        assertThat(first).hasSize(16);
        assertThat(second).containsExactlyElementsOf(first);
        assertThat(model.modelName()).isEqualTo("offline-test");
        assertThat(model.dimensions()).isEqualTo(16);
    }
}

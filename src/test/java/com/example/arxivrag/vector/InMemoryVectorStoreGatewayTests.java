package com.example.arxivrag.vector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryVectorStoreGatewayTests {

    private final InMemoryVectorStoreGateway gateway = new InMemoryVectorStoreGateway();

    @Test
    void returnsNearestDocumentsInDescendingScoreOrder() {
        gateway.upsert(document(1L, "near", List.of(1.0d, 0.0d)));
        gateway.upsert(document(2L, "far", List.of(0.0d, 1.0d)));

        var results = gateway.search(new VectorSearchRequest("test-model", List.of(1.0d, 0.0d), 2));

        assertThat(results).extracting(VectorSearchResult::paperId).containsExactly(1L, 2L);
        assertThat(results.get(0).score()).isGreaterThan(results.get(1).score());
    }

    @Test
    void upsertReplacesTheSamePaperChunkAndModel() {
        gateway.upsert(document(1L, "old", List.of(1.0d, 0.0d)));
        gateway.upsert(document(1L, "new", List.of(0.0d, 1.0d)));

        var results = gateway.search(new VectorSearchRequest("test-model", List.of(0.0d, 1.0d), 1));

        assertThat(gateway.countByEmbeddingModel("test-model")).isEqualTo(1);
        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.text()).isEqualTo("new");
            assertThat(result.paperId()).isEqualTo(1L);
        });
    }

    @Test
    void emptyStoreReturnsNoResults() {
        assertThat(gateway.search(new VectorSearchRequest("test-model", List.of(1.0d, 0.0d), 10))).isEmpty();
    }

    private static EmbeddedDocument document(Long paperId, String text, List<Double> embedding) {
        return new EmbeddedDocument(
                paperId,
                "2401." + paperId,
                "title-abstract",
                "hash-" + paperId + "-" + text,
                "test-model",
                embedding.size(),
                text,
                List.of("cs.CL"),
                null,
                embedding
        );
    }
}

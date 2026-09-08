package com.example.arxivrag.vector;

import com.example.arxivrag.PostgresIntegrationTest;
import com.example.arxivrag.paper.Paper;
import com.example.arxivrag.paper.PaperRepository;
import com.example.arxivrag.vector.pgvector.PgVectorStoreGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@TestPropertySource(properties = "vector.store.type=pgvector")
@SpringBootTest
class PgVectorStoreGatewayIT extends PostgresIntegrationTest {

    @Autowired
    private PgVectorStoreGateway gateway;

    @Autowired
    private PaperRepository paperRepository;

    @BeforeEach
    void deleteExistingData() {
        paperRepository.deleteAll();
    }

    @Test
    void returnsKnownNearestVectorsInScoreOrder() {
        var near = paperRepository.saveAndFlush(paper("2401.10001", "Near vector"));
        var far = paperRepository.saveAndFlush(paper("2401.10002", "Far vector"));
        gateway.upsert(document(near.getId(), near.getArxivId(), 1.0d, 0.0d));
        gateway.upsert(document(far.getId(), far.getArxivId(), 0.0d, 1.0d));

        var results = gateway.search(new VectorSearchRequest("test-model", vector(1.0d, 0.0d), 2));

        assertThat(results).extracting(VectorSearchResult::paperId).containsExactly(near.getId(), far.getId());
        assertThat(results.get(0).score()).isGreaterThan(results.get(1).score());
    }

    @Test
    void emptyVectorTableReturnsNoResults() {
        assertThat(gateway.search(new VectorSearchRequest("test-model", vector(1.0d, 0.0d), 10))).isEmpty();
    }

    private static Paper paper(String arxivId, String title) {
        return new Paper(
                arxivId,
                title,
                "A test abstract.",
                List.of("Ada Lovelace"),
                List.of("cs.CL"),
                java.time.LocalDate.of(2024, 1, 1),
                java.time.LocalDate.of(2024, 1, 1),
                null,
                null
        );
    }

    private static EmbeddedDocument document(Long paperId, String arxivId, double first, double second) {
        return new EmbeddedDocument(
                paperId,
                arxivId,
                "title-abstract",
                "hash-" + paperId,
                "test-model",
                1536,
                "test document",
                List.of("cs.CL"),
                java.time.LocalDate.of(2024, 1, 1),
                vector(first, second)
        );
    }

    private static List<Double> vector(double first, double second) {
        var values = new ArrayList<>(Collections.nCopies(1536, 0.0d));
        values.set(0, first);
        values.set(1, second);
        return values;
    }
}

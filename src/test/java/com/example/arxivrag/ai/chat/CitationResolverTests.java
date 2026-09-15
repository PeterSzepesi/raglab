package com.example.arxivrag.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.arxivrag.vector.SemanticSearchResult;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CitationResolverTests {

    @Test
    void deduplicatesEvidenceAndBuildsCanonicalServerUrl() {
        var first = result("2401.00001", 0.9d);
        var duplicate = result("2401.00001", 0.8d);

        var citations = new CitationResolver().resolveResults(List.of(first, duplicate));

        assertThat(citations).singleElement().satisfies(citation -> {
            assertThat(citation.arxivId()).isEqualTo("2401.00001");
            assertThat(citation.url()).isEqualTo("https://arxiv.org/abs/2401.00001");
            assertThat(citation.score()).isEqualTo(0.9d);
        });
    }

    private static SemanticSearchResult result(String id, double score) {
        return new SemanticSearchResult(1L, id, "Paper", "Evidence", List.of("Author"),
                List.of("cs.AI"), LocalDate.of(2024, 1, 1), score, "Evidence", "vector");
    }
}

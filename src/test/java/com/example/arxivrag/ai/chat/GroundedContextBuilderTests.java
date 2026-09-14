package com.example.arxivrag.ai.chat;

import com.example.arxivrag.vector.SemanticSearchResult;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GroundedContextBuilderTests {

    @Test
    void retainsHighestRankedUniqueEvidenceWithinAggregateBudget() {
        var properties = new ChatProperties();
        properties.setMaxContextCharactersPerPaper(120);
        properties.setMaxContextCharactersTotal(550);
        var builder = new GroundedContextBuilder(properties, new ObjectMapper());
        var lower = result(2L, "2401.00002", 0.4, "b".repeat(300));
        var higher = result(1L, "2401.00001", 0.9, "a".repeat(300));

        var context = builder.build(new ChatRetrieval(List.of(lower, higher, higher), List.of("semantic_search")));

        assertThat(context.evidenceJson()).hasSizeLessThanOrEqualTo(550);
        assertThat(context.citations()).extracting(ChatCitation::arxivId)
                .startsWith("2401.00001").doesNotHaveDuplicates();
        assertThat(context.toolsUsed()).containsExactly("semantic_search");
    }

    private static SemanticSearchResult result(long id, String arxivId, double score, String abstractText) {
        return new SemanticSearchResult(id, arxivId, "Title " + id, abstractText, List.of("Author"),
                List.of("cs.AI"), LocalDate.of(2024, 1, 1), score, abstractText);
    }
}

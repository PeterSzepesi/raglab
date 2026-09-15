package com.example.arxivrag.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.arxivrag.vector.SemanticSearchResult;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

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

    @Test
    void selectsAQueryRelevantSentenceAndDropsNearDuplicateTitles() {
        var properties = new ChatProperties();
        properties.setMaxContextCharactersPerPaper(110);
        properties.setMaxContextCharactersTotal(600);
        var builder = new GroundedContextBuilder(properties, new ObjectMapper());
        var relevant = new SemanticSearchResult(1L, "2401.00001", "Neural Retrieval for RAG",
                "Unrelated opening material fills the introduction. Neural retrieval improves evidence selection. "
                        + "A final sentence discusses deployment.",
                List.of("Author"), List.of("cs.AI"), LocalDate.of(2024, 1, 1), 0.9, "text");
        var duplicate = new SemanticSearchResult(2L, "2401.00002", "Neural Retrieval for RAG Systems",
                "Duplicate evidence.", List.of("Author"), List.of("cs.AI"),
                LocalDate.of(2024, 1, 2), 0.8, "text");

        var context = builder.build(new ChatRetrieval(List.of(relevant, duplicate), List.of("hybrid_search")),
                "How does neural retrieval improve evidence selection?");

        assertThat(context.evidenceJson()).contains("Neural retrieval improves evidence selection");
        assertThat(context.citations()).extracting(ChatCitation::arxivId).containsExactly("2401.00001");
    }

    private static SemanticSearchResult result(long id, String arxivId, double score, String abstractText) {
        return new SemanticSearchResult(id, arxivId, "Title " + id, abstractText, List.of("Author"),
                List.of("cs.AI"), LocalDate.of(2024, 1, 1), score, abstractText);
    }
}

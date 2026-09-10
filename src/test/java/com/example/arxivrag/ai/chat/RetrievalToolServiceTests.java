package com.example.arxivrag.ai.chat;

import com.example.arxivrag.paper.PaperRepository;
import com.example.arxivrag.vector.SemanticSearchService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RetrievalToolServiceTests {

    private final RetrievalToolService service = new RetrievalToolService(
            mock(SemanticSearchService.class),
            mock(PaperRepository.class)
    );

    @Test
    void exposesOnlyNarrowRetrievalTools() {
        assertThat(service.toolDefinitions().stream()
                .map(definition -> String.valueOf(((Map<?, ?>) definition.get("function")).get("name")))
                .toList())
                .containsExactlyInAnyOrder("semantic_search", "relational_paper_search", "paper_lookup");
    }

    @Test
    void rejectsUnknownToolsSqlAndInvalidTopK() {
        assertThatThrownBy(() -> service.execute("run_sql", Map.of("sql", "select 1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown retrieval tool");
        assertThatThrownBy(() -> service.execute("semantic_search", Map.of("query", "papers", "sql", "select 1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown tool argument: sql");
        assertThatThrownBy(() -> service.execute("semantic_search", Map.of("query", "papers", "topK", 11)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 10");
    }
}

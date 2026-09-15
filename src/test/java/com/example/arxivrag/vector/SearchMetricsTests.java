package com.example.arxivrag.vector;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchMetricsTests {

    @Test
    void recordsEmptyCountsScoresAndAuthoritativeSourcesWithBoundedTags() {
        var registry = new SimpleMeterRegistry();
        var metrics = new SearchMetrics(registry);
        var hit = new SemanticSearchResult(1L, "2401.00001", "Title", "Abstract", List.of(), List.of(),
                LocalDate.of(2024, 1, 1), 0.82d, "text", "both");

        metrics.results("hybrid", List.of(hit));
        metrics.results("semantic", List.of());

        assertThat(registry.get("rag.search.result.count").tag("mode", "hybrid").summary().totalAmount())
                .isEqualTo(1d);
        assertThat(registry.get("rag.search.result.score").tag("mode", "hybrid").summary().max())
                .isEqualTo(0.82d);
        assertThat(registry.get("rag.search.result.source").tag("mode", "hybrid").tag("source", "both")
                .counter().count()).isEqualTo(1d);
        assertThat(registry.get("rag.search.empty").tag("mode", "semantic").counter().count()).isEqualTo(1d);
    }
}

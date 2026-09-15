package com.example.arxivrag.search;

import com.example.arxivrag.vector.SemanticSearchService;
import com.example.arxivrag.vector.VectorRecallDiagnostics;
import com.example.arxivrag.vector.VectorRecallOptions;
import com.example.arxivrag.vector.VectorSearchDiagnostics;
import java.time.LocalDate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping({"/api/search/diagnostics", "/api/v1/search/diagnostics"})
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "rag.features.search-enabled", havingValue = "true")
@ConditionalOnProperty(name = "chat.debug-timings", havingValue = "true")
public class VectorDiagnosticsController {

    private final SemanticSearchService search;

    public VectorDiagnosticsController(SemanticSearchService search) {
        this.search = search;
    }

    @PostMapping("/vector-plan")
    public VectorSearchDiagnostics explain(@RequestBody SearchRequest request) {
        if (request == null || request.query() == null || request.query().isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        var topK = request.topK() == null ? 10 : request.topK();
        if (topK < 1 || topK > 50) {
            throw new IllegalArgumentException("topK must be between 1 and 50");
        }
        return search.explain(request.query().trim(), topK, request.category(), request.fromDate(), request.toDate());
    }

    @PostMapping("/vector-recall")
    public VectorRecallDiagnostics recall(@RequestBody RecallRequest request) {
        if (request == null || request.query() == null || request.query().isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        var topK = request.topK() == null ? 10 : request.topK();
        if (topK < 1 || topK > 50) {
            throw new IllegalArgumentException("topK must be between 1 and 50");
        }
        if (request.fromDate() != null && request.toDate() != null
                && request.fromDate().isAfter(request.toDate())) {
            throw new IllegalArgumentException("fromDate must not be after toDate");
        }
        return search.compareRecall(request.query().trim(), topK, request.category(),
                request.fromDate(), request.toDate(),
                VectorRecallOptions.from(request.hnswEfSearch(), request.iterativeScan()));
    }

    public record RecallRequest(
            String query,
            Integer topK,
            String category,
            LocalDate fromDate,
            LocalDate toDate,
            Integer hnswEfSearch,
            String iterativeScan
    ) { }

}

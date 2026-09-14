package com.example.arxivrag.vector;

import java.util.List;

public record VectorSearchDiagnostics(
        String embeddingModel,
        long embeddingMilliseconds,
        long requestPreparationMilliseconds,
        long explainAnalyzeMilliseconds,
        List<String> plan
) {
}

package com.example.arxivrag.vector;

import java.util.List;

public interface VectorStoreGateway {

    void upsert(EmbeddedDocument document);

    default void upsertAll(List<EmbeddedDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        documents.forEach(this::upsert);
    }

    List<VectorSearchResult> search(VectorSearchRequest request);

    long countByEmbeddingModel(String embeddingModel);

    default List<String> explain(VectorSearchRequest request) {
        return List.of("EXPLAIN is not supported by the active vector store");
    }

    default VectorRecallDiagnostics compareRecall(
            VectorSearchRequest request,
            VectorRecallOptions options
    ) {
        return VectorRecallDiagnostics.unsupported(request, options,
                "Exact-versus-approximate recall diagnostics are not supported by the active vector store");
    }

    default void analyze() {
    }
}

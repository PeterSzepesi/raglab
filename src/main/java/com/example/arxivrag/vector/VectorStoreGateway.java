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
}

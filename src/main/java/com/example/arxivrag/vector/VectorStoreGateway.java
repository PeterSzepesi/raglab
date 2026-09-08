package com.example.arxivrag.vector;

import java.util.List;

public interface VectorStoreGateway {

    void upsert(EmbeddedDocument document);

    List<VectorSearchResult> search(VectorSearchRequest request);

    long countByEmbeddingModel(String embeddingModel);
}

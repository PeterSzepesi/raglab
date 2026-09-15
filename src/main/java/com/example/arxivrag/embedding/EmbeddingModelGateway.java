package com.example.arxivrag.embedding;

import java.util.List;

public interface EmbeddingModelGateway {

    String modelName();

    int dimensions();

    List<Double> embed(RetrievalDocument document);

    default List<Double> embed(RetrievalDocument document, EmbeddingWorkClass workClass) {
        return embed(document);
    }

    /**
     * Embeds a provider-sized batch. Implementations must preserve input order.
     * The default keeps the offline/test gateways backwards compatible while
     * Foundry can override it with one HTTP request for the whole batch.
     */
    default List<List<Double>> embedBatch(List<RetrievalDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException("documents must not be empty");
        }
        return documents.stream().map(this::embed).toList();
    }

    default List<List<Double>> embedBatch(List<RetrievalDocument> documents, EmbeddingWorkClass workClass) {
        return embedBatch(documents);
    }
}

package com.example.arxivrag.embedding;

import java.util.List;

public interface EmbeddingModelGateway {

    String modelName();

    int dimensions();

    List<Double> embed(RetrievalDocument document);
}

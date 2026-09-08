package com.example.arxivrag.vector;

import com.example.arxivrag.embedding.EmbeddingModelGateway;
import com.example.arxivrag.embedding.RetrievalDocumentFactory;
import com.example.arxivrag.paper.Paper;
import com.example.arxivrag.paper.PaperRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SemanticSearchService {

    public static final int DEFAULT_TOP_K = 10;

    private final EmbeddingModelGateway embeddingModel;

    private final RetrievalDocumentFactory retrievalDocumentFactory;

    private final VectorStoreGateway vectorStoreGateway;

    private final PaperRepository paperRepository;

    public SemanticSearchService(
            EmbeddingModelGateway embeddingModel,
            RetrievalDocumentFactory retrievalDocumentFactory,
            VectorStoreGateway vectorStoreGateway,
            PaperRepository paperRepository
    ) {
        this.embeddingModel = embeddingModel;
        this.retrievalDocumentFactory = retrievalDocumentFactory;
        this.vectorStoreGateway = vectorStoreGateway;
        this.paperRepository = paperRepository;
    }

    public java.util.List<SemanticSearchResult> search(String query) {
        return search(query, DEFAULT_TOP_K);
    }

    public String embeddingModelName() {
        return embeddingModel.modelName();
    }

    public java.util.List<SemanticSearchResult> search(String query, int topK) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be greater than zero");
        }

        var queryDocument = retrievalDocumentFactory.forQuery(query);
        var queryEmbedding = embeddingModel.embed(queryDocument);
        if (queryEmbedding.size() != embeddingModel.dimensions()) {
            throw new IllegalStateException("Embedding model returned " + queryEmbedding.size()
                    + " dimensions, expected " + embeddingModel.dimensions());
        }

        var vectorHits = vectorStoreGateway.search(new VectorSearchRequest(
                embeddingModel.modelName(),
                queryEmbedding,
                topK
        ));
        if (vectorHits.isEmpty()) {
            return java.util.List.of();
        }

        var papersById = paperRepository.findAllById(vectorHits.stream()
                        .map(VectorSearchResult::paperId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(Paper::getId, Function.identity()));

        return vectorHits.stream()
                .map(hit -> toResult(hit, papersById))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static SemanticSearchResult toResult(VectorSearchResult hit, Map<Long, Paper> papersById) {
        var paper = papersById.get(hit.paperId());
        if (paper == null) {
            return null;
        }
        return new SemanticSearchResult(
                paper.getId(),
                paper.getArxivId(),
                paper.getTitle(),
                paper.getAbstractText(),
                paper.getAuthors(),
                paper.getCategories(),
                paper.getSubmittedDate(),
                hit.score(),
                hit.text()
        );
    }
}

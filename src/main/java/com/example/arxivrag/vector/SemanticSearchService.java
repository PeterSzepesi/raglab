package com.example.arxivrag.vector;

import com.example.arxivrag.embedding.EmbeddingModelGateway;
import com.example.arxivrag.embedding.RetrievalDocumentFactory;
import com.example.arxivrag.paper.Paper;
import com.example.arxivrag.paper.PaperRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.LocalDate;

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
        return search(query, topK, null, null, null);
    }

    public java.util.List<SemanticSearchResult> search(
            String query,
            int topK,
            String category,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be greater than zero");
        }
        if (category != null && category.isBlank()) {
            throw new IllegalArgumentException("category must not be blank");
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must not be after toDate");
        }

        var queryDocument = retrievalDocumentFactory.forQuery(query);
        var queryEmbedding = embeddingModel.embed(queryDocument);
        if (queryEmbedding.size() != embeddingModel.dimensions()) {
            throw new IllegalStateException("Embedding model returned " + queryEmbedding.size()
                    + " dimensions, expected " + embeddingModel.dimensions());
        }

        var retrievalLimit = category == null && fromDate == null && toDate == null
                ? topK
                : Math.min(Math.max(topK * 10, topK), 100);
        var vectorHits = vectorStoreGateway.search(new VectorSearchRequest(
                embeddingModel.modelName(),
                queryEmbedding,
                retrievalLimit
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
                .filter(result -> matches(result, category, fromDate, toDate))
                .limit(topK)
                .toList();
    }

    private static boolean matches(
            SemanticSearchResult result,
            String category,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        return (category == null || result.categories().contains(category))
                && (fromDate == null || (result.submittedDate() != null && !result.submittedDate().isBefore(fromDate)))
                && (toDate == null || (result.submittedDate() != null && !result.submittedDate().isAfter(toDate)));
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

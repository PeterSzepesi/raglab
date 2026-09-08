package com.example.arxivrag.vector;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "vector.store.type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryVectorStoreGateway implements VectorStoreGateway {

    private final Map<String, EmbeddedDocument> documents = new ConcurrentHashMap<>();

    @Override
    public void upsert(EmbeddedDocument document) {
        documents.put(key(document.paperId(), document.chunkId(), document.embeddingModel()), document);
    }

    @Override
    public List<VectorSearchResult> search(VectorSearchRequest request) {
        return documents.values().stream()
                .filter(document -> document.embeddingModel().equals(request.embeddingModel()))
                .map(document -> new VectorSearchResult(
                        document.paperId(),
                        document.arxivId(),
                        document.chunkId(),
                        document.contentHash(),
                        document.embeddingModel(),
                        cosineSimilarity(request.embedding(), document.embedding()),
                        document.text()
                ))
                .sorted(Comparator.comparingDouble(VectorSearchResult::score).reversed())
                .limit(request.topK())
                .toList();
    }

    @Override
    public long countByEmbeddingModel(String embeddingModel) {
        return documents.values().stream()
                .filter(document -> document.embeddingModel().equals(embeddingModel))
                .count();
    }

    public void clear() {
        documents.clear();
    }

    private static String key(Long paperId, String chunkId, String embeddingModel) {
        return paperId + ":" + chunkId + ":" + embeddingModel;
    }

    private static double cosineSimilarity(List<Double> left, List<Double> right) {
        if (left.size() != right.size()) {
            throw new IllegalArgumentException("Vector dimensions do not match");
        }

        var dotProduct = 0.0d;
        var leftMagnitude = 0.0d;
        var rightMagnitude = 0.0d;
        for (var index = 0; index < left.size(); index++) {
            var leftValue = left.get(index);
            var rightValue = right.get(index);
            dotProduct += leftValue * rightValue;
            leftMagnitude += leftValue * leftValue;
            rightMagnitude += rightValue * rightValue;
        }

        if (leftMagnitude == 0.0d || rightMagnitude == 0.0d) {
            return 0.0d;
        }
        return dotProduct / (Math.sqrt(leftMagnitude) * Math.sqrt(rightMagnitude));
    }
}

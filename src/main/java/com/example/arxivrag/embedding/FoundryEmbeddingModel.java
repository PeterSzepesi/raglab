package com.example.arxivrag.embedding;

import com.example.arxivrag.ai.foundry.FoundryClient;
import com.example.arxivrag.ai.foundry.FoundryProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("foundry")
public class FoundryEmbeddingModel implements EmbeddingModelGateway {

    private final FoundryClient foundryClient;

    private final FoundryProperties foundryProperties;

    private final EmbeddingIngestionProperties embeddingProperties;

    public FoundryEmbeddingModel(
            FoundryClient foundryClient,
            FoundryProperties foundryProperties,
            EmbeddingIngestionProperties embeddingProperties
    ) {
        this.foundryClient = foundryClient;
        this.foundryProperties = foundryProperties;
        this.embeddingProperties = embeddingProperties;
        if (!foundryProperties.getEmbeddingDeployment().equals(embeddingProperties.getModelName())) {
            throw new IllegalStateException("foundry.embedding-deployment must match embedding.ingestion.model-name");
        }
    }

    @Override
    public String modelName() {
        return embeddingProperties.getModelName();
    }

    @Override
    public int dimensions() {
        return embeddingProperties.getDimensions();
    }

    @Override
    public List<Double> embed(RetrievalDocument document) {
        var embedding = foundryClient.embed(
                foundryProperties.getEmbeddingDeployment(),
                document.text(),
                dimensions()
        );
        if (embedding.size() != dimensions()) {
            throw new IllegalStateException("Foundry deployment " + modelName() + " returned " + embedding.size()
                    + " dimensions, expected " + dimensions());
        }
        return embedding;
    }

    @Override
    public List<List<Double>> embedBatch(List<RetrievalDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException("documents must not be empty");
        }
        var embeddings = foundryClient.embed(
                foundryProperties.getEmbeddingDeployment(),
                documents.stream().map(RetrievalDocument::text).toList(),
                dimensions()
        );
        if (embeddings.size() != documents.size()) {
            throw new IllegalStateException("Foundry returned " + embeddings.size()
                    + " embeddings for " + documents.size() + " input documents");
        }
        for (var embedding : embeddings) {
            if (embedding.size() != dimensions()) {
                throw new IllegalStateException("Foundry deployment " + modelName() + " returned " + embedding.size()
                        + " dimensions, expected " + dimensions());
            }
        }
        return embeddings;
    }

}

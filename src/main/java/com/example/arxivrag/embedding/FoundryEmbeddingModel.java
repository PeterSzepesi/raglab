package com.example.arxivrag.embedding;

import com.example.arxivrag.ai.foundry.FoundryClient;
import com.example.arxivrag.ai.foundry.FoundryEmbeddingClient;
import com.example.arxivrag.ai.foundry.FoundryOperationalState;
import com.example.arxivrag.ai.foundry.FoundryProperties;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("foundry")
public class FoundryEmbeddingModel implements EmbeddingModelGateway {

    private final FoundryEmbeddingClient foundryClient;

    private final FoundryProperties foundryProperties;

    private final EmbeddingIngestionProperties embeddingProperties;
    private final FoundryOperationalState operationalState;
    private final com.example.arxivrag.ai.foundry.ProviderCircuitBreaker circuitBreaker;

    public FoundryEmbeddingModel(
            FoundryEmbeddingClient foundryClient,
            FoundryProperties foundryProperties,
            EmbeddingIngestionProperties embeddingProperties,
            FoundryOperationalState operationalState,
            com.example.arxivrag.ai.foundry.ProviderCircuitBreaker circuitBreaker
    ) {
        this.foundryClient = foundryClient;
        this.foundryProperties = foundryProperties;
        this.embeddingProperties = embeddingProperties;
        this.operationalState = operationalState;
        this.circuitBreaker = circuitBreaker;
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
        return embed(document, EmbeddingWorkClass.INTERACTIVE);
    }

    @Override
    public List<Double> embed(RetrievalDocument document, EmbeddingWorkClass workClass) {
        var embedding = embedOne(document.text());
        if (embedding.size() != dimensions()) {
            throw new IllegalStateException("Foundry deployment " + modelName() + " returned " + embedding.size()
                    + " dimensions, expected " + dimensions());
        }
        return embedding;
    }

    @Override
    public List<List<Double>> embedBatch(List<RetrievalDocument> documents) {
        return embedBatch(documents, EmbeddingWorkClass.BULK);
    }

    @Override
    public List<List<Double>> embedBatch(List<RetrievalDocument> documents, EmbeddingWorkClass workClass) {
        if (documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException("documents must not be empty");
        }
        List<List<Double>> embeddings;
        var operation = workClass == EmbeddingWorkClass.INTERACTIVE
                ? com.example.arxivrag.ai.foundry.FoundryOperation.QUERY_EMBEDDING
                : com.example.arxivrag.ai.foundry.FoundryOperation.INGESTION_EMBEDDING;
        try (var permit = circuitBreaker.acquire(foundryProperties.getEmbeddingDeployment(), operation)) {
            embeddings = workClass == EmbeddingWorkClass.INTERACTIVE && documents.size() == 1
                    ? List.of(foundryClient.embed(foundryProperties.getEmbeddingDeployment(),
                            documents.getFirst().text(), dimensions()))
                    : foundryClient.embedBatch(foundryProperties.getEmbeddingDeployment(),
                            documents.stream().map(RetrievalDocument::text).toList(), dimensions());
            operationalState.success("embedding", null);
            permit.success();
        } catch (FoundryClient.FoundryProviderException exception) {
            operationalState.failure("embedding", exception.statusCode(), exception.requestId());
            throw exception;
        } catch (com.example.arxivrag.ai.foundry.ProviderCircuitBreaker.CircuitOpenException exception) {
            operationalState.failure("embedding", 503, null);
            throw new FoundryClient.FoundryProviderException(exception.getMessage(), 503, null, null);
        }
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

    private List<Double> embedOne(String text) {
        try (var permit = circuitBreaker.acquire(foundryProperties.getEmbeddingDeployment(),
                com.example.arxivrag.ai.foundry.FoundryOperation.QUERY_EMBEDDING)) {
            var result = foundryClient.embed(foundryProperties.getEmbeddingDeployment(), text, dimensions());
            operationalState.success("embedding", null);
            permit.success();
            return result;
        } catch (FoundryClient.FoundryProviderException exception) {
            operationalState.failure("embedding", exception.statusCode(), exception.requestId());
            throw exception;
        } catch (com.example.arxivrag.ai.foundry.ProviderCircuitBreaker.CircuitOpenException exception) {
            operationalState.failure("embedding", 503, null);
            throw new FoundryClient.FoundryProviderException(exception.getMessage(), 503, null, null);
        }
    }

}

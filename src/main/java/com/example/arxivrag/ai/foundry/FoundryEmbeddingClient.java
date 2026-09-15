package com.example.arxivrag.ai.foundry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Owns the Microsoft AI Foundry embeddings protocol and response validation. */
@Component
@Profile("foundry")
public class FoundryEmbeddingClient {

    private final ObjectMapper objectMapper;
    private final FoundryQuotaLimiterRegistry quotaLimiters;
    private final FoundryRequestExecutor requests;

    public FoundryEmbeddingClient(
            ObjectMapper objectMapper,
            FoundryQuotaLimiterRegistry quotaLimiters,
            FoundryRequestExecutor requests
    ) {
        this.objectMapper = objectMapper;
        this.quotaLimiters = quotaLimiters;
        this.requests = requests;
    }

    public List<Double> embed(String deployment, String input, int dimensions) {
        return embedInternal(deployment, List.of(input), dimensions, FoundryOperation.QUERY_EMBEDDING).getFirst();
    }

    public List<List<Double>> embedBatch(String deployment, List<String> inputs, int dimensions) {
        return embedInternal(deployment, inputs, dimensions, FoundryOperation.INGESTION_EMBEDDING);
    }

    private List<List<Double>> embedInternal(
            String deployment,
            List<String> inputs,
            int dimensions,
            FoundryOperation operation
    ) {
        if (deployment == null || deployment.isBlank()) {
            throw new FoundryClient.FoundryProviderException("Foundry embedding deployment must not be blank");
        }
        if (inputs == null || inputs.isEmpty()) {
            throw new FoundryClient.FoundryProviderException("Foundry embedding requires at least one input");
        }
        if (inputs.stream().anyMatch(Objects::isNull)) {
            throw new FoundryClient.FoundryProviderException("Foundry embedding inputs must not contain null");
        }
        if (dimensions <= 0) {
            throw new FoundryClient.FoundryProviderException("Foundry embedding dimensions must be positive");
        }

        var body = FoundryProtocolSupport.writeJson(objectMapper, Map.of(
                "model", deployment,
                "input", inputs,
                "dimensions", dimensions
        ));
        quotaLimiters.acquire(deployment, operation,
                inputs.stream().mapToInt(FoundryProtocolSupport::estimateTokens).sum());
        var response = requests.post("/openai/deployments/" + FoundryProtocolSupport.encode(deployment)
                + "/embeddings", body, operation);
        var data = response.body().path("data");
        if (!data.isArray() || data.size() != inputs.size()) {
            throw new FoundryClient.FoundryProviderException("Foundry embedding response contained "
                    + (data.isArray() ? data.size() : 0) + " vectors for " + inputs.size() + " inputs");
        }

        var embeddings = new ArrayList<List<Double>>(Collections.nCopies(inputs.size(), null));
        var seenIndexes = new HashSet<Integer>();
        for (var position = 0; position < data.size(); position++) {
            var item = data.get(position);
            var index = item.path("index").isInt() ? item.path("index").intValue() : position;
            if (index < 0 || index >= inputs.size() || !seenIndexes.add(index)) {
                throw new FoundryClient.FoundryProviderException(
                        "Foundry embedding response contained invalid vector indexes");
            }
            var embedding = item.path("embedding");
            if (!embedding.isArray()) {
                throw new FoundryClient.FoundryProviderException(
                        "Foundry embedding response did not contain an embedding array");
            }
            var values = new ArrayList<Double>(embedding.size());
            for (var value : embedding) {
                if (!value.isNumber() || !Double.isFinite(value.doubleValue())) {
                    throw new FoundryClient.FoundryProviderException(
                            "Foundry returned a non-finite embedding value");
                }
                values.add(value.doubleValue());
            }
            if (values.isEmpty()) {
                throw new FoundryClient.FoundryProviderException("Foundry returned an empty embedding");
            }
            embeddings.set(index, List.copyOf(values));
        }
        if (embeddings.stream().anyMatch(Objects::isNull)) {
            throw new FoundryClient.FoundryProviderException(
                    "Foundry embedding response did not contain every input index");
        }
        return List.copyOf(embeddings);
    }
}

package com.example.arxivrag.embedding;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

@Component
@Profile("!foundry")
public class OfflineDeterministicEmbeddingModel implements EmbeddingModelGateway {

    private final EmbeddingIngestionProperties properties;

    public OfflineDeterministicEmbeddingModel(EmbeddingIngestionProperties properties) {
        this.properties = properties;
    }

    @Override
    public String modelName() {
        return properties.getModelName();
    }

    @Override
    public int dimensions() {
        return properties.getDimensions();
    }

    @Override
    public List<Double> embed(RetrievalDocument document) {
        if (dimensions() <= 0) {
            throw new IllegalStateException("embedding.ingestion.dimensions must be greater than zero");
        }

        var values = new ArrayList<Double>(dimensions());
        var magnitudeSquared = 0.0d;
        for (var index = 0; index < dimensions(); index++) {
            var value = componentValue(document.contentHash(), index);
            values.add(value);
            magnitudeSquared += value * value;
        }

        var magnitude = Math.sqrt(magnitudeSquared);
        if (magnitude == 0.0d) {
            return values;
        }

        return values.stream()
                .map(value -> value / magnitude)
                .toList();
    }

    private static double componentValue(String contentHash, int index) {
        var digest = sha256(contentHash + ":" + index);
        var unsigned = Byte.toUnsignedInt(digest[0]);
        return (unsigned / 127.5d) - 1.0d;
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available", exception);
        }
    }
}

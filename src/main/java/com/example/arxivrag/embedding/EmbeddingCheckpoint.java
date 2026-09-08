package com.example.arxivrag.embedding;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "embedding_checkpoints")
public class EmbeddingCheckpoint {

    @EmbeddedId
    private EmbeddingCheckpointId id;

    @Column(name = "embedding_dimension", nullable = false)
    private int embeddingDimension;

    @CreationTimestamp
    @Column(name = "embedded_at", nullable = false, updatable = false)
    private Instant embeddedAt;

    protected EmbeddingCheckpoint() {
    }

    public EmbeddingCheckpoint(EmbeddingCheckpointId id, int embeddingDimension) {
        this.id = id;
        this.embeddingDimension = embeddingDimension;
    }

    public EmbeddingCheckpointId getId() {
        return id;
    }

    public int getEmbeddingDimension() {
        return embeddingDimension;
    }

    public Instant getEmbeddedAt() {
        return embeddedAt;
    }
}

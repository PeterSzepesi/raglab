package com.example.arxivrag.embedding;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class EmbeddingCheckpointId implements Serializable {

    @Column(name = "paper_id", nullable = false)
    private Long paperId;

    @Column(name = "chunk_id", nullable = false, length = 100)
    private String chunkId;

    @Column(name = "embedding_model", nullable = false, length = 160)
    private String embeddingModel;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "document_schema_version", nullable = false, length = 64)
    private String documentSchemaVersion;

    protected EmbeddingCheckpointId() {
    }

    public EmbeddingCheckpointId(Long paperId, String chunkId, String embeddingModel, String contentHash) {
        this(paperId, chunkId, embeddingModel, contentHash, RetrievalDocumentFactory.DOCUMENT_SCHEMA_VERSION);
    }

    public EmbeddingCheckpointId(Long paperId, String chunkId, String embeddingModel, String contentHash,
                                 String documentSchemaVersion) {
        this.paperId = paperId;
        this.chunkId = chunkId;
        this.embeddingModel = embeddingModel;
        this.contentHash = contentHash;
        this.documentSchemaVersion = documentSchemaVersion;
    }

    public Long getPaperId() {
        return paperId;
    }

    public String getChunkId() {
        return chunkId;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getDocumentSchemaVersion() {
        return documentSchemaVersion;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof EmbeddingCheckpointId that)) {
            return false;
        }
        return Objects.equals(paperId, that.paperId)
                && Objects.equals(chunkId, that.chunkId)
                && Objects.equals(embeddingModel, that.embeddingModel)
                && Objects.equals(contentHash, that.contentHash)
                && Objects.equals(documentSchemaVersion, that.documentSchemaVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(paperId, chunkId, embeddingModel, contentHash, documentSchemaVersion);
    }
}

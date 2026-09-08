CREATE TABLE embedding_checkpoints (
    paper_id BIGINT NOT NULL,
    chunk_id VARCHAR(100) NOT NULL,
    embedding_model VARCHAR(160) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    embedding_dimension INTEGER NOT NULL,
    embedded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_embedding_checkpoints PRIMARY KEY (paper_id, chunk_id, embedding_model, content_hash),
    CONSTRAINT fk_embedding_checkpoints_paper FOREIGN KEY (paper_id) REFERENCES papers (id) ON DELETE CASCADE
);

CREATE INDEX idx_embedding_checkpoints_model ON embedding_checkpoints (embedding_model);
CREATE INDEX idx_embedding_checkpoints_paper ON embedding_checkpoints (paper_id);

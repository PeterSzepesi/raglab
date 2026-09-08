CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE paper_embeddings (
    paper_id BIGINT NOT NULL,
    arxiv_id VARCHAR(64) NOT NULL,
    chunk_id VARCHAR(100) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    embedding_model VARCHAR(160) NOT NULL,
    embedding_dimension INTEGER NOT NULL,
    text TEXT NOT NULL,
    categories VARCHAR(50) ARRAY NOT NULL,
    submitted_date DATE,
    embedding vector(1536) NOT NULL,
    embedded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_paper_embeddings PRIMARY KEY (paper_id, chunk_id, embedding_model),
    CONSTRAINT fk_paper_embeddings_paper FOREIGN KEY (paper_id) REFERENCES papers (id) ON DELETE CASCADE
);

CREATE INDEX idx_paper_embeddings_model ON paper_embeddings (embedding_model);
CREATE INDEX idx_paper_embeddings_arxiv_id ON paper_embeddings (arxiv_id);
CREATE INDEX idx_paper_embeddings_embedding_cosine ON paper_embeddings USING hnsw (embedding vector_cosine_ops);

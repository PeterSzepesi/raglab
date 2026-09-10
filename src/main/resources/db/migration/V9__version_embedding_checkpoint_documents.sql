ALTER TABLE embedding_checkpoints
    ADD COLUMN document_schema_version VARCHAR(64) NOT NULL DEFAULT 'title-abstract-v1';

ALTER TABLE embedding_checkpoints
    DROP CONSTRAINT pk_embedding_checkpoints;

ALTER TABLE embedding_checkpoints
    ADD CONSTRAINT pk_embedding_checkpoints
        PRIMARY KEY (paper_id, chunk_id, embedding_model, content_hash, document_schema_version);

CREATE TABLE embedding_jobs (
    job_id VARCHAR(36) PRIMARY KEY,
    status VARCHAR(16) NOT NULL,
    embedding_model VARCHAR(160) NOT NULL,
    embedding_dimension INTEGER NOT NULL,
    document_schema_version VARCHAR(64) NOT NULL,
    total_papers BIGINT NOT NULL DEFAULT 0,
    papers_scanned BIGINT NOT NULL DEFAULT 0,
    documents_prepared BIGINT NOT NULL DEFAULT 0,
    embedded BIGINT NOT NULL DEFAULT 0,
    skipped BIGINT NOT NULL DEFAULT 0,
    failures BIGINT NOT NULL DEFAULT 0,
    provider_requests BIGINT NOT NULL DEFAULT 0,
    last_paper_id BIGINT,
    last_error TEXT,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_embedding_jobs_started_at ON embedding_jobs (started_at DESC);

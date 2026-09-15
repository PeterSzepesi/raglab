ALTER TABLE embedding_jobs ADD COLUMN max_papers INTEGER;
ALTER TABLE embedding_jobs ADD COLUMN from_paper_id BIGINT;
ALTER TABLE embedding_jobs ADD COLUMN to_paper_id BIGINT;
ALTER TABLE embedding_jobs ADD COLUMN category VARCHAR(64);
ALTER TABLE embedding_jobs ADD COLUMN from_date DATE;
ALTER TABLE embedding_jobs ADD COLUMN to_date DATE;
ALTER TABLE embedding_jobs ADD COLUMN dry_run BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE embedding_jobs ADD COLUMN cancel_requested BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE embedding_jobs ADD COLUMN owner_id VARCHAR(64);
ALTER TABLE embedding_jobs ADD COLUMN lease_expires_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_embedding_jobs_status_lease
    ON embedding_jobs (status, lease_expires_at);

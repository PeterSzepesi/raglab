ALTER TABLE import_jobs ADD COLUMN owner_id VARCHAR(64);
ALTER TABLE import_jobs ADD COLUMN lease_expires_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_import_jobs_status_lease
    ON import_jobs (status, lease_expires_at);

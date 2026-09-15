-- New durable states such as WAITING_FOR_PROVIDER_CAPACITY are intentionally descriptive.
ALTER TABLE embedding_jobs ALTER COLUMN status TYPE VARCHAR(40);

CREATE EXTENSION IF NOT EXISTS pg_trgm;

ALTER TABLE papers
    ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
        to_tsvector('english'::regconfig, coalesce(title, '') || ' ' || coalesce(abstract_text, ''))
    ) STORED;

CREATE INDEX idx_papers_search_vector ON papers USING GIN (search_vector);
CREATE INDEX idx_papers_title_trgm ON papers USING GIN (title gin_trgm_ops);

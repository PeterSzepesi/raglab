CREATE INDEX idx_papers_categories ON papers USING GIN (categories);

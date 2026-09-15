CREATE TABLE corpus_state (
    singleton_id INTEGER PRIMARY KEY,
    generation BIGINT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT corpus_state_singleton CHECK (singleton_id = 1),
    CONSTRAINT corpus_state_generation_nonnegative CHECK (generation >= 0)
);

INSERT INTO corpus_state (singleton_id, generation) VALUES (1, 0);

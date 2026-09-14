# pgvector query-plan baseline

Captured on 2026-09-11 against the local PostgreSQL 18/pgvector container after the Foundry embedding run.

## Representative query

The measured query requested the ten nearest rows for `text-embedding-3-large` using cosine distance and an existing stored vector as the query vector. This isolates PostgreSQL/HNSW behavior; it does not include the remote query-embedding call or JPA paper hydration.

## Result

- PostgreSQL used `idx_paper_embeddings_embedding_cosine` through an index scan.
- Cold-ish run: 116.133 ms execution, 430 shared-buffer hits and 775 reads.
- Immediate warm run: 2.260 ms execution, 1,204 shared-buffer hits and no reads.
- Warm planning time: 0.738 ms.
- The planner estimated approximately 16,995 rows for the active embedding model.

This confirms the intended HNSW index is eligible and warm local vector retrieval is far below the 150 ms target. The large cold/warm difference means benchmark runs must report cold and warm results separately. Category/date-filter recall and `hnsw.ef_search` still need measurement on representative filtered questions before any tuning; no speculative index or HNSW parameter change was made.

For reproducible application-level plans, enable `chat.debug-timings` and use `POST /api/search/diagnostics/vector-plan` as documented in the root README.

# Embedding and RAG Improvement Plan

## Purpose

This plan starts from the currently working end-to-end path and describes how to make it safer, faster, easier to operate, and more useful without losing the project's learning value.

The immediate priority is to finish the current full embedding run. No schema reset or architectural rewrite is required before that run finishes. The later phases are deliberately ordered so that performance changes are measured against a known-good baseline rather than guessed at.

## Current baseline

The application currently has this executable path:

```text
arXiv JSONL
    -> streaming importer
    -> PostgreSQL papers
    -> title + abstract retrieval document
    -> Microsoft AI Foundry text-embedding-3-large
    -> local PostgreSQL/pgvector
    -> semantic search
    -> local retrieval tools
    -> Microsoft AI Foundry gpt-5.6-luna
    -> cited chat answer
```

The model plane is remote, but the application, canonical paper data, vectors, retrieval tools, and UI remain local. The Foundry model never receives PostgreSQL credentials and never connects directly to the local database.

The important current implementation facts are:

- `EmbeddingIngestionService` pages through every row in `papers`.
- The default Foundry profile uses `text-embedding-3-large` and 1536 dimensions.
- One paper currently produces one retrieval document with the title and abstract.
- `FoundryEmbeddingModel` currently sends one HTTP request for each document.
- `embedding.ingestion.batch-size` controls the database page/transaction size; it is not an API batch size.
- The provider call happens inside the database transaction for that page.
- Vectors and checkpoints are written one document at a time.
- Checkpoints make reruns safe for unchanged documents, but the CLI prints only its final summary.
- The pgvector table already has an HNSW cosine index.
- Search currently retrieves vector candidates and applies some metadata filters in application code.

The database state observed during development contained substantially more papers than the latest small import. Therefore, a command intended to test 25 newly imported records can still scan and embed the entire existing corpus.

## Phase 0 — Finish the current full embedding run

This is the operational step to perform before changing the implementation.

### 0.1 Do not reset the database

The checkpoint identity includes the paper, chunk, embedding model, and content hash. If the process is interrupted, rerunning the command should skip successful unchanged work and continue with the remaining papers.

Do not delete `papers`, `paper_embeddings`, or `embedding_checkpoints` merely because the terminal was silent. The current design is intended to resume.

### 0.2 Run only one embedding worker

Start PostgreSQL and run one worker process:

```powershell
docker compose up -d postgres

java -jar target\arxiv-rag-0.0.1-SNAPSHOT.jar `
  --spring.profiles.active=foundry `
  --foundry.request-timeout=30s `
  embed-papers
```

Do not start a second embedding command against the same database. The current code has no job-level lock, so two workers could duplicate provider calls and compete on checkpoints.

### 0.3 Record the baseline counts

Before and after a run, record these values:

```sql
select count(*) as papers from papers;

select count(distinct paper_id) as embedded_papers
from paper_embeddings
where embedding_model = 'text-embedding-3-large';

select count(*) as embedding_rows
from paper_embeddings
where embedding_model = 'text-embedding-3-large';

select count(distinct paper_id) as checkpointed_papers
from embedding_checkpoints
where embedding_model = 'text-embedding-3-large';
```

For the current one-paper/one-chunk design, `embedded_papers`, `embedding_rows`, and `checkpointed_papers` should converge to the number of eligible papers. A mismatch needs investigation before trusting search coverage.

### 0.4 Acceptance criteria

- The process exits normally with an embedding summary.
- Every eligible paper has a 1536-dimensional Foundry vector.
- Re-running the command reports unchanged work as skipped.
- No API key appears in the repository, logs, command history, or browser responses.
- The baseline duration, number of provider calls, and final row counts are recorded for comparison with later phases.

## Phase 1 — Make ingestion observable and operationally safe

The current worker is functional but difficult to distinguish from a hung process. This phase should come first after the current run.

### 1.1 Add progress reporting

Emit structured progress at a configurable interval, for example every 10 or 25 documents:

- papers scanned;
- documents skipped from checkpoint;
- provider requests started/completed/failed;
- vectors written;
- current rate and average latency;
- estimated remaining work;
- current batch and paper identifier, with no abstract contents or secrets.

Use normal application logging rather than `System.out`. Keep the final CLI summary as a stable machine-readable result, and add an optional JSON output mode for scripts.

### 1.2 Add a real ingestion job status

Introduce an `embedding_jobs` table or equivalent job state containing:

- job ID and status: `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`;
- model, deployment, dimension, and document schema version;
- started, updated, and finished timestamps;
- scanned, skipped, embedded, failed, and retried counts;
- last successful paper ID and last error summary.

Expose a safe read-only status endpoint. The existing `/api/embeddings/status` can remain as a coverage endpoint, while a job endpoint reports active progress.

### 1.3 Make the worker a worker

The current one-shot command starts the full web server before running the embedding runner. Change CLI execution to use a non-web Spring application context, for example with `spring.main.web-application-type=none`, so an ingestion process does not bind port 8080 or initialize unnecessary HTTP infrastructure.

Keep the same Spring services and profiles; only the command execution mode changes.

### 1.4 Add operational controls

Add bounded options such as:

- `--embedding.ingestion.max-papers` for a controlled smoke run;
- `--embedding.ingestion.from-paper-id` and `to-paper-id` for a deterministic slice;
- category/date filters for a corpus slice;
- `--embedding.ingestion.retry-failed`;
- dry-run mode showing eligible, checkpointed, and pending counts;
- graceful cancellation after the current provider request/batch.

This prevents a 25-paper test from accidentally starting a 17,000-paper job.

### 1.5 Add a single-worker lock

Use a PostgreSQL advisory lock or a job-row lease. A lease should have an expiry and heartbeat so a crashed process does not permanently block future work. The lock must be acquired before remote calls begin.

### 1.6 Acceptance criteria

- A developer can see progress within a few seconds.
- A test run can be limited to a known number of papers without modifying production data.
- A second worker is rejected cleanly.
- A cancelled or crashed run can resume without re-embedding completed work.
- The command does not start Tomcat unless explicitly requested.

## Phase 2 — Increase Foundry embedding throughput safely

The largest performance opportunity is the current one-request-per-paper design.

### 2.1 Add batch embedding to the provider contract

Change the application boundary from conceptually:

```java
List<Double> embed(RetrievalDocument document);
```

to a batch-capable contract such as:

```java
List<EmbeddedVector> embed(List<RetrievalDocument> documents);
```

The response must preserve input order or return an explicit input index. Validate that the number of vectors equals the number of input documents and that every vector has the configured dimension.

Keep a single-document convenience method implemented through the batch method so query embedding and small tests remain simple.

### 2.2 Use adaptive batches, not one huge request

The Microsoft Foundry embeddings API accepts an array of inputs, but each input has a token limit and the request has a total token limit. Batch construction should consider both:

- maximum number of documents;
- estimated total tokens;
- request body size;
- configured quota headroom.

Start conservatively with 32–128 documents per request, measure actual token usage, and adjust. Do not assume `embedding.ingestion.batch-size` is an API batch size; split the database page and provider batch settings into separate properties.

### 2.3 Respect quota and retry semantics

The current Foundry quota is 150,000 TPM and 150 RPM. Add a bounded rate limiter based on both requests per minute and estimated tokens per minute. Add retries only for transient failures such as timeouts, connection resets, 408, 429, and selected 5xx responses.

Retry behavior should:

- honor `Retry-After` when returned;
- use exponential backoff with jitter;
- cap the number of attempts;
- avoid retrying authentication, invalid request, dimension, or deployment errors;
- record the provider request ID and error category;
- preserve the batch for retry without marking it checkpointed.

Do not add unconstrained parallelism. After batching is implemented, test a small bounded concurrency level, such as 2–8 in-flight requests, behind the same quota limiter.

### 2.4 Revisit the provider client boundary

Keep the application-owned `EmbeddingModelGateway`, but make the transport implementation replaceable. The current Java `HttpClient` adapter is adequate for the first integration; later evaluate Spring's `RestClient` or Spring AI's embedding abstraction if it provides the required batching, metrics, timeout, and error behavior without hiding important provider details.

Make the route style explicit and tested. The current deployment-based route works for the supplied resource. The current Foundry reference also documents the `/openai/v1/embeddings` route, so support should be selected by configuration rather than silently trying multiple routes after a 404.

### 2.5 Acceptance criteria

- A batch request creates multiple vectors in one provider call.
- Output ordering is correct and mismatched response sizes fail safely.
- 429 and transient failures resume without corrupting checkpoints.
- Throughput is measured in documents/minute and tokens/minute.
- The full corpus run is materially faster than the one-request-per-paper baseline while remaining within quota.

## Phase 3 — Shorten database transactions and improve persistence

The current implementation calls Foundry while holding a database transaction open. That is safe for a small single worker but inefficient and fragile for a long-running remote operation.

### 3.1 Move provider calls outside transactions

Use this lifecycle:

```text
read a page
  -> identify checkpointed documents
  -> call Foundry for pending documents
  -> open a short transaction
  -> batch upsert vectors and checkpoints
  -> commit
```

The transaction should contain only local reads/writes. If a provider call fails, the database connection is not held while waiting and the completed prior batches remain committed.

For multiple workers, combine this with a claim/lease mechanism so two workers do not select the same pending documents.

### 3.2 Batch database writes

Replace one `JdbcTemplate.update` per vector with a batch operation or a PostgreSQL-specific bulk path. Preserve the current idempotent upsert semantics. For a large initial rebuild, benchmark staging plus `COPY` followed by a merge; do not introduce that complexity until measurements show it is worthwhile.

### 3.3 Make checkpoint identity complete

The current checkpoint key includes paper, chunk, model, and content hash. Add the values that affect vector meaning:

- embedding dimension;
- retrieval-document schema/formatter version;
- provider model revision or deployment revision where available.

Otherwise, changing the document formatter or changing the effective model behind a deployment alias can incorrectly reuse an old vector.

### 3.4 Define update and cleanup behavior

When a paper changes, the new content hash causes re-embedding, but old checkpoint rows can remain. Define one of these policies:

1. retain historical checkpoints for audit and periodically remove superseded rows; or
2. keep only the current checkpoint per paper/chunk/model and remove old state after the new vector is committed.

The vector table currently has one row per paper/chunk/model, so the new vector replaces the old one. The checkpoint policy should match that storage model.

### 3.5 Acceptance criteria

- Database connections are not held during remote calls.
- A failed provider batch does not roll back unrelated completed batches.
- One database transaction writes a complete provider batch atomically.
- Changes to document format, dimension, or model revision force intentional re-embedding.
- Restarting after failure does not create duplicate current vector rows.

## Phase 4 — Tune pgvector and metadata retrieval

The current HNSW index is a good first choice, but the query path should be measured after the full corpus is present.

### 4.1 Establish exact-search and indexed baselines

For representative queries, record:

- `EXPLAIN (ANALYZE, BUFFERS)` for exact search;
- `EXPLAIN (ANALYZE, BUFFERS)` with HNSW;
- p50/p95 latency;
- rows scanned and returned;
- recall against exact search.

Do not optimize only for latency. A faster approximate query with poor recall is a regression for a RAG system.

### 4.2 Make model filtering index-aware

The current table stores the model name in each row and filters it in the SQL query, while the HNSW index is global. If multiple embedding models are stored together, benchmark one of these designs:

- one table/index per embedding space;
- a model-specific partial index;
- partitioning by embedding model/dimension;
- one active model table and historical model tables.

The choice should follow measured corpus size and whether model comparison is an active feature.

### 4.3 Push metadata filters into retrieval

Category/date filters are currently applied after candidate retrieval in application code. Add filter-aware vector queries so the database can apply constraints before the final result set. Add ordinary indexes for selective metadata filters and use pgvector iterative scans when approximate indexes return too few filtered candidates.

The search contract should carry filters from the API/tool layer into `VectorSearchRequest`, not hide them in an application-level post-filter.

### 4.4 Improve relational search

The current `lower(title) like '%query%'` and abstract search is useful for a prototype but will not scale as a high-quality lexical search. Add:

- a generated or maintained `tsvector` column and GIN index for PostgreSQL full-text search;
- optional `pg_trgm` indexes for substring/fuzzy title matching;
- normalized category fields or a category join table if category analytics become important.

Use PostgreSQL full-text search for exact/lexical intent and vector search for conceptual intent.

### 4.5 Storage and maintenance

After initial bulk loading, benchmark whether `halfvec` or binary-quantized indexes are useful. Keep the original full-precision vector available for reranking if quantization is adopted. Run `ANALYZE` after large loads and monitor table/index bloat and vacuum behavior.

Do not change the current `vector(1536)` schema to `halfvec` or binary storage without a recall and memory comparison.

### 4.6 Acceptance criteria

- Exact and approximate recall are measured on a fixed query set.
- Filtered semantic search returns the requested number of valid candidates when they exist.
- Search p95 latency and database buffer usage are recorded.
- Lexical and semantic retrieval can be selected independently for testing.

## Phase 5 — Improve retrieval-document quality and chunking

The current title-plus-abstract document is appropriate for the metadata-only v1 corpus. It should remain the baseline for comparison.

### 5.1 Version the document format

Introduce a document schema version and include it in the content hash/checkpoint identity. Store enough metadata to reconstruct the exact text sent to Foundry. This makes experiments reproducible when changing labels, normalization, or field order.

### 5.2 Add full-text ingestion only as a separate corpus mode

If PDF or source ingestion is added later:

- parse documents asynchronously;
- extract sections and references;
- split by token budget, not character count alone;
- use deterministic chunk IDs;
- preserve title, section, page, and source URL metadata;
- keep overlap configurable and measure whether it improves recall;
- cite chunks, not only papers.

Do not mix full-text chunks with title/abstract vectors without a clear document type and evaluation set.

### 5.3 Handle malformed and incomplete source data

Add data-quality counters for missing abstracts, invalid dates, duplicate IDs, unusually long text, and unsupported categories. Decide whether to skip, quarantine, or import each class of record.

## Phase 6 — Improve retrieval and answer quality

Once the pgvector baseline is fast and measurable, add quality improvements in this order:

1. hybrid lexical + semantic retrieval;
2. metadata-aware query planning;
3. optional small reranker over the top 20–50 candidates;
4. duplicate/near-duplicate suppression;
5. answer context packing with a token budget;
6. citation validation and answer-evidence checks.

The remote chat model should continue to request narrow local tools. It should never receive unrestricted SQL. Tool results should include bounded snippets, identifiers, scores, and metadata only.

Add a retrieval evaluation set containing questions with known relevant papers. Track:

- Recall@K;
- MRR or nDCG;
- citation precision;
- unsupported-claim rate;
- no-evidence behavior;
- answer latency and token usage.

Do not add query rewriting or an autonomous router until direct semantic, lexical, relational, and grounded paths have independent measurements.

## Phase 7 — Improve API, UI, and developer experience

### 7.1 Background ingestion API

Move long embedding work behind a job endpoint rather than making the browser or a terminal wait for a process. Recommended operations:

- start a bounded job;
- inspect status;
- cancel a job;
- retry failed documents;
- view coverage by model/document version;
- download a sanitized job report.

Protect these operations as administrative endpoints. Do not expose the Foundry key or raw provider responses.

### 7.2 UI improvements

Add:

- live ingestion progress and ETA;
- current embedding model/dimension;
- pending/failed/retried counts;
- search mode indicator: semantic, lexical, or hybrid;
- citations linked to arXiv pages;
- expandable evidence snippets and tool trace;
- clear “no evidence” state;
- request and response latency display for development mode.

### 7.3 Observability

Add Micrometer meters and expose them through Actuator for local diagnostics:

- embedding request count;
- embedding request failures by category;
- embedding request latency;
- documents embedded/skipped;
- provider tokens and estimated cost;
- vector upsert latency;
- retrieval latency and result count;
- chat tool rounds and failures.

Keep high-cardinality values such as paper IDs out of metric tags. Put detailed IDs in structured logs instead.

## Phase 8 — Vector-store comparison and scaling

Only start this phase after pgvector has a measured ingestion/search/chat baseline.

Implement Milvus and Chroma behind the existing `VectorStoreGateway`, but keep the same:

- corpus slice;
- embedding model and dimension;
- document format;
- query set;
- top-K and filter semantics;
- recall and latency measurements.

Compare ingestion throughput, query p50/p95, recall, memory, operational complexity, metadata filtering, restart behavior, and developer setup. Do not compare stores using different models or different corpora.

## Phase 9 — Security and deployment hardening

For local development, keep the rotated Foundry key in environment variables or a local secret store. For deployed Azure workloads, prefer managed identity/keyless authentication where the selected Foundry API supports it. Store database credentials and provider credentials separately.

Additional hardening:

- do not log request headers or full provider payloads;
- restrict administrative ingestion endpoints;
- add request size and chat message limits;
- validate all tool arguments server-side;
- enforce outbound endpoint allowlists if the application is hosted;
- add TLS and authentication before exposing the UI beyond localhost;
- back up PostgreSQL before large schema/index experiments.

## Recommended implementation order

The practical order is:

1. Finish the current full Foundry embedding run.
2. Record corpus, vector, checkpoint, duration, and failure baselines.
3. Add progress reporting, max-paper controls, job status, and a worker lock.
4. Move CLI ingestion to a non-web worker context.
5. Add batch Foundry embeddings with token-aware sizing.
6. Add quota-aware retries and bounded concurrency.
7. Move remote calls outside database transactions.
8. Batch PostgreSQL vector/checkpoint writes and improve checkpoint identity.
9. Measure exact versus HNSW search and tune metadata filtering/indexing.
10. Add PostgreSQL lexical search and hybrid retrieval.
11. Add document-versioning, evaluation data, and retrieval-quality metrics.
12. Add background ingestion/UI progress and operational controls.
13. Add reranking, richer chunking, and full-text ingestion only when justified by evaluation.
14. Compare Milvus/Chroma after the pgvector baseline is stable.
15. Harden secrets, authentication, backups, and deployment.

## Definition of a clean and fast embedding pipeline

The embedding subsystem is ready for the next major RAG features when all of the following are true:

- ingestion is resumable, bounded, observable, and single-worker safe;
- Foundry requests are batched, rate-limited, retried, and measured;
- provider calls do not hold database transactions open;
- vector and checkpoint writes are idempotent and atomic per batch;
- document/model/dimension versions prevent stale-vector reuse;
- a controlled test slice can be embedded without scanning the whole corpus;
- exact-search recall is the reference for pgvector tuning;
- semantic, lexical, and relational retrieval have explicit contracts;
- the UI can show ingestion and retrieval state without exposing secrets;
- live provider calls remain isolated from normal offline tests.

## Main architectural decisions

### Keep

- Java 21 and Spring Boot;
- PostgreSQL as canonical metadata store;
- pgvector as the first vector backend;
- Microsoft AI Foundry as the remote model plane;
- the provider-neutral `EmbeddingModelGateway`, `VectorStoreGateway`, and retrieval-tool boundaries;
- deterministic offline implementations for tests;
- Flyway-owned schema migrations.

### Change

- one-document-at-a-time provider calls to batch calls;
- remote calls inside database transactions to remote calls outside short write transactions;
- silent one-shot ingestion to observable, resumable jobs;
- unbounded whole-table test behavior to explicit slices and limits;
- application-side-only metadata filtering to database-aware filtered retrieval;
- unversioned retrieval text identity to versioned document/model identity.

### Defer until measurements justify it

- Spring Batch if the simpler job service is insufficient;
- `halfvec` or binary quantization;
- Milvus and Chroma;
- full PDF ingestion and chunking;
- reranking and query rewriting;
- distributed workers and sharding.

## References

- [Microsoft Foundry embeddings REST reference](https://learn.microsoft.com/en-us/rest/api/microsoft-foundry/azureopenai/embeddings) — batch input, dimension, and token constraints.
- [pgvector official documentation](https://github.com/pgvector/pgvector) — HNSW/IVFFlat tuning, filtered iterative scans, bulk loading, half precision, and monitoring.
- [Spring Boot metrics documentation](https://docs.spring.io/spring-boot/reference/actuator/metrics.html) — Micrometer, HTTP client, JVM, database, and application metrics.
- [Spring Boot application startup documentation](https://docs.spring.io/spring-boot/reference/features/spring-application.html) — application startup instrumentation and Java 21 virtual-thread considerations.

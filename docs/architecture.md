# Architecture

This document describes the current executable architecture and the target boundaries for the arXiv RAG lab. Do not read proposed package names as implemented classes unless they exist under `src/`.

## Current Starting Point

The repository currently runs as a Spring Boot application with a local data plane and a remote Microsoft AI Foundry model plane:

```text
Browser / HTTP client
        |
        v
Spring Boot 4.1.0
        |
        +-- Static UI
        |     `-- src/main/resources/static
        |
        +-- Runtime/search/chat/job controllers
        |     +-- /api/health and /api/runtime-config
        |     +-- /api/search and /api/chat
        |     `-- /api/embeddings/status and /api/embeddings/jobs
        |
        `-- Actuator
              +-- GET /actuator/health
              `-- GET /actuator/metrics
```

Implemented Java packages:

```text
com.example.arxivrag
├── ArxivRagApplication
├── config
│   ├── BackendHealthController
│   ├── RagRuntimeProperties
│   └── RuntimeConfigController
├── arxiv
├── paper
│   ├── Paper
│   └── PaperRepository
├── embedding
│   ├── RetrievalDocument
│   ├── RetrievalDocumentFactory
│   └── RetrievalDocumentController
└── vector
    ├── SemanticSearchService
    ├── VectorSearchCommandLineRunner
    └── pgvector
```

The `paper`, `arxiv`, `embedding`, `vector`, `search`, and `ai` packages contain executable slices. Embedding ingestion is bounded, resumable, batch-oriented, and single-worker safe. The search API supports semantic pgvector retrieval, PostgreSQL lexical retrieval, and a small rank-fusion hybrid mode. Grounded chat uses local retrieval tools while Foundry is used only for model inference.

## Target Modular Boundaries

### `paper`

Owns canonical paper metadata and relational persistence.

Planned responsibilities:
- `Paper` entity or aggregate
- `PaperRepository`
- category/date/author filters
- paper ID resolution for citations
- relational pagination and browsing APIs

Rules:
- PostgreSQL is the canonical source for display metadata.
- Vector search results must resolve back to papers before being shown to users.
- Do not store generated answers or chat-specific state in the paper domain.

### `arxiv`

Owns source dataset ingestion.

Planned responsibilities:
- streaming JSONL reader
- tolerant record parsing
- deterministic filter rules
- raw-to-canonical mapping
- import statistics
- CLI/import endpoint orchestration

Rules:
- Import must not run during normal application startup.
- Reader, filter, mapper, and import service must each be unit-testable.
- Large files must be streamed, not loaded fully into memory.

### `embedding`

Owns retrieval-document construction and embedding ingestion.

Planned responsibilities:
- title+abstract document formatting
- stable content hashing
- batch embedding workflow
- checkpoint persistence
- Microsoft AI Foundry embedding adapter
- deterministic offline embedding profile

Rules:
- Checkpoint by `(paper_id, chunk_id, embedding_model, content_hash, document_schema_version)`.
- Re-running embedding ingestion should skip unchanged papers.
- Live provider failures must not corrupt already-committed provider batches.
- The local application sends text to the configured AI Foundry embedding deployment and stores returned vectors locally.
- Provider calls happen outside short database write transactions; vector and checkpoint writes are committed together.
- `embedding_jobs` records durable progress while the in-process tracker provides live UI/CLI status.

### `vector`

Owns vector-store-neutral contracts.

Planned contracts:
- `VectorStoreGateway`
- `VectorSearchRequest`
- `VectorSearchResult`
- `EmbeddedDocument`

Rules:
- Application services depend on this package, not on pgvector/Milvus/Chroma clients.
- Results expose application identifiers and scores, not database-specific row shapes.
- Search must be testable with a fake/in-memory gateway before external vector stores exist.
- Retrieval tools call this boundary locally; the remote chat model receives bounded tool results, not database connections.

### `ai`

Owns model-provider and tool-calling integration.

Current responsibilities:
- AI Foundry chat model client through the current provider HTTP adapter
- AI Foundry embedding model client through the current provider HTTP adapter
- tool definitions for semantic search, relational search, and paper lookup
- validation and orchestration of model-requested tool calls

Rules:
- AI Foundry endpoints, deployment names, API versions, and credentials are profile-specific configuration.
- The model may request a tool, but only the local application executes it.
- Never expose arbitrary SQL, database credentials, or unrestricted local network access to the model.

### `search`

Owns the `POST /api/search` HTTP contract. It validates request bounds and selects semantic, PostgreSQL lexical, or hybrid retrieval. All returned papers resolve through canonical PostgreSQL rows.

### `vector.pgvector`

Owns the PostgreSQL pgvector adapter.

Planned responsibilities:
- Flyway-managed vector schema
- pgvector-specific insert/upsert/search SQL or Spring AI adapter wiring
- metadata filtering where pgvector has an advantage because metadata and vectors can share PostgreSQL

Rules:
- pgvector imports must not leak into the parent `vector` package.
- Schema must be created by Flyway, not by Spring AI automatic initialization.

## Target Data Flow

```text
arXiv JSONL
    |
    v
ArxivDatasetReader -> ArxivRecordFilter -> PaperMapper
    |
    v
Local PostgreSQL papers table <-----------------------------+
    |
    v
    RetrievalDocumentFactory -> token-aware batches -> AI Foundry embedding deployment
    |
    v
Local VectorStoreGateway
    |
    +-- pgvector
    +-- Milvus
    +-- Chroma
    |
    v
Local retrieval tools -> filtered pgvector/PostgreSQL -> cited context
    |
    v
Local Spring Boot API <-> AI Foundry chat deployment
    |
    v
Local web UI answer with citations
```

The application and data plane run locally. AI Foundry is the remote model plane. During chat, the model requests a narrow tool, the local application executes the tool against PostgreSQL or the selected local vector store, and the local application sends a bounded result back to the model. The model does not connect directly to the local database.

## Testability Standard

Every implementation stage must expose a result without depending on later stages:

- Runtime baseline: `GET /api/health`, `GET /api/runtime-config`, and UI shell tests.
- Relational ingestion: importer tests and SQL-verifiable paper rows; no embeddings required.
- Embedding documents: formatting/hash tests; no vector database required.
- Vector gateway: fake gateway contract tests plus pgvector integration tests.
- Retrieval: deterministic top-K search tests; no LLM required.
- Chat: mock retrieval and mock chat model tests before live provider tests.
- UI: static shell and route-level behavior tests using mock API responses.

## Recommended Implementation Order

1–6B. Implemented: PostgreSQL/Flyway, paper persistence, streaming import, retrieval documents, checkpointed embeddings, pgvector search, and Foundry chat/embedding adapters.
7–9. Implemented: semantic search API, local retrieval tools, grounded chat, citations, and static UI wiring.
10. Implemented improvement core: bounded/durable embedding jobs, batch Foundry calls, quota-aware retry, short transactions, batch vector writes, versioned checkpoints, filtered vector search, lexical/hybrid search, metrics, and job controls.
11. Deferred: fixed evaluation set, reranking, richer full-text ingestion/chunking, Milvus/Chroma adapters, benchmark comparison, and production security/deployment hardening.

# Architecture

This document describes the current executable architecture and the target boundaries for the arXiv RAG lab. Do not read proposed package names as implemented classes unless they exist under `src/`.

## Current Starting Point

The repository currently runs as a small Spring Boot application:

```text
Browser / HTTP client
        |
        v
Spring Boot 4.1.0
        |
        +-- Static UI
        |     `-- src/main/resources/static
        |
        +-- Runtime controllers
        |     +-- GET /api/health
        |     `-- GET /api/runtime-config
        |
        `-- Actuator
              `-- GET /actuator/health
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
├── embedding
└── vector
    └── pgvector
```

The non-`config` packages are currently boundary placeholders with `package-info.java` files only.

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
- deterministic offline embedding profile

Rules:
- Checkpoint by `(paper_id, chunk_id, embedding_model, content_hash)`.
- Re-running embedding ingestion should skip unchanged papers.
- Live provider failures must not corrupt already-committed batches.

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
PostgreSQL papers table
    |
    v
RetrievalDocumentFactory -> EmbeddingModel
    |
    v
VectorStoreGateway
    |
    v
Retrieval service -> PaperRepository -> cited context
    |
    v
Chat generation service
    |
    v
Grounded answer with citations
```

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

1. Add PostgreSQL/Flyway/Testcontainers infrastructure.
2. Add paper schema, repository, and repository tests.
3. Add streaming arXiv importer with a tiny fixture.
4. Add retrieval-document formatting and deterministic hashing.
5. Add offline embedding model and checkpointing.
6. Add pgvector gateway and semantic search CLI/API.
7. Add grounded answer generation using mock-first tests.
8. Add chat UI only after `POST /api/chat` exists.
9. Add Milvus and Chroma adapters.
10. Add benchmark harness and comparison write-up.

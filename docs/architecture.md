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

The `paper`, `arxiv`, `embedding`, and `vector` packages now contain executable slices. Embedding ingestion writes through the selected gateway, and the retrieval-only `search-vector <query>` CLI resolves ranked vector hits back to canonical papers. A semantic search REST API is still planned for Step 7.

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
- Checkpoint by `(paper_id, chunk_id, embedding_model, content_hash)`.
- Re-running embedding ingestion should skip unchanged papers.
- Live provider failures must not corrupt already-committed batches.
- The local application sends text to the configured AI Foundry embedding deployment and stores returned vectors locally.

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
- AI Foundry chat model client through the current provider HTTP adapter; Spring AI orchestration is planned for the tool-calling step
- AI Foundry embedding model client through the current provider HTTP adapter
- tool definitions for semantic search, relational search, and paper lookup
- validation and orchestration of model-requested tool calls

Rules:
- AI Foundry endpoints, deployment names, API versions, and credentials are profile-specific configuration.
- The model may request a tool, but only the local application executes it.
- Never expose arbitrary SQL, database credentials, or unrestricted local network access to the model.

### `search`

Owns the `POST /api/search` HTTP contract. It validates request bounds and delegates semantic retrieval to `SemanticSearchService`, which resolves vector hits through canonical PostgreSQL paper rows.

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
RetrievalDocumentFactory -> AI Foundry embedding deployment
    |
    v
Local VectorStoreGateway
    |
    +-- pgvector
    +-- Milvus
    +-- Chroma
    |
    v
Local retrieval tools -> PaperRepository -> cited context
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

1. Add PostgreSQL/Flyway/Testcontainers infrastructure.
2. Add paper schema, repository, and repository tests.
3. Add streaming arXiv importer with a tiny fixture.
4. Add retrieval-document formatting and deterministic hashing.
5. Add offline embedding model and checkpointing.
6. Add pgvector gateway and semantic search CLI.
6B. Add AI Foundry chat and embedding adapters plus local retrieval-tool execution. (Provider adapters implemented; tool execution remains planned.)
7. Add the semantic search API.
8. Add grounded answer generation using mock-first tests.
9. Add chat UI only after `POST /api/chat` exists.
10. Add Milvus and Chroma adapters.
11. Add benchmark harness and comparison write-up.

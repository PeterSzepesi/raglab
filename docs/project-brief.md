# arXiv RAG Lab Project Brief

## Goal

Build a working Retrieval-Augmented Generation application over arXiv metadata and abstracts while comparing multiple vector-store approaches. The learning goals are embeddings, chunking, retrieval, grounding, citations, hybrid structured/vector search, and vector-store operations.

The practical end deliverable is:

- a web chatbot that answers questions about papers;
- grounded citations linked back to canonical paper records;
- PostgreSQL as the relational source of truth;
- swappable vector-store adapters for pgvector, Milvus, and Chroma;
- benchmark results and an engineering recommendation.

## Current Starting Point

The current branch is a baseline, not a full RAG system.

Implemented:

- Java 21 Spring Boot application;
- Maven Wrapper build;
- Spring WebMVC;
- Spring Boot Actuator;
- static frontend shell;
- package boundary placeholders outside the paper domain;
- `GET /api/health`;
- `GET /api/runtime-config`;
- PostgreSQL/Flyway infrastructure;
- canonical `papers` schema;
- `Paper` entity and repository;
- streaming arXiv JSONL reader/filter/mapper/import service;
- `import-arxiv <file>` CLI command;
- Spring Boot tests for the shell and safe runtime endpoints;
- GitHub Actions and Dependabot.

Not implemented yet:

- embedding generation;
- vector-store schema or adapters;
- semantic search;
- chat generation;
- Milvus or Chroma;
- benchmark harness.

## Dataset

Use the arXiv metadata dataset from Kaggle/Cornell. It gives enough text for a useful RAG project without requiring PDF parsing in v1.

V1 uses title + abstract as the retrieval document. Full paper text is a stretch goal.

Initial filter:

- categories: `cs.CL`, `cs.AI`, `cs.LG`, `cs.CV`;
- submitted date: on or after `2022-01-01`;
- profile-specific record caps.

## Target Architecture

```text
arXiv JSONL
    |
    v
Streaming importer
    |
    v
PostgreSQL papers table  <----+
    |                         |
    v                         |
Embedding ingestion            |
    |                         |
    v                         |
Vector store gateway           |
    |                         |
    +-- pgvector               |
    +-- Milvus                 |
    +-- Chroma                 |
    |                         |
    v                         |
Retrieval service -------------+
    |
    v
Grounded generation
    |
    v
Web chat UI with citations
```

PostgreSQL remains the canonical source for paper metadata. Vector stores return IDs and scores; the application resolves those IDs through PostgreSQL before generating answers or rendering citations.

## Why Two Store Types

The project should demonstrate where vector search helps and where it is the wrong tool.

- Semantic question: "What are recent approaches to reducing hallucination in LLMs?" Use vector search.
- Structured question: "How many papers by a given author were submitted in 2023?" Use SQL, not vectors.
- Hybrid question: "Summarize diffusion model papers in `cs.CV` from the last 12 months." Use structured filtering plus semantic ranking.
- Grounded answer: answer with cited paper IDs, titles, and source links.

## Recommended Engineering Strategy

Use pgvector first because it has the lowest operational overhead and makes SQL+vector hybrid retrieval easiest. Do not add Milvus or Chroma until pgvector ingestion and search work end to end.

Keep the first frontend static. A separate frontend build becomes worthwhile only after the chat API has real behavior.

Use an offline deterministic embedding implementation in tests and local smoke demos. Live embedding providers should be a profile-specific integration path, not a requirement for every developer action.

## Vector Store Comparison Criteria

Compare each backend using the same corpus slice, embedding model, query set, and evaluation harness.

| Criterion | pgvector | Milvus | Chroma |
|---|---|---|---|
| Local setup | simplest | most complex | simple |
| Metadata filtering | strongest for SQL-first hybrid retrieval | supported, separate service model | supported, prototype-friendly |
| Scaling posture | good until vertical Postgres limits matter | strongest large-scale vector infra | best for local demos/prototypes |
| Learning value | vectors inside relational DB | purpose-built vector infrastructure | fast experimentation |

Benchmark minimums:

- ingestion duration;
- embedding throughput;
- vector upsert throughput;
- query latency p50/p95;
- recall on a hand-checked query set;
- operational notes for setup/debugging.

## Phased Roadmap

1. Baseline app and safe runtime UI.
2. PostgreSQL, Flyway, and paper schema.
3. Streaming arXiv import into PostgreSQL.
4. Retrieval-document formatting and embedding checkpointing.
5. pgvector gateway and semantic search.
6. Grounded chat API with citations.
7. Minimal web chat UI.
8. Hybrid SQL+vector retrieval.
9. Milvus adapter.
10. Chroma adapter.
11. Benchmark harness.
12. Comparison write-up.

## Definition Of Done

- The app can ingest a deterministic arXiv slice.
- The app can embed and search title+abstract documents.
- The chatbot returns grounded answers with citations.
- Each vector store can run through the same retrieval contract.
- The benchmark report contains numbers and operational observations.
- Each major feature has a standalone test or smoke command that proves it works without relying on later roadmap stages.

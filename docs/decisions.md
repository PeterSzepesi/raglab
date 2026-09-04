# V1 Technical Decisions

This file is the implementation contract for the first coherent project path. It separates decisions already reflected in the executable baseline from decisions that are accepted for upcoming work.

## Implemented Baseline

Java version:
21

Build system:
Maven via Maven Wrapper (`mvnw` / `mvnw.cmd`)

Application framework:
Spring Boot 4.1.0

Current backend modules:
- Spring Boot core
- Spring WebMVC
- Spring Boot Actuator
- Configuration properties

Current frontend:
Static HTML/CSS/JavaScript served from `src/main/resources/static`

Current runtime endpoints:
- `GET /`
- `GET /api/health`
- `GET /api/runtime-config`
- `GET /actuator/health`

Current test scope:
- Spring context boot
- static frontend shell
- safe runtime configuration endpoint
- backend health endpoint

## Accepted V1 Target Decisions

AI framework:
Spring AI first.

Alternative framework:
LangChain4j may be evaluated by the other implementation branch, but should not be mixed into this branch until there is a deliberate comparison ticket.

Embedding provider:
Azure OpenAI primary.

Embedding fallback:
OpenAI API.

Embedding model:
`text-embedding-3-small`

Embedding dimension:
1536

Vector similarity:
Cosine distance.

Canonical relational database:
PostgreSQL.

Target PostgreSQL runtime:
PostgreSQL 18 if available in the team environment; otherwise use the newest stable PostgreSQL image that supports the required pgvector version and document the deviation.

Vector extension:
pgvector first.

Target pgvector runtime:
0.8.x.

Database migrations:
Flyway.

Schema ownership:
Flyway owns all PostgreSQL schema changes. Disable automatic Hibernate schema creation. Do not rely on Spring AI automatic pgvector schema initialization.

Initial vector-store implementation:
pgvector.

Later vector-store implementations:
Milvus, then Chroma, behind the same application-level gateway.

Initial arXiv categories:
- `cs.CL`
- `cs.AI`
- `cs.LG`
- `cs.CV`

Initial date range:
`submitted_date >= 2022-01-01`

Dataset profiles:
- `tiny` = 500 matching papers, for fast tests and demos
- `dev` = 50,000 matching papers, for normal local development
- `scale` = all matching papers, for benchmark runs only

Default profile:
`local` for application boot; `dev` for ingestion experiments once persistence exists.

Initial document strategy:
1 paper = 1 retrieval document.

Embedding text:

```text
Title: <title>

Abstract:
<abstract>
```

Vector metadata:
- `paper_id`
- `arxiv_id`
- `chunk_id`
- `categories`
- `submitted_date`

Canonical metadata source:
PostgreSQL `papers` table.

## Technology Recommendations

- Keep this branch on Spring Boot + Spring AI because the project is Java-first and the current baseline already uses Spring Boot.
- Keep the UI static until `POST /api/chat` exists. A React/Vue frontend adds build complexity before there is useful state to manage.
- Use Testcontainers when adding database behavior. Manual Docker-only tests are useful, but they are not enough for repeatable CI.
- Add an offline deterministic embedding profile before live embedding providers. This avoids blocking importer, vector-store, and UI work on API keys or model availability.

## Non-Goals For The First End-To-End Slice

- No full PDF ingestion.
- No reranking.
- No local embedding model hosting.
- No LLM router agent until direct SQL, vector search, and grounded generation are independently working.
- No Milvus or Chroma implementation until pgvector has a passing ingestion/search/chat loop.

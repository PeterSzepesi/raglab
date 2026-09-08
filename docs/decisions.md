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

Model hosting:
Microsoft AI Foundry. Deploy the selected chat model and a small embedding model in AI Foundry, then call their APIs from the locally running Spring Boot application.

Chat model:
Use the Microsoft AI Foundry deployment `gpt-5.6-luna`. The exact deployment remains configuration, not an application package dependency.

Embedding provider:
Microsoft AI Foundry embedding deployment for normal local development and demonstrations.

Embedding fallback:
Offline deterministic embedding model for tests and no-network smoke runs. A direct OpenAI-compatible provider may be evaluated separately if needed.

Embedding model:
The configured Microsoft AI Foundry deployment `text-embedding-3-large`.

Embedding dimension:
1536 for the initial vector schema. The application requests 1536 values from `text-embedding-3-large`; the dimension must match both the provider response and the vector column. If the deployment does not accept the reduced dimension request, use a Flyway migration to change the vector schema rather than transforming vectors.

Microsoft AI Foundry connection:
The current environment uses endpoint `https://models-for-peter-resource.services.ai.azure.com`, API version `2024-10-21`, and region Sweden Central. The API key is runtime-only through `AZURE_AI_FOUNDRY_API_KEY` and must never be committed.

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

Application and data placement:
The frontend, Spring Boot application, PostgreSQL database, and selected vector store run locally. AI Foundry hosts the remote chat and embedding model deployments. The local application calls AI Foundry over outbound APIs; AI Foundry does not receive database credentials or direct database access.

Tool execution:
The chat model receives narrow tool definitions such as semantic search, relational paper search, and paper lookup. When the model requests a tool, the local application validates the arguments, executes the tool against local PostgreSQL or the local vector store, and sends a bounded result back to the model. The model requests retrieval; the application performs retrieval.

## Technology Recommendations

- Keep this branch on Spring Boot + Spring AI because the project is Java-first and the current baseline already uses Spring Boot.
- Use Microsoft AI Foundry as the remote model plane while keeping the application and corpus local. This provides deployed chat and embedding APIs without requiring model weights to run on every developer machine.
- Keep the UI static until `POST /api/chat` exists. A React/Vue frontend adds build complexity before there is useful state to manage.
- Use Testcontainers when adding database behavior. Manual Docker-only tests are useful, but they are not enough for repeatable CI.
- Add an offline deterministic embedding profile before live AI Foundry calls. This avoids blocking importer, vector-store, and UI work on API keys or model availability.
- Keep retrieval tools application-owned and narrowly scoped. Do not allow the remote model to submit arbitrary SQL or access the local database directly.

## Non-Goals For The First End-To-End Slice

- No full PDF ingestion.
- No reranking.
- No local embedding model hosting.
- No LLM router agent until direct SQL, vector search, and grounded generation are independently working.
- No Milvus or Chroma implementation until pgvector has a passing ingestion/search/chat loop.

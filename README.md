# arxiv-rag

[![CI](https://github.com/KenyeresKornel/rag/actions/workflows/ci.yml/badge.svg)](https://github.com/KenyeresKornel/rag/actions/workflows/ci.yml)

Spring Boot baseline for the arXiv retrieval/RAG project.

## Baseline

- Java 21
- Spring Boot 4.1.0
- Maven 3.9.11, invoked through the Maven Wrapper
- Maven Wrapper 3.3.4

The broader v1 technical contract is documented in [`docs/decisions.md`](docs/decisions.md).

## Prerequisites

- JDK 21
- Internet access on the first wrapper run so Maven can be downloaded

A system Maven installation is not required.

## Build and test

```bash
./mvnw clean verify
```

On Windows:

```bat
mvnw.cmd clean verify
```

## Run

```bash
./mvnw spring-boot:run
```

Run with an explicit profile when demonstrating environment-specific settings:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The baseline exposes Spring Boot Actuator health information at:

```text
GET /actuator/health
```

## Local PostgreSQL

The `local` and `dev` profiles expect PostgreSQL on `localhost:5432` with database/user/password `arxiv_rag`. Start the bundled pgvector-enabled database with:

```bash
docker compose up -d postgres
```

Then run the app:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

On Windows:

```bat
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

Flyway owns schema migrations from `src/main/resources/db/migration`, and Hibernate schema generation is disabled with validation-only behavior.

### Legacy V6 migration recovery

An earlier local Ollama experiment used migration version 6 for `create ollama bge m3 embeddings table`. The current Foundry implementation no longer needs that table, but an existing database can still contain its Flyway history entry. New migrations deliberately continue at V7, V8, and V9 so the applied V6 version is never reused.

If that legacy V6 is present, run Flyway `repair` once with both project migration locations before starting the application. Repair marks the retired, no-longer-resolved V6 migration as deleted while preserving the database objects and data. Do not edit `flyway_schema_history` directly and do not remove the PostgreSQL Docker volume.

PostgreSQL/Testcontainers integration tests are opt-in so the default build still works on machines without Docker:

```bash
./mvnw -Ppostgres-it verify
```

On Windows:

```bat
mvnw.cmd -Ppostgres-it verify
```

## Paper schema

The canonical paper metadata table is managed by Flyway in:

```text
src/main/resources/db/migration/V2__create_papers_table.sql
src/main/resources/db/vendor/postgresql/V3__create_paper_postgresql_indexes.sql
```

The Java domain and repository live in:

```text
src/main/java/com/example/arxivrag/paper/Paper.java
src/main/java/com/example/arxivrag/paper/PaperRepository.java
```

Fast repository tests run on H2 in PostgreSQL compatibility mode:

```bat
mvnw.cmd -Dtest=PaperRepositoryTests test
```

PostgreSQL-specific schema/index tests require Docker/Testcontainers:

```bat
mvnw.cmd -Ppostgres-it verify
```

## arXiv import

The importer reads arXiv JSONL files line by line, filters records by configured categories/date, maps accepted records into `papers`, and upserts by `arxiv_id`.

Run the committed tiny fixture against a local PostgreSQL container:

```bat
docker compose up -d postgres
mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local" "-Dspring-boot.run.arguments=import-arxiv src/test/resources/fixtures/arxiv-tiny.jsonl"
```

Run importer-focused tests without Docker:

```bat
mvnw.cmd "-Dtest=*Arxiv*,PaperMapperTests" test
```

For a no-Docker CLI smoke test, use the test classpath so the test-scoped H2 driver is available:

```bat
mvnw.cmd -q dependency:build-classpath "-Dmdep.outputFile=target/test-classpath.txt" "-Dmdep.scope=test"
```

```powershell
$cp = Get-Content target\test-classpath.txt
java "-Dspring.profiles.active=test" -cp "target/classes;target/test-classes;$cp" com.example.arxivrag.ArxivRagApplication import-arxiv src/test/resources/fixtures/arxiv-tiny.jsonl
```

## Retrieval document preview

The Search page can generate the exact deterministic title+abstract document used by embedding ingestion. It does not require an LLM or vector database.

Run the app and open `http://localhost:8080/#/search`:

```bat
mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

API-only preview:

```bat
curl -X POST http://localhost:8080/api/retrieval-documents/preview -H "Content-Type: application/json" -d "{\"arxivId\":\"2401.00001\",\"title\":\"Retrieval Testing\",\"abstractText\":\"A concise abstract.\"}"
```

Focused tests:

```bat
mvnw.cmd "-Dtest=*RetrievalDocument*" test
```

## Vector ingestion, search, and grounded chat

With the local pgvector database running and papers imported, generate and persist vectors. The default Foundry provider batch is 32 documents and the job is resumable through checkpoints:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=embed-papers"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=search-vector neural retrieval"
```

For a bounded smoke run, pass a limit or slice. These options prevent a small test from scanning the complete corpus:

```powershell
java -jar target\arxiv-rag-0.0.1-SNAPSHOT.jar `
  --spring.profiles.active=foundry `
  embed-papers --max-papers=25

java -jar target\arxiv-rag-0.0.1-SNAPSHOT.jar `
  --spring.profiles.active=foundry `
  embed-papers --from-paper-id=100 --to-paper-id=150 --dry-run
```

The embedding command uses the configured embedding gateway, writes vectors through `VectorStoreGateway`, and checkpoints successful work. The search command embeds the query with the same model, searches the selected local vector store, and resolves returned paper IDs through PostgreSQL. The default profile remains deterministic and offline; the `foundry` profile calls the configured Microsoft AI Foundry embedding deployment.

The initial UI shell is served by Spring Boot static resources at:

```text
GET /
```

Frontend assets live under `src/main/resources/static` and are packaged into the backend artifact by Maven's standard resource handling. There is intentionally no separate frontend build step yet.

Safe runtime configuration is exposed at:

```text
GET /api/runtime-config
```

This endpoint is intentionally whitelisted and does not return secrets, tokens, API keys, passwords, or raw environment variables.

## Package structure

```text
com.example.arxivrag
├── arxiv       # dataset ingestion/import boundary
├── paper       # canonical paper domain/persistence boundary
├── embedding   # retrieval documents and embedding ingestion
└── vector      # vector-store-neutral retrieval boundary
    └── pgvector
```

The current working tree contains executable paper, arXiv import, resumable Foundry embedding jobs, pgvector upsert/search, semantic/lexical/hybrid search, local retrieval tools, profile-scoped AI Foundry chat/embedding adapters, grounded chat, and the static UI.

Useful endpoints:

```text
POST /api/search
POST /api/chat                         # foundry profile
GET  /api/embeddings/status
POST /api/embeddings/jobs              # bounded background job
GET  /api/embeddings/jobs/latest
DELETE /api/embeddings/jobs/current
GET  /actuator/metrics
```

The job endpoint is intentionally an unauthenticated local-development control. Add authentication and authorization before binding the application beyond localhost.

## UI

The app shell includes navigation and placeholder pages for:

- System Status
- Import
- Papers
- Search
- Chat
- Vector Stores (coverage, live job status, bounded start/cancel controls)
- Benchmarks
- Settings

## Repository documentation

- `docs/README.md` — documentation map and source-of-truth rules
- `docs/decisions.md` — v1 technical decisions
- `docs/architecture.md` — current architecture and target boundaries
- `docs/dataset.md` — dataset ingestion design
- `docs/implementation_steps.txt` — independently testable implementation steps

## Deferred improvement tickets

1. Add a fixed retrieval evaluation set and exact-versus-HNSW recall measurements.
2. Add reranking and richer PDF/full-text chunking only when evaluation justifies them.
3. Add Milvus/Chroma adapters and benchmark comparison using the same corpus and query set.
4. Add production authentication, managed identity, backups, and deployment hardening.

## Continuous integration

GitHub Actions runs the Maven build and unit tests on every pull request and on pushes to `main`. The workflow uses Java 21, caches Maven dependencies, and invokes the repository's Maven Wrapper:

```bash
./mvnw --batch-mode --no-transfer-progress clean verify
```

Surefire/Failsafe test reports and the Maven build log are uploaded as a GitHub Actions artifact named `maven-test-results-and-build-log`, including on failed CI runs.

Before pushing the repository to GitHub, replace `YOUR_GITHUB_USERNAME` in the CI badge at the top of this README with the repository owner or organization name.

Dependabot is configured to check Maven and GitHub Actions dependencies weekly.

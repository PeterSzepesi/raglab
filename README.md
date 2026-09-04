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

Only package boundaries are created by this bootstrap ticket. Domain classes, repositories, importers, migrations, database infrastructure, and vector-store implementations are intentionally deferred to later tickets.

## UI shell

The app shell includes navigation and placeholder pages for:

- System Status
- Import
- Papers
- Search
- Chat
- Vector Stores
- Benchmarks
- Settings

## Repository documentation

- `docs/README.md` — documentation map and source-of-truth rules
- `docs/decisions.md` — v1 technical decisions
- `docs/architecture.md` — current architecture and target boundaries
- `docs/dataset.md` — dataset ingestion design
- `docs/implementation_steps.txt` — independently testable implementation steps

## Next implementation tickets

1. PostgreSQL/Flyway infrastructure and relational paper schema.
2. Streaming arXiv dataset reader and importer.
3. pgvector and embedding ingestion.
4. Semantic retrieval and paper-ID resolution.

## Continuous integration

GitHub Actions runs the Maven build and unit tests on every pull request and on pushes to `main`. The workflow uses Java 21, caches Maven dependencies, and invokes the repository's Maven Wrapper:

```bash
./mvnw --batch-mode --no-transfer-progress clean verify
```

Surefire/Failsafe test reports and the Maven build log are uploaded as a GitHub Actions artifact named `maven-test-results-and-build-log`, including on failed CI runs.

Before pushing the repository to GitHub, replace `YOUR_GITHUB_USERNAME` in the CI badge at the top of this README with the repository owner or organization name.

Dependabot is configured to check Maven and GitHub Actions dependencies weekly.

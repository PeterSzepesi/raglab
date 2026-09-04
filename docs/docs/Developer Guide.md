# arXiv RAG Developer Guide

## Overview

`arxiv-rag` is a Java/Spring learning project for building a Retrieval-Augmented Generation system over arXiv paper metadata and abstracts. The intended architecture combines PostgreSQL for canonical structured metadata with swappable vector stores for semantic retrieval.

The current branch is intentionally small. It provides a tested Spring Boot baseline and static UI shell. Most RAG functionality is still planned.

## Current Executable State

Implemented:

- Java 21 Spring Boot application;
- Maven Wrapper build;
- Spring WebMVC;
- Spring Boot Actuator;
- safe backend health endpoint;
- safe runtime configuration endpoint;
- static frontend shell;
- package boundary placeholders;
- JUnit/Spring Boot tests;
- GitHub Actions CI;
- Dependabot.

Not implemented yet:

- PostgreSQL/Flyway schema;
- JPA entities and repositories;
- arXiv importer;
- embedding model integration;
- pgvector/Milvus/Chroma adapters;
- semantic search API;
- chat API;
- benchmark runner.

## Build And Test

Windows:

```powershell
.\mvnw.cmd clean verify
```

Unix-like shells:

```bash
./mvnw clean verify
```

The baseline tests verify:

- Spring context startup;
- `GET /`;
- `GET /api/health`;
- `GET /api/runtime-config`;
- runtime config does not leak a sample secret value.

## Run Locally

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

With an explicit profile:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

Useful URLs:

```text
http://localhost:8080/
http://localhost:8080/api/health
http://localhost:8080/api/runtime-config
http://localhost:8080/actuator/health
```

## Runtime Configuration

Runtime feature flags live under `rag.features` in the Spring configuration files.

Current profile files:

- `src/main/resources/application.yml`
- `src/main/resources/application-local.yml`
- `src/main/resources/application-dev.yml`
- `src/main/resources/application-test.yml`

`/api/runtime-config` is intentionally safe for frontend use. Do not add raw environment variables, provider API keys, database passwords, tokens, or full connection strings to this response.

## Package Boundaries

Use the existing packages deliberately:

- `config` — safe runtime and operational controllers.
- `arxiv` — dataset reader, filter, mapper, and import orchestration.
- `paper` — canonical paper domain and relational persistence.
- `embedding` — retrieval-document formatting, content hashing, and embedding ingestion.
- `vector` — store-neutral contracts and application-facing search types.
- `vector.pgvector` — pgvector-specific adapter details.

Rules:

- Application services should depend on store-neutral interfaces where practical.
- PostgreSQL remains the canonical metadata source.
- Vector results should resolve back to paper rows before display or citation.
- Do not put dataset import work in normal application startup.

## Adding A Feature

Use this minimum workflow:

1. Read `docs/docs/decisions.md` and `docs/docs/implementation_steps.txt`.
2. Add the smallest independently testable slice.
3. Prefer unit tests for pure mapping/formatting/filtering logic.
4. Prefer Testcontainers for database behavior once persistence is added.
5. Keep live AI provider calls behind profiles and mocks so normal tests are offline.
6. Update docs when implementation status changes.

## Recommended Test Split

- Pure unit tests: mappers, filters, prompt builders, hash generation, benchmark math.
- Spring MVC tests: API validation and response contracts.
- Testcontainers integration tests: Flyway, repository queries, pgvector behavior.
- Manual smoke tests: CLI commands, UI routes, live provider calls.

Do not require Azure/OpenAI keys for `mvnw clean verify`.

## Implementation Sequence

Follow `docs/docs/implementation_steps.txt`. The short version:

1. Preserve baseline behavior.
2. Add PostgreSQL/Flyway/Testcontainers.
3. Add paper schema and repository.
4. Add streaming arXiv import.
5. Add retrieval-document formatting.
6. Add embedding checkpointing.
7. Add pgvector search.
8. Add retrieval and chat APIs.
9. Wire the UI.
10. Add Milvus/Chroma.
11. Add benchmark reporting.

## Documentation Discipline

When code lands, update these files in the same pull request:

- `docs/docs/project-brief.md` if project scope or roadmap changes;
- `docs/docs/decisions.md` if technology decisions change;
- `docs/docs/architecture.md` if package boundaries or flows change;
- `docs/docs/dataset.md` if ingestion rules change;
- `docs/docs/implementation_steps.txt` if step ordering or acceptance criteria change.

Planning notes in `docs/plans/` and `docs/markdown/` are not authoritative unless explicitly promoted into `docs/docs/`.

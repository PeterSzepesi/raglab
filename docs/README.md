# Documentation Map

This folder contains both the current project contract and older experiment notes. Treat the files in `docs/` as the current source of truth.

## Current source of truth

- `docs/project-brief.md` — product goal, current starting point, target architecture, and experiment strategy.
- `docs/decisions.md` — pinned v1 technical decisions and explicit alternatives to evaluate separately.
- `docs/architecture.md` — current executable architecture plus proposed package boundaries.
- `docs/dataset.md` — dataset ingestion design, acceptance criteria, and local test slices.
- `docs/Developer Guide.md` — how to build, run, test, and add features without mixing concerns.
- `docs/implementation_steps.txt` — ordered implementation plan with each stage independently testable.

## Legacy / experiment notes

- `docs/markdown/` duplicates early planning files. Keep these only as historical snapshots unless you intentionally resync them.
- `docs/plans/` contains colleague/agent-specific proposals. They are useful input, but some files describe features that are not present in this branch.

## Important current-state warning

As of this branch, the executable application includes the Spring Boot baseline, PostgreSQL/Flyway paper persistence, streaming arXiv import, retrieval-document formatting, embedding checkpointing, vector upsert/search contracts, the in-memory gateway, and the pgvector adapter.

Step 6B adds profile-scoped Microsoft AI Foundry chat/embedding adapters and a direct chat smoke command. Steps 7–9 now add the semantic search API, bounded local retrieval tools, Foundry-grounded chat API, and static UI integration. Milvus/Chroma adapters and benchmark reporting remain planned.

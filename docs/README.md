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

As of this branch, the executable application is a Spring Boot baseline with a static UI shell and two safe runtime endpoints:

- `GET /api/health`
- `GET /api/runtime-config`

The branch does not yet contain database migrations, JPA entities, arXiv importers, embedding ingestion, vector-store adapters, chat generation, or benchmark code. Planning documents must not describe those as implemented until code exists under `src/` and tests verify it.

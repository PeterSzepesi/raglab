# Local and hosted operations runbook

**Status:** active

## Normal local startup

1. Start Docker Desktop and wait for its Linux engine.
2. Start the persistent PostgreSQL volume:

   ```powershell
   docker compose up -d --wait postgres
   docker compose ps
   ```

3. Open a new PowerShell after changing user environment variables. Confirm presence without printing the key:

   ```powershell
   [bool]$env:AZURE_AI_FOUNDRY_API_KEY
   ```

4. Build the UI and application, then start the non-terminating server:

   ```powershell
   npm ci
   npm run build
   .\mvnw.cmd clean verify
   .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=foundry"
   ```

5. Open `http://127.0.0.1:8080/`. Use `/actuator/health/liveness`, `/actuator/health/readiness`, `/api/v1/health`, and the System Status tab for diagnosis.

The server binds to loopback by default. Set `RAG_SERVER_ADDRESS=0.0.0.0` only inside a protected container/network. Mutation endpoints are loopback-only unless `RAG_ADMIN_TOKEN` is configured; then every administrative request must send `X-Admin-Token`.

## Data persistence and migration safety

The Compose volume `arxiv-rag-postgres-data` is mounted at `/var/lib/postgresql`, the PostgreSQL 18-compatible location. `docker compose stop`, container recreation, and image replacement retain papers, vectors, checkpoints, jobs, and corpus generation. `docker compose down -v` intentionally deletes them.

Never edit an applied Flyway migration. Add a new numbered migration. On checksum failure, compare the checked-in file with the database history first; use Flyway repair only when the database history is known to be wrong, never to hide an unexplained edit.

## Durable jobs

- Import and embedding jobs are persisted before submission.
- Workers claim jobs atomically. Running jobs renew a two-minute lease during progress writes.
- An expired lease becomes `INTERRUPTED`; resume it from the UI/API.
- Cancellation is persisted, not only held in a Java `Future`.
- Imports record a source SHA-256. Embedding jobs record provider requests, last paper, filters, failures, and timestamps.

For long work, inspect without stopping the worker:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/v1/embeddings/status
Invoke-RestMethod http://127.0.0.1:8080/api/v1/embeddings/jobs
```

## Metrics dashboard

Start the optional local-only stack after the application is running:

```powershell
docker compose --profile monitoring up -d prometheus grafana
```

Prometheus is at `http://127.0.0.1:9090`; Grafana is at `http://127.0.0.1:3000`. Change `GRAFANA_ADMIN_PASSWORD` before using it beyond a disposable workstation. The provisioned dashboard covers chat rate/latency/TTFT, retries, embedding throughput, and Hikari pressure. Prometheus scrapes `host.docker.internal:8080/actuator/prometheus`.

OTLP trace export is disabled by default. Enable only when a collector exists:

```powershell
$env:OTEL_TRACES_EXPORT_ENABLED = "true"
$env:OTEL_EXPORTER_OTLP_TRACES_ENDPOINT = "http://127.0.0.1:4318/v1/traces"
```

## Incident triage

| Symptom | Check | Safe action |
|---|---|---|
| App will not start | Flyway error and `docker compose ps` | restore DB reachability; never modify old migrations |
| 429 | admission permits, Foundry quota/retry metrics | retry later; reduce bulk work; do not raise limits blindly |
| 503/504 | passive provider event/circuit state and request ID | verify credential/endpoint; wait for circuit cooldown |
| Chat says no evidence | paper/vector coverage and filters | import/embed changed papers or broaden filters |
| Embedding appears frozen | durable job `updatedAt`, provider calls, last error | wait for quota, cancel, or resume after lease expiry |
| Readiness down, liveness up | PostgreSQL/pgvector details | restore dependency; do not restart-loop the healthy JVM |

Provider errors exposed to clients are sanitized. Use local logs plus application/provider request IDs; do not log question text, abstracts, credentials, authorization headers, or raw provider bodies.

## Container and graceful shutdown

Build with `docker build -t arxiv-rag:local .`. Supply configuration at runtime, bind 8080 intentionally, and use Actuator probes. The image runs as a non-root user on a Java 21 JRE. Spring graceful shutdown stops admission of new work and gives active requests a bounded opportunity to finish.

Before a second replica, follow the scale gates in `docs/operations/scaling.md` and keep the sum of Hikari pool sizes below PostgreSQL capacity.

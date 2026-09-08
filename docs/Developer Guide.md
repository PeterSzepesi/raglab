# arXiv RAG Developer Guide

## Overview

`arxiv-rag` is a Java/Spring learning project for building a Retrieval-Augmented Generation system over arXiv paper metadata and abstracts. The intended architecture combines PostgreSQL for canonical structured metadata with swappable local vector stores for semantic retrieval and Microsoft AI Foundry for remote chat and embedding model deployments.

The current branch is intentionally small. It provides a tested Spring Boot baseline, static UI shell, resumable embedding ingestion, a retrieval-only vector search path, and the first Microsoft AI Foundry provider adapters. The user-facing search API, retrieval tools, and grounded chat loop are still planned.

## Current Executable State

Implemented:

- Java 21 Spring Boot application;
- Maven Wrapper build;
- Spring WebMVC;
- Spring Boot Actuator;
- safe backend health endpoint;
- safe runtime configuration endpoint;
- static frontend shell;
- package boundary placeholders outside the paper domain;
- PostgreSQL/Flyway infrastructure;
- canonical `papers` schema;
- `Paper` entity and repository;
- streaming arXiv JSONL reader/filter/mapper/import service;
- `import-arxiv <file>` CLI command;
- retrieval-document formatting and SHA-256 content hashing;
- UI/API retrieval-document preview;
- offline deterministic embedding generation with checkpointing;
- store-neutral vector contracts, in-memory search, pgvector persistence, and the `search-vector <query>` CLI;
- profile-scoped Microsoft AI Foundry chat/embedding HTTP adapters and the `chat-model <question>` smoke command;
- JUnit/Spring Boot tests;
- GitHub Actions CI;
- Dependabot.

Not implemented yet:

- Spring AI orchestration and Microsoft AI Foundry retrieval-tool loop;
- Milvus/Chroma adapters;
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

## Microsoft AI Foundry Profile

Step 6B connects the local application to the model deployments in Microsoft AI Foundry. The application, PostgreSQL database, and pgvector store remain local. AI Foundry only receives model requests; it never receives database credentials and it cannot open a connection to the local database.

The configured non-secret deployment settings are:

- endpoint: `https://models-for-peter-resource.services.ai.azure.com`;
- API version: `2024-10-21`;
- chat deployment: `gpt-5.6-luna`;
- embedding deployment: `text-embedding-3-large`;
- region: Sweden Central;
- application vector dimension: `1536`.

The API key supplied during setup must be rotated because it was pasted into a chat. Never put the replacement key in Git, YAML, browser code, command history, or a document. The application reads it only from `AZURE_AI_FOUNDRY_API_KEY`.

### Configure the local shell

After rotating the key in Azure, open a new PowerShell window and set the values for that session:

```powershell
$env:AZURE_AI_FOUNDRY_ENDPOINT = "https://models-for-peter-resource.services.ai.azure.com"
$env:AZURE_AI_FOUNDRY_API_KEY = "<rotated-key>"
$env:AZURE_AI_FOUNDRY_API_VERSION = "2024-10-21"
$env:AZURE_AI_FOUNDRY_CHAT_DEPLOYMENT = "gpt-5.6-luna"
$env:AZURE_AI_FOUNDRY_EMBEDDING_DEPLOYMENT = "text-embedding-3-large"
$env:FOUNDRY_EMBEDDING_DIMENSIONS = "1536"
```

The checked-in `application-foundry.yml` contains the endpoint and deployment defaults but an empty key default. This makes accidental unauthenticated use fail clearly without creating a secret in source control. The `foundry` Spring profile creates the remote adapters; other profiles keep using the offline deterministic embedding model.

### Run the first provider checks

Start the local database before commands that import or embed papers:

```powershell
docker compose up -d postgres
```

Run each command in a separate process because the CLI runners terminate the Spring application after completing their command:

```powershell
.\mvnw.cmd spring-boot:run `
  "-Dspring-boot.run.profiles=foundry" `
  "-Dspring-boot.run.arguments=chat-model Explain RAG in one sentence"

.\mvnw.cmd spring-boot:run `
  "-Dspring-boot.run.profiles=foundry" `
  "-Dspring-boot.run.arguments=import-arxiv .\src\test\resources\fixtures\arxiv-tiny.jsonl"

.\mvnw.cmd spring-boot:run `
  "-Dspring-boot.run.profiles=foundry" `
  "-Dspring-boot.run.arguments=embed-papers"

.\mvnw.cmd spring-boot:run `
  "-Dspring-boot.run.profiles=foundry" `
  "-Dspring-boot.run.arguments=search-vector neural retrieval"
```

The first command proves chat connectivity. The import command loads canonical paper metadata. The embedding command sends each retrieval document to `text-embedding-3-large`, requests 1536 values, and stores them in the existing `paper_embeddings` pgvector table while checkpointing successful content hashes. The final command proves that the local vector store can search those vectors. Retrieval-only search is intentional at this stage: the remote model is not yet allowed to invoke database tools, and the frontend APIs are planned for Steps 7–9.

The current HTTP adapter uses the deployment-based Azure OpenAI-compatible routes:

```text
POST {endpoint}/openai/deployments/{deployment}/chat/completions?api-version=2024-10-21
POST {endpoint}/openai/deployments/{deployment}/embeddings?api-version=2024-10-21
```

The deployment name is included both in the route and as the request body's `model` value, matching the supplied Foundry deployment names. If the Code tab for this specific resource gives an `/openai/v1/` request instead, treat that generated request as authoritative and update the route style before the live smoke test; do not silently retry both routes.

## Historical Optional Model Hosting With Visual Studio Enterprise

This section is retained as a fallback reference only. The active implementation now uses Microsoft AI Foundry through the `foundry` profile; the Ollama/VM implementation has been reverted. Do not create `application-ollama.yml` or apply the temporary 1024-dimensional schema unless the interim provider is deliberately reintroduced as a separate change.

Visual Studio Enterprise standard subscriptions currently advertise a $150 monthly Azure dev/test individual credit, but the exact benefit depends on how the subscription was purchased and assigned. The credit must first be activated from the Visual Studio Subscriber Portal and is intended for personal development and testing. It is not a permanent free VM, has no production SLA, and availability, region restrictions, and quotas apply. Microsoft also reserves the right to suspend continuously running instances under the subscriber benefit rules. Verify the Azure benefit tile and current allowance for the specific subscription before creating resources.

The VM cannot host proprietary AI Foundry models such as `gpt-4o-mini` or `text-embedding-3-small`. Those models are consumed through Foundry APIs. The interim VM instead hosts open-weight replacements:

- chat: Ollama `phi4-mini` (3.8B quantized model);
- embedding: Ollama `bge-m3` (1024-dimensional embedding model).

The 1024-dimensional embedding is deliberately not treated as a 1536-dimensional Foundry vector. A vector must retain the dimension produced by its model. The implementation should therefore use a separate temporary vector collection/table for the VM profile, or use a disposable database reset for the experiment. The existing 1536-dimensional collection remains reserved for `text-embedding-3-small`.

### 1. Activate the Visual Studio Azure benefit

1. Sign in to `https://my.visualstudio.com` using the identity to which Visual Studio Enterprise is assigned.
2. Open Benefits and locate the Azure dev/test individual credit.
3. Activate the benefit using the same account, or an alternate identity explicitly associated with the subscription.
4. In the Azure Portal, confirm that the Visual Studio Azure subscription is visible and selected in the subscription switcher.

If the Azure benefit is not visible, the subscription may be an IDE-only entitlement, may not yet be assigned, or the organization administrator may need to enable it. Visual Studio Enterprise by itself is not a VM entitlement; the Azure subscription/credit benefit is the part that permits resource creation.

### 2. Create a small Linux VM

Create a resource group such as `rg-arxiv-rag-interim` and then create an Ubuntu Linux VM in a region where the subscription has capacity.

For the first test, prefer a small general-purpose CPU VM with approximately 4 vCPUs and 16 GB RAM. A GPU is not required for a small quantized model and can consume the monthly credit very quickly. GPU families also commonly begin with zero quota and require a quota request, so do not make GPU availability a prerequisite.

Configure:

- SSH public-key authentication;
- a non-root administrator account;
- a small managed OS disk plus enough data disk for model files;
- inbound SSH (22) only from the developer's current public IP;
- automatic shutdown;
- no public inbound port for Ollama (11434).

Microsoft's Linux VM quickstart covers the portal flow, SSH-key setup, network rules, and cleanup. A VM in the `Stopped` state can still incur compute charges; it must be `Deallocated` to stop compute billing. Delete the resource group when the experiment is complete.

### 3. Install and verify Ollama on the VM

Connect over SSH and install Ollama using the official installation instructions. Then download the two models:

```bash
ollama pull phi4-mini
ollama pull bge-m3
ollama list
```

Verify chat locally on the VM:

```bash
curl http://127.0.0.1:11434/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"model":"phi4-mini","messages":[{"role":"user","content":"Reply with exactly OK"}],"stream":false}'
```

Verify embeddings locally on the VM:

```bash
curl http://127.0.0.1:11434/api/embed \
  -H 'Content-Type: application/json' \
  -d '{"model":"bge-m3","input":["semantic retrieval over research papers"]}'
```

Confirm that the embedding response contains 1024 values. Ollama exposes its API on port 11434 and supports both chat and embedding endpoints. Its current model library documents Phi-4-mini tool calling and BGE-M3 embedding usage.

### 4. Connect the local application securely

Keep Ollama bound to loopback on the VM. From Windows, create an SSH tunnel:

```powershell
ssh -i C:\path\to\arxiv-rag-vm.pem `
  -N `
  -L 11434:127.0.0.1:11434 `
  azureuser@<VM_PUBLIC_IP>
```

The local application can now call `http://localhost:11434`, while port 11434 remains closed to the Internet. Keep this terminal open while testing.

### 5. Add the `ollama` application profile

Add a profile-specific configuration file, for example `src/main/resources/application-ollama.yml`:

```yaml
spring:
  ai:
    model:
      chat: ollama
      embedding: ollama
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: phi4-mini
      embedding:
        model: bge-m3

embedding:
  ingestion:
    model-name: bge-m3
    dimensions: 1024

vector:
  store:
    type: pgvector
    collection: ollama-bge-m3
```

The historical implementation approach described above is no longer active. The current provider implementation is `FoundryEmbeddingModel` behind `EmbeddingModelGateway`, and `FoundryChatGateway` behind `GenerationGateway`. Keep the existing deterministic implementation active for the default and test profiles.

Because the current pgvector schema is `vector(1536)`, add a Flyway-managed temporary collection/table using `vector(1024)` and an appropriate HNSW index, or use a disposable database for this experiment. Do not pad, truncate, or otherwise transform a 1024-dimensional embedding into 1536 dimensions. The checkpoint identity must include the embedding model and dimension so that Ollama and Foundry vectors cannot be mixed.

### 6. Test the interim path

Use a tiny paper corpus first:

```powershell
.\mvnw.cmd spring-boot:run `
  "-Dspring-boot.run.profiles=ollama" `
  "-Dspring-boot.run.arguments=embed-papers"
```

Then run the existing CLI search:

```powershell
.\mvnw.cmd spring-boot:run `
  "-Dspring-boot.run.profiles=ollama" `
  "-Dspring-boot.run.arguments=search-vector neural retrieval"
```

For a direct chat connectivity test, use:

```powershell
.\mvnw.cmd spring-boot:run `
  "-Dspring-boot.run.profiles=ollama" `
  "-Dspring-boot.run.arguments=chat-model Explain retrieval augmented generation in one sentence"
```

Only after this succeeds should the model be connected to the chat API and UI. The browser still calls the local Spring Boot API; it never calls Ollama directly.

## Runtime Configuration

Runtime feature flags live under `rag.features` in the Spring configuration files.

Current profile files:

- `src/main/resources/application.yml`
- `src/main/resources/application-local.yml`
- `src/main/resources/application-dev.yml`
- `src/main/resources/application-test.yml`
- `src/main/resources/application-foundry.yml`

`/api/runtime-config` is intentionally safe for frontend use. Do not add raw environment variables, provider API keys, database passwords, tokens, or full connection strings to this response.

## Package Boundaries

Use the existing packages deliberately:

- `config` — safe runtime and operational controllers.
- `arxiv` — dataset reader, filter, mapper, and import orchestration.
- `paper` — canonical paper domain and relational persistence.
- `embedding` — retrieval-document formatting, content hashing, and embedding ingestion.
- `vector` — store-neutral contracts and application-facing search types.
- `vector.pgvector` — pgvector-specific adapter details.
- `ai` — provider-neutral generation boundary and provider-specific model clients.
- `ai.foundry` — Microsoft AI Foundry HTTP client and deployment-specific model adapters.

Rules:

- Application services should depend on store-neutral interfaces where practical.
- PostgreSQL remains the canonical metadata source.
- Vector results should resolve back to paper rows before display or citation.
- Do not put dataset import work in normal application startup.

## Adding A Feature

Use this minimum workflow:

1. Read `docs/decisions.md` and `docs/implementation_steps.txt`.
2. Add the smallest independently testable slice.
3. Prefer unit tests for pure mapping/formatting/filtering logic.
4. Prefer Testcontainers for database behavior once persistence is added.
5. Keep live AI provider calls behind profiles and mocks so normal tests are offline.
6. Keep Microsoft AI Foundry calls behind profiles and mocks. The model can request narrow retrieval tools, but the local Spring Boot application executes those tools against local PostgreSQL or the selected local vector store.
7. Update docs when implementation status changes.

## Recommended Test Split

- Pure unit tests: mappers, filters, prompt builders, hash generation, benchmark math.
- Spring MVC tests: API validation and response contracts.
- Testcontainers integration tests: Flyway, repository queries, pgvector behavior.
- Manual smoke tests: CLI commands, UI routes, live provider calls.

Do not require AI Foundry credentials or any other live provider keys for `mvnw clean verify`.

## Implementation Sequence

Follow `docs/implementation_steps.txt`. The short version:

1. Preserve baseline behavior.
2. Add PostgreSQL/Flyway/Testcontainers.
3. Add paper schema and repository.
4. Add streaming arXiv import.
5. Add retrieval-document formatting.
6. Add embedding checkpointing.
7. Finish pgvector search and vector-ingestion wiring. (Completed.)
8. Connect Microsoft AI Foundry chat and embedding deployments through the `foundry` profile. (Implemented.)
9. Add the semantic search API, local semantic/relational retrieval tools, and grounded chat APIs.
10. Wire the UI.
11. Add Milvus/Chroma.
12. Add benchmark reporting.

## Documentation Discipline

When code lands, update these files in the same pull request:

- `docs/project-brief.md` if project scope or roadmap changes;
- `docs/decisions.md` if technology decisions change;
- `docs/architecture.md` if package boundaries or flows change;
- `docs/dataset.md` if ingestion rules change;
- `docs/implementation_steps.txt` if step ordering or acceptance criteria change.

Planning notes in `docs/plans/` and `docs/markdown/` are not authoritative unless explicitly promoted into `docs/`.

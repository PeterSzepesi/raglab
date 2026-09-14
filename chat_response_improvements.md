# Chat Response Performance Improvement Plan

> Implementation status (2026-09-11): Steps 1–8 and 10 are implemented. Step 9's application-side candidate-deployment configuration and A/B benchmark are implemented; provisioning a second model deployment and choosing it require an explicit Azure action plus measured benchmark results. See `README.md` for runtime commands, diagnostics, and configuration.

## Purpose

The current grounded chat path commonly takes more than ten seconds. This document explains why that is plausible in the present architecture and gives a measured, step-by-step implementation plan for reducing both:

- **time to first useful output**, which is what the user feels;
- **time to complete response**, which is the actual end-to-end work.

The goal is not to make the application fast by weakening grounding. Every optimized answer must still be based on locally retrieved papers and must return valid arXiv citations.

## Executive recommendation

Implement the work in this order:

1. Measure each stage and expose provider token/retry information.
2. Separate chat and embedding quota control, then bound answer and context size.
3. Add a one-chat-call **pre-retrieval fast path** for ordinary RAG questions.
4. Stream the final answer to the browser.
5. Add bounded caches and parallelize independent retrieval operations.
6. Tune PostgreSQL only if measurements show that local retrieval is material.
7. Compare a faster Foundry chat deployment only after the application path is efficient.

The pre-retrieval fast path is expected to provide the largest reduction in actual completion time because it removes one complete chat-model inference request from the normal request path. Streaming provides the largest improvement in perceived responsiveness.

## Current request path

For a typical conceptual question, `GroundedChatService` currently performs the following sequence:

```text
Browser
  |
  | POST /api/chat
  v
GroundedChatService
  |
  | 1. Foundry chat request with three tool definitions
  |    Model decides which retrieval tool to call
  v
RetrievalToolService
  |
  | 2. semantic_search usually calls the remote embedding deployment
  | 3. pgvector searches locally
  | 4. JPA loads canonical paper records
  v
GroundedChatService
  |
  | 5. Foundry chat request with tool results
  |    Model generates the grounded answer
  v
Browser receives the complete JSON response
```

The simplified latency equation is:

```text
T_total = T_chat_tool_selection
        + T_query_embedding
        + T_vector_search
        + T_paper_hydration
        + T_chat_answer_generation
        + T_retry_or_quota_wait
```

The browser currently receives nothing until every stage and the complete answer have finished.

## Findings from the current implementation

### 1. Two chat-model calls are the normal minimum

The tool-calling loop first asks the model to select a tool. After the tool result is added to the message history, it asks the model again to produce the answer. Each model request has its own network, queue, prompt-processing, reasoning, and generation latency.

`MAX_TOOL_ROUNDS` is currently three. A model that requests another tool after the first result can therefore trigger a third remote chat request.

### 2. Semantic retrieval adds another remote model call

`semantic_search` creates a query embedding through `text-embedding-3-large` before searching pgvector. PostgreSQL is local, but the embedding calculation is remote.

This query embedding must come from the same embedding space as the stored paper vectors. Replacing only the query model with a smaller model would make similarity scores invalid. Changing embedding models requires a separate vector collection or a full corpus re-embedding.

### 3. The second chat prompt can be fairly large

The tool returns up to five papers by default, with as much as 1,200 abstract characters per paper, plus metadata, tool-call messages, the system prompt, and tool schemas. A larger prompt increases model prompt-processing time.

### 4. Answer length is not bounded in code

The system prompt says to be concise, but the chat request does not currently set a hard completion-token limit. Model latency grows with generated output. Microsoft describes total generation latency as time to first token plus time-between-tokens multiplied by generated token count.

### 5. The UI is fully non-streaming

`POST /api/chat` waits for the complete Foundry response and then returns one JSON object. Even if the provider produces its first token quickly, the user cannot see it.

### 6. One quota limiter is shared by chat and embedding traffic

`FoundryClient` currently owns one `FoundryQuotaLimiter` for both deployments. The supplied quota is per deployment, so a background paper-embedding job can unnecessarily consume the local accounting used by chat and make a chat request wait. This may not explain an idle 10-second request, but it can cause severe latency while ingestion is running.

### 7. Retry and quota waits are invisible at the chat boundary

Transient HTTP statuses can add one, two, four, or more seconds of backoff. The current API response does not indicate whether the request waited for quota or retried. It also does not expose provider prompt/completion token counts.

### 8. PostgreSQL is probably not the primary bottleneck

The corpus contains roughly 17,000 papers and the vector table already has an HNSW cosine index. Local vector search and canonical-paper hydration should normally be much smaller than remote inference latency. This must still be measured with `EXPLAIN (ANALYZE, BUFFERS)` before it is assumed.

## Preliminary performance targets

These are initial engineering targets, not promises. Revise them after collecting a baseline on the actual Foundry deployment.

| Metric | Initial target |
| --- | --- |
| Ordinary grounded question, chat-model calls | 1 |
| Ordinary semantic question, total remote calls | 2: one embedding plus one chat |
| Exact arXiv-ID lookup, total remote calls | 1 chat call |
| Warm p50 time to first visible answer token | <= 2.5 seconds |
| Warm p95 time to first visible answer token | <= 5 seconds |
| Warm p50 complete response | <= 6 seconds |
| Warm p95 complete response | <= 10 seconds |
| Local retrieval excluding remote embedding | <= 150 ms p95 |
| Citation correctness | No regression from the evaluation baseline |

Measure cold startup separately from warm requests. The first request can include DNS, TLS, class loading, connection-pool, and provider warm-up costs that do not represent steady-state behavior.

## Step 1 — Add stage-level chat telemetry

### Objective

Determine exactly where each second is spent before changing orchestration.

### Implementation

1. Add `ChatMetrics` in `ai.chat` using Micrometer.
2. Record an end-to-end timer named `rag.chat.latency`.
3. Record stage timers with a bounded `stage` tag:
   - `tool_selection_model`;
   - `query_embedding`;
   - `vector_search`;
   - `paper_hydration`;
   - `tool_execution`;
   - `answer_model`;
   - `quota_wait`;
   - `retry_backoff`.
4. Add counters for:
   - chat requests and failures;
   - Foundry calls per chat request;
   - tool rounds;
   - tool calls by the fixed tool-name set;
   - retries by HTTP status;
   - cache hits and misses once caching exists.
5. Extend `FoundryChatResult` with provider metadata:
   - prompt tokens;
   - completion tokens;
   - total tokens;
   - reasoning tokens when returned;
   - cached prompt tokens when returned;
   - provider request ID;
   - request duration;
   - retry count.
6. Capture `apim-request-id` as well as `x-request-id`. The Azure REST documentation identifies `apim-request-id` as a troubleshooting header.
7. Add a per-request correlation ID and include it in structured logs.
8. Return a standards-compatible `Server-Timing` header from `ChatController`, for example:

   ```text
   Server-Timing: retrieve;dur=620, foundry-plan;dur=3100, foundry-answer;dur=4900
   ```

9. Keep detailed timing data out of the normal JSON contract unless a local debug property is enabled.

### Tests

- Unit-test usage parsing when optional usage fields are absent.
- Unit-test retry and quota-wait timer recording.
- Mock-MVC test the `Server-Timing` header.
- Verify metric tags come only from fixed enums; never tag by query, paper ID, or request ID because that creates unbounded metric cardinality.

### Acceptance criteria

- One request can be decomposed into model, embedding, database, retry, and quota time.
- The sum of stage timings approximately explains end-to-end latency.
- Run at least 20 warm requests covering semantic, exact-ID, relational, no-result, and multi-tool questions.

## Step 2 — Separate quota domains and make hidden waits observable

### Objective

Prevent embedding ingestion from locally delaying interactive chat and make provider throttling obvious.

### Implementation

1. Replace the single limiter in `FoundryClient` with a registry keyed by deployment and operation type.
2. Configure separate limits for:
   - chat deployment requests/tokens;
   - embedding deployment requests/tokens.
3. Record the actual time spent inside `acquire`.
4. Give interactive query embeddings priority over background ingestion batches, or reserve a small embedding quota slice for interactive search.
5. Add a bounded queue-wait limit for chat. If the wait exceeds the limit, return an explicit busy/throttled response rather than appearing frozen.
6. Log retry status, delay, attempt number, and provider request ID without logging prompts or the API key.
7. Keep retry behavior for transient failures, but use separate retry policies for interactive chat and offline ingestion. Interactive requests should usually fail faster than a background job.

### Tests

- Simulate a saturated embedding limiter and verify chat can still proceed.
- Verify two calls to the same deployment share a limiter.
- Verify calls to different deployments do not consume each other's local request count.
- Verify retry delays appear in metrics.

### Acceptance criteria

- A running embedding job does not add local limiter delay to chat requests.
- Any request taking more than one second in quota/retry waits is visible in metrics and logs.

## Step 3 — Bound output and retrieval-context size

### Objective

Reduce prompt processing and generation time without changing the overall architecture yet.

### Configuration

Create `ChatProperties` under the `chat` prefix rather than adding more unrelated settings to `FoundryProperties`:

```yaml
chat:
  orchestration-mode: agentic
  max-tool-rounds: 1
  retrieval-top-k: 4
  max-context-characters-per-paper: 800
  max-context-characters-total: 4000
  max-completion-tokens: 400
  temperature: 0.1
```

Every provider parameter must be capability-tested against the deployed `gpt-5.6-luna` endpoint. Some model families reject or reinterpret parameters such as `temperature` or completion-token controls.

### Implementation

1. Add a configurable completion-token limit to chat requests.
2. Keep `n=1` explicitly.
3. Reduce default retrieval from five papers to four for chat, while leaving the search API's independent top-K untouched.
4. Replace the fixed 1,200-character truncation with a total context budget.
5. Prefer a compact evidence object containing:
   - arXiv ID and URL;
   - title;
   - categories/date only when relevant;
   - a bounded abstract excerpt;
   - retrieval score.
6. Deduplicate papers across tool calls before serializing context.
7. On the final answer request, omit tool definitions and force an answer-only call after the first successful tool round. This reduces prompt tokens and prevents unnecessary second-round tool calls.
8. Keep a feature flag that restores multiple tool rounds for comparison.

### Tests

- Verify context never exceeds its configured aggregate bound.
- Verify the highest-ranked papers are retained when trimming.
- Verify citations refer only to retained evidence.
- Verify the final request contains no tool definitions in one-round mode.
- Verify a no-result request still produces a clear grounded response.

### Acceptance criteria

- Prompt and completion token counts decrease measurably.
- Answer quality on the fixed evaluation questions does not regress.
- No ordinary question performs more than two chat calls in legacy agentic mode.

## Step 4 — Add a one-chat-call pre-retrieval fast path

### Objective

Remove the remote tool-selection model call from ordinary RAG questions.

### Target flow

```text
User question
  |
  v
Local ChatRetrievalPlanner
  |-- exact arXiv ID detected --> PaperRepository lookup
  |-- clear metadata request --> PostgreSQL lexical/relational retrieval
  `-- ordinary research query --> semantic or hybrid retrieval
                                      |
                                      v
                              compact grounded context
                                      |
                                      v
                         one Foundry answer-generation call
```

### Why this is the main optimization

The application already knows that research questions must be grounded and already owns the retrieval implementations. Asking the remote model whether retrieval is needed adds an inference round trip without adding much value for normal single-question RAG.

Tool calling remains useful for genuinely agentic, multi-step work. It should be a fallback mode, not the default tax on every question.

### Implementation

1. Introduce `ChatRetrievalPlanner`, implemented locally with deterministic rules.
2. Detect exact arXiv IDs with the same validation used by `ChatCitation` and route directly to `paper_lookup`. This path needs no query embedding.
3. Detect explicit category/date constraints from structured request fields. Do not attempt broad natural-language date parsing in the first version.
4. Route ordinary free-text research questions to semantic retrieval initially.
5. Add a `hybrid_search` service that fuses semantic and PostgreSQL full-text results when evaluation shows better coverage.
6. Introduce `GroundedContextBuilder` to enforce evidence and token budgets independently of retrieval implementation.
7. Introduce an answer-only Foundry method that sends:
   - stable system instructions;
   - the user question;
   - compact evidence;
   - no tool definitions.
8. Keep the current tool-calling orchestration under:

   ```yaml
   chat:
     orchestration-mode: agentic
   ```

   Make the optimized path selectable as:

   ```yaml
   chat:
     orchestration-mode: pre-retrieval
   ```

9. After evaluation, make `pre-retrieval` the foundry-profile default and retain `agentic` for multi-hop experiments.
10. Return the same `GroundedChatResponse` shape so the existing UI remains compatible.

### Tests

- Exact-ID question: one chat call, zero embedding calls.
- Conceptual question: one query-embedding call and one chat call.
- Metadata-only question: local retrieval and one chat call.
- No-result question: one chat call with an explicit empty-evidence instruction.
- Verify the model cannot cite a paper absent from the returned citation collection.
- Run the same evaluation set through both orchestration modes and compare citation precision and answer usefulness.

### Acceptance criteria

- At least 90% of the evaluation set completes with exactly one chat-model request.
- Ordinary questions retain correct citations.
- Measured p50 completion time improves materially over the agentic baseline.

## Step 5 — Stream the final answer

### Objective

Show useful output as soon as the provider returns the first token.

Streaming primarily improves perceived latency. It does not necessarily reduce the time required to generate the complete answer.

### API design

Preserve `POST /api/chat` for compatibility and add:

```text
POST /api/chat/stream
Content-Type: text/event-stream
```

Suggested events:

```text
event: retrieval
data: {"status":"started"}

event: citations
data: [{"arxivId":"2401.00001","title":"...","url":"https://arxiv.org/abs/2401.00001"}]

event: delta
data: {"text":"partial answer text"}

event: complete
data: {"model":"gpt-5.6-luna","toolsUsed":["semantic_search"]}

event: error
data: {"message":"...","retryable":true}
```

### Implementation

1. Add a streaming method to `FoundryClient` using `stream=true` and an asynchronous Java HTTP body handler.
2. Parse SSE frames incrementally and emit only assistant text deltas.
3. Implement the streaming endpoint with Spring MVC `SseEmitter` or a `StreamingResponseBody`.
4. Emit citations immediately after local retrieval, before answer generation completes.
5. Update the browser to consume the response stream and append deltas to the active assistant message.
6. Add `AbortController` so navigation, cancellation, or a replacement question closes the provider request.
7. Persist the final assembled answer in the client transcript only after the completion event.
8. Measure both TTFT and TTLT.
9. Implement streaming after pre-retrieval; streaming tool-call arguments from the first model round is significantly more complex and offers less value once normal questions use local pre-retrieval.

### Tests

- Parser tests with SSE chunks split at arbitrary byte boundaries.
- Endpoint test for retrieval, citation, delta, complete, and error ordering.
- Cancellation test verifies the downstream request is interrupted.
- Browser test verifies escaped text and clickable citations remain safe.

### Acceptance criteria

- The user sees retrieval status immediately.
- The first answer token normally appears within the TTFT target.
- The non-streaming endpoint remains operational.

## Step 6 — Cache repeatable retrieval work

### Objective

Avoid repeated query embeddings and searches for identical or recently repeated questions.

### Implementation

1. Add Caffeine as a bounded in-memory cache; do not introduce Redis for a single local process.
2. Cache query embeddings by:
   - normalized query;
   - embedding deployment;
   - dimensions;
   - retrieval-document schema version.
3. Cache retrieval results by:
   - normalized query;
   - retrieval mode;
   - category/date filters;
   - top-K;
   - embedding model;
   - corpus generation/version.
4. Start with a 10-minute TTL and a maximum of 1,000 entries, then tune from observed hit rate and memory use.
5. Increment a corpus generation value when imports or embedding updates complete. Include it in retrieval-cache keys so stale results naturally stop matching.
6. Do not cache final generated answers initially. Retrieval caching is easier to invalidate and preserves fresh model behavior.
7. Never cache errors or empty results for a long period; use a short negative-cache TTL only if repeated misses become measurable.

### Tests

- Repeated requests call the embedding provider once.
- Different filters or model names cannot collide.
- Corpus generation changes invalidate retrieval entries.
- Cache size remains bounded.

### Acceptance criteria

- Repeated-query cache hits avoid the embedding call.
- Cache metrics show hit rate, load time, and eviction count.
- No stale citations are returned after corpus updates.

## Step 7 — Parallelize only independent operations

### Objective

Reduce wall-clock time where dependencies do not require sequential execution.

### Implementation

1. Execute semantic and lexical retrieval concurrently when hybrid mode is selected.
2. If agentic mode returns multiple independent tool calls in one round, execute them with a bounded virtual-thread executor and restore the original tool-call order before adding messages.
3. Bound parallel tool calls to three to protect database and provider quotas.
4. Consider speculative semantic retrieval in parallel with agentic tool selection only as an experiment. It can save embedding time when semantic search is selected, but wastes quota when it is not.
5. Do not parallelize the final answer call with retrieval; the answer depends on the evidence.

### Tests

- Use controllable test delays to prove parallel branches reduce wall-clock time.
- Verify output order remains deterministic.
- Verify one failed tool produces a bounded tool error without cancelling unrelated successful calls unless configured.

### Acceptance criteria

- Hybrid retrieval time approaches the slower branch rather than the sum of both branches.
- Concurrency remains bounded under load.

## Step 8 — Verify and tune PostgreSQL retrieval

### Objective

Ensure local work remains a small fraction of total chat latency.

### Investigation

1. Capture `EXPLAIN (ANALYZE, BUFFERS)` for representative vector queries.
2. Verify `idx_paper_embeddings_embedding_cosine` is used where appropriate.
3. Measure separately:
   - vector literal construction;
   - JDBC query execution;
   - HNSW search;
   - paper hydration.
4. Run `ANALYZE papers` and `ANALYZE paper_embeddings` after bulk ingestion.
5. Check whether category/date filtering causes insufficient HNSW candidates. Tune pgvector iterative scan or `hnsw.ef_search` only after measuring recall and latency.
6. If paper hydration is material, fetch only the response/context columns rather than complete JPA entities.
7. Do not add indexes speculatively. Each index increases storage and import/update cost.

### Acceptance criteria

- Local retrieval is within the 150 ms p95 target, excluding remote query embedding.
- Query plans and index use are captured in the benchmark notes.

## Step 9 — Evaluate model and deployment choices

### Objective

Reduce the remaining provider latency after application-level inefficiencies are removed.

### Implementation

1. Use Foundry model benchmarks to shortlist a faster tool-capable/chat model available in Sweden Central.
2. Deploy the candidate under a new deployment name; do not replace the existing deployment in place.
3. Run an A/B test using identical prompts, evidence, output limits, and concurrency.
4. Compare:
   - TTFT p50/p95;
   - TTLT p50/p95;
   - generated tokens per second;
   - prompt/completion/reasoning tokens;
   - citation correctness;
   - answer usefulness;
   - cost per request.
5. Prefer a smaller/mini model if it satisfies the quality gate. Microsoft specifically recommends evaluating a mini model when latency is important, but actual availability and behavior must be checked in this Foundry resource.
6. Compare Standard/GlobalStandard behavior with provisioned throughput only if stable p95 latency justifies the extra cost.
7. Preserve the current model as a feature-flagged fallback.

### Embedding model warning

A faster embedding deployment is a separate project. Query and document vectors must use the same model and dimension. A switch requires side-by-side vectors or a full re-embedding, followed by retrieval-quality comparison. Do not mix vectors from `text-embedding-3-large` and another embedding model.

### Acceptance criteria

- A model switch occurs only if it improves measured latency while passing citation and answer-quality gates.
- Rollback requires only a configuration change.

## Step 10 — Build a repeatable latency and quality evaluation

### Objective

Prevent a fast but less trustworthy implementation from being accepted.

### Evaluation set

Create at least 30 version-controlled questions across:

- semantic/conceptual discovery;
- exact arXiv-ID lookup;
- title/keyword lookup;
- category and date filters;
- comparison across two papers;
- no-evidence questions;
- repeated questions for cache measurement;
- one or two genuine multi-step questions for agentic fallback.

For each question, store expected paper IDs or an explicit no-evidence expectation. Do not require one exact natural-language answer.

### Run matrix

| Variant | Purpose |
| --- | --- |
| A: current agentic, non-streaming | Baseline |
| B: bounded agentic | Quantify prompt/output reductions |
| C: pre-retrieval, non-streaming | Quantify removal of first chat call |
| D: pre-retrieval, streaming | Quantify perceived latency |
| E: pre-retrieval, streaming, cache | Quantify warm/repeated-query behavior |

Capture warm and cold results separately and report p50, p90, and p95 rather than only averages.

### Quality gates

- Every cited arXiv ID was present in retrieved evidence.
- Citation URLs correspond to the cited IDs.
- No-evidence questions do not invent papers.
- Expected-paper recall does not regress beyond the agreed tolerance.
- Answers remain concise and useful.

## Recommended delivery tickets

### Ticket 1 — Observability baseline

Implement Step 1 and run the baseline matrix. No orchestration changes.

### Ticket 2 — Interactive quota isolation

Implement Step 2. Verify chat during a bounded embedding job.

### Ticket 3 — Bounded agentic path

Implement Step 3 behind configuration and compare token/latency results.

### Ticket 4 — Pre-retrieval orchestration

Implement Step 4 behind `chat.orchestration-mode` and make it the candidate default.

### Ticket 5 — Streaming UI

Implement Step 5 after the one-call path is stable.

### Ticket 6 — Cache and parallel hybrid retrieval

Implement Steps 6 and 7 with bounded resources and invalidation.

### Ticket 7 — Database and Foundry A/B tuning

Implement Steps 8 and 9 only where measurements justify changes.

### Ticket 8 — Performance regression suite

Automate Step 10 and publish results with each major orchestration/model change.

## Changes that will not, by themselves, solve the problem

- Lowering the 120-second request timeout only fails slow requests sooner; it does not make successful requests faster.
- Adding application threads does not remove sequential model dependencies.
- Streaming improves time to first visible output but usually does not reduce full completion time.
- Raising quota does not improve an idle request unless telemetry shows throttling or quota waits.
- Switching only the query embedding model invalidates vector similarity.
- Removing retrieval would be fast but would defeat grounded RAG and citation correctness.

## Expected outcome by phase

| Completed through | Expected effect |
| --- | --- |
| Step 1 | Latency becomes explainable; no material speedup yet |
| Steps 2–3 | Fewer hidden waits and fewer processed/generated tokens |
| Step 4 | Largest likely reduction in actual end-to-end time |
| Step 5 | Largest improvement in perceived responsiveness |
| Steps 6–7 | Faster repeated and hybrid/multi-tool requests |
| Steps 8–9 | Evidence-based database/provider tuning |
| Step 10 | Ongoing protection against speed and quality regressions |

## Recommended first implementation slice

The first coding slice should contain Steps 1–4, guarded by configuration:

```yaml
chat:
  orchestration-mode: pre-retrieval
  max-tool-rounds: 1
  retrieval-top-k: 4
  max-context-characters-per-paper: 800
  max-context-characters-total: 4000
  max-completion-tokens: 400
```

This slice provides real latency measurements, eliminates avoidable local quota coupling, bounds token work, and removes one chat-model request from the normal path. Streaming should follow as a separate slice because it changes the HTTP and browser interaction model and needs dedicated cancellation/error tests.

## References

- [Microsoft Foundry: Azure OpenAI performance and latency](https://learn.microsoft.com/en-us/azure/foundry/openai/how-to/latency) — explains TTFT, time between tokens, total latency, token effects, and recommended Azure Monitor metrics.
- [Microsoft Foundry REST API: Chat](https://learn.microsoft.com/en-us/azure/foundry/openai/latest) — documents chat request controls, parallel tool calls, streaming, usage fields, and prompt/completion token details.
- [Microsoft Foundry model benchmarks](https://learn.microsoft.com/en-us/azure/foundry/concepts/model-benchmarks) — defines TTFT, latency percentiles, and generated-token throughput for model comparison.
- [Microsoft Foundry model monitoring](https://learn.microsoft.com/en-us/azure/ai-services/openai/how-to/monitoring) — describes the provider-side latency and token dashboards.

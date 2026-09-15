# Service objectives and regression policy

**Status:** active working objectives; calibrate with live baseline data

The application records distributions through Micrometer and the bounded benchmark command. These initial objectives separate local work from provider work so a slow model is not misdiagnosed as a PostgreSQL problem.

| Signal | Working objective | Measurement |
|---|---:|---|
| Warm lexical search p95 | < 250 ms | `rag.search` diagnostics / load test |
| Warm semantic or hybrid search p95 | < 1.5 s | includes remote query embedding |
| Chat TTFT p50 / p95 | < 4 s / < 8 s | SSE `metrics` and `rag.chat.ttft` |
| Chat total p95 | < 15 s | `rag.chat.latency` |
| Deterministic no-evidence p95 | < 300 ms | benchmark no-evidence cases |
| Citation URL validity | 100% | benchmark `citationValidityRate` |
| Provider error rate | < 1% outside incidents | Foundry counters |
| UI token-to-DOM update | < 100 ms | Playwright trace/browser profiling |

## Regression gate

Use at least 30 representative cases and compare cold and warm runs. Reject a change if retrieval Recall@k or valid-citation rate declines by more than two absolute percentage points unless the change explicitly fixes a higher-severity correctness problem. Compare matching corpus generation, embedding model/dimension, prompt/retrieval versions, and deployment; otherwise the runs are not equivalent.

The live command is manual because it spends Foundry quota:

```powershell
java -jar target\arxiv-rag-0.0.1-SNAPSHOT.jar `
  --spring.profiles.active=foundry `
  chat-benchmark benchmarks\chat-evaluation.jsonl `
  --max-questions=30 --cache=both --variant=streaming
```

Results are written to `benchmarks/results/chat-<timestamp>.json` with p50/p90/p95, TTFT, tokens, cost when prices are configured, Recall@k, reciprocal rank, citation validity, commit, versions, and corpus generation.

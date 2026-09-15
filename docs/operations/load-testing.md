# Load and failure testing

**Status:** runnable local harness; destructive/provider-failure scenarios require an isolated environment

Install k6, run the application with a representative already-embedded corpus, then execute:

```powershell
$env:BASE_URL = "http://127.0.0.1:8080/api/v1"
$env:SEARCH_VUS = "5"
$env:CHAT_VUS = "1"
$env:DURATION = "30s"
k6 run .\ops\load\k6-search-chat.js
```

Repeat with 1, 5, 10, and 25 chat users only while remaining inside the configured Foundry RPM/TPM budget. The HTTP harness measures the complete synchronous response; use the benchmark's streaming variant for TTFT.

Failure tests belong against a provider stub and disposable PostgreSQL volume:

1. Return slow embedding, slow first token, 429 with Retry-After, 500, disconnect, and malformed SSE fixtures.
2. Assert bounded retries, circuit opening, lexical fallback for interactive embedding timeout, one terminal SSE event, and no retries for bulk data corruption.
3. Stop the application during a leased job, let the lease expire, restart, and resume the `INTERRUPTED` job.
4. Stop PostgreSQL and assert liveness remains UP while readiness and operations report dependency failure.
5. Send SIGTERM/container stop while streams run and verify graceful completion within the configured platform deadline.

Do not run paid load tests from ordinary CI. The manual Foundry workflow enforces a maximum of five evaluation cases.

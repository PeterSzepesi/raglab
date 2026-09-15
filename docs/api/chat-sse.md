# Grounded chat SSE contract

**Status:** active — API v1

`POST /api/v1/chat/stream` accepts the same JSON body as synchronous chat and returns `text/event-stream`. The client must parse complete SSE frames, not network chunks: a frame can be split across reads and one read can contain several frames.

## Event order

The normal evidence-bearing path emits:

```text
retrieval -> citations -> delta* -> metrics -> complete
```

- `retrieval`: `{ "status": "started" }`.
- `citations`: the complete server-authoritative citation array. URLs are constructed locally from validated arXiv identifiers.
- `delta`: `{ "text": "..." }`; zero or more model text fragments.
- `metrics`: terminal diagnostics including request ID, total/stage milliseconds, token counts, cache state, and grounding state.
- `complete`: the authoritative final answer, model alias, tool names, states, request ID, and optional debug timings.

The deterministic no-evidence path emits `retrieval`, an empty `citations` list, one local `delta`, `metrics`, and `complete`. It makes no chat-model call.

## Errors and cancellation

Validation/admission errors that occur before the stream is established use the normal `application/problem+json` contract. Once streaming starts, a terminal `error` event contains `{ "message", "retryable" }`; the server then closes the emitter. No `complete` event follows an `error` event.

Closing the browser request or invoking `AbortController.abort()` cancels the submitted virtual-thread task. The backend releases its chat-admission permit in `finally`. A client must never automatically retry chat generation because doing so can duplicate paid work; it may offer an explicit Retry action.

## Security and rendering

The stream never contains credentials, hidden prompts, SQL, or raw provider response bodies. Client rendering uses text nodes for model/user content. Only URLs supplied in the authoritative citation objects may become clickable links.

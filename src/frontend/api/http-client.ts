import type { ProblemDetail } from "./contracts";

export class ApiError extends Error {
  constructor(public readonly status: number, public readonly problem: ProblemDetail,
              public readonly requestId: string | null) {
    super(problem.detail ?? problem.error ?? `Request failed with status ${status}`);
  }
}

export class ApiClient {
  constructor(private readonly base = "/api/v1", private readonly defaultTimeoutMs = 15_000) {}

  get<T>(path: string, options: RequestInit = {}): Promise<T> { return this.json<T>(path, options); }
  post<T>(path: string, body?: unknown, options: RequestInit = {}, timeoutMs?: number): Promise<T> {
    return this.json<T>(path, { ...options, method: "POST", body: body instanceof FormData ? body : JSON.stringify(body ?? {}) }, timeoutMs);
  }
  delete<T>(path: string, options: RequestInit = {}): Promise<T> { return this.json<T>(path, { ...options, method: "DELETE" }); }

  async json<T>(path: string, options: RequestInit = {}, timeoutMs = this.defaultTimeoutMs): Promise<T> {
    const timeout = new AbortController();
    const timer = window.setTimeout(() => timeout.abort("deadline exceeded"), timeoutMs);
    const signal = options.signal ? AbortSignal.any([options.signal, timeout.signal]) : timeout.signal;
    const headers = new Headers(options.headers);
    headers.set("Accept", "application/json");
    if (options.body && !(options.body instanceof FormData)) headers.set("Content-Type", "application/json");
    try {
      const response = await fetch(this.base + path, { ...options, headers, signal });
      const requestId = response.headers.get("X-Request-ID");
      if (!response.ok) {
        let problem: ProblemDetail = { status: response.status, detail: response.statusText };
        try { problem = await response.json() as ProblemDetail; } catch { /* retain safe status */ }
        throw new ApiError(response.status, problem, requestId ?? problem.requestId ?? null);
      }
      if (response.status === 204) return undefined as T;
      return await response.json() as T;
    } finally {
      window.clearTimeout(timer);
    }
  }
}

export const api = new ApiClient();

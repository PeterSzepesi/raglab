const ARXIV_ID = /^(?:\d{4}\.\d{4,5}|[A-Za-z][A-Za-z0-9.-]*\/\d{7})(?:v\d+)?$/;

export function safeArxivUrl(arxivId: string, candidate?: string | null): string | null {
  if (!ARXIV_ID.test(arxivId)) return null;
  try {
    const url = new URL(candidate ?? `https://arxiv.org/abs/${arxivId}`);
    if (url.protocol !== "https:" || url.hostname !== "arxiv.org" || url.username || url.password || url.port) return null;
    if (url.pathname !== `/abs/${arxivId}`) return null;
    return url.href;
  } catch { return null; }
}

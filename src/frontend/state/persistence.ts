const PREFIX = "arxiv-rag:";

export function readVersioned<T>(key: string, version: number, fallback: T): T {
  try {
    const parsed = JSON.parse(localStorage.getItem(PREFIX + key) ?? "null") as { version: number; value: T } | null;
    return parsed?.version === version ? parsed.value : fallback;
  } catch { return fallback; }
}

export function writeVersioned<T>(key: string, version: number, value: T): void {
  localStorage.setItem(PREFIX + key, JSON.stringify({ version, value }));
}

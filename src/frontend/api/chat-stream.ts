import { ApiError } from "./http-client";
import type { ChatRequest, ProblemDetail } from "./contracts";

export type SseFrame = { event: string; data: unknown };

export function parseSseFrames(input: string): { frames: SseFrame[]; remainder: string } {
  let buffer = input.replaceAll("\r\n", "\n");
  const frames: SseFrame[] = [];
  for (let boundary; (boundary = buffer.indexOf("\n\n")) >= 0;) {
    const raw = buffer.slice(0, boundary);
    buffer = buffer.slice(boundary + 2);
    if (!raw.trim() || raw.startsWith(":")) continue;
    let event = "message";
    const data: string[] = [];
    for (const line of raw.split("\n")) {
      if (line.startsWith("event:")) event = line.slice(6).trim();
      if (line.startsWith("data:")) data.push(line.slice(5).trimStart());
    }
    if (!data.length) continue;
    try { frames.push({ event, data: JSON.parse(data.join("\n")) as unknown }); }
    catch { throw new Error(`Malformed ${event} SSE event`); }
  }
  return { frames, remainder: buffer };
}

export async function streamChat(request: ChatRequest, signal: AbortSignal,
                                 onFrame: (frame: SseFrame) => void): Promise<void> {
  const response = await fetch("/api/v1/chat/stream", {
    method: "POST", headers: { Accept: "text/event-stream", "Content-Type": "application/json" },
    body: JSON.stringify(request), signal
  });
  if (!response.ok || !response.body) {
    let problem: ProblemDetail = { status: response.status, detail: response.statusText };
    try { problem = await response.json() as ProblemDetail; } catch { /* safe status */ }
    throw new ApiError(response.status, problem, response.headers.get("X-Request-ID"));
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let remainder = "";
  while (true) {
    const { value, done } = await reader.read();
    const parsed = parseSseFrames(remainder + decoder.decode(value ?? new Uint8Array(), { stream: !done }));
    remainder = parsed.remainder;
    parsed.frames.forEach(onFrame);
    if (done) break;
  }
  if (remainder.trim()) {
    const parsed = parseSseFrames(remainder + "\n\n");
    parsed.frames.forEach(onFrame);
  }
}

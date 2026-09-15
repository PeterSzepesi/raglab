import { describe, expect, it } from "vitest";
import { parseSseFrames } from "./chat-stream";

describe("parseSseFrames", () => {
  it("handles split, multiple, and CRLF frames", () => {
    const first = parseSseFrames("event: delta\r\ndata: {\"text\":\"hel");
    expect(first.frames).toEqual([]);
    const second = parseSseFrames(first.remainder + "lo\"}\r\n\r\nevent: complete\ndata: {\"answer\":\"hello\"}\n\n");
    expect(second.frames).toEqual([
      { event: "delta", data: { text: "hello" } },
      { event: "complete", data: { answer: "hello" } }
    ]);
    expect(second.remainder).toBe("");
  });

  it("rejects malformed JSON rather than silently corrupting a chat", () => {
    expect(() => parseSseFrames("event: delta\ndata: nope\n\n")).toThrow("Malformed delta SSE event");
  });
});

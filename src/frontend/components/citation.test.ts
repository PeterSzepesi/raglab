import { describe, expect, it } from "vitest";
import { safeArxivUrl } from "./citation";

describe("safeArxivUrl", () => {
  it("accepts canonical modern and legacy arXiv links", () => {
    expect(safeArxivUrl("2401.00001")).toBe("https://arxiv.org/abs/2401.00001");
    expect(safeArxivUrl("hep-th/9901001v2")).toBe("https://arxiv.org/abs/hep-th/9901001v2");
  });
  it("rejects off-site, credentialed, and mismatched links", () => {
    expect(safeArxivUrl("2401.00001", "https://evil.example/abs/2401.00001")).toBeNull();
    expect(safeArxivUrl("2401.00001", "https://arxiv.org/abs/2401.00002")).toBeNull();
    expect(safeArxivUrl("javascript:alert(1)")).toBeNull();
  });
});

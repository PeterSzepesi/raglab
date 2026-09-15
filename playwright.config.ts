import { defineConfig } from "@playwright/test";

process.env.PLAYWRIGHT_BROWSERS_PATH ??= ".playwright";

export default defineConfig({
  testDir: "src/frontend/e2e",
  use: { baseURL: "http://arxiv-rag.local" }
});

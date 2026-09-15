import { expect, test } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";

test.beforeEach(async ({ page }) => {
  await page.route("http://arxiv-rag.local/", async route => route.fulfill({ status: 200, contentType: "text/html",
    body: await readFile(resolve("src/main/resources/static/index.html"), "utf8") }));
  await page.route("http://arxiv-rag.local/assets/app.js", async route => route.fulfill({ status: 200, contentType: "text/javascript",
    body: await readFile(resolve("src/main/resources/static/assets/app.js"), "utf8") }));
  await page.route("http://arxiv-rag.local/assets/app.css", async route => route.fulfill({ status: 200, contentType: "text/css",
    body: await readFile(resolve("src/main/resources/static/assets/app.css"), "utf8") }));
  await page.route("**/api/v1/**", async route => {
    const path = new URL(route.request().url()).pathname;
    const runtime = { applicationName: "arxiv-rag", configuredProfile: "test", activeProfiles: ["test"],
      features: ["Import", "Papers", "Search", "Chat", "Vector Stores", "Benchmarks"].map(name => ({ name, enabled: true })),
      facts: { provider: "OFFLINE", endpointHost: "not-configured", chatDeployment: "test-chat", embeddingDeployment: "test-embedding",
        embeddingDimensions: 1536, authenticationMode: "api-key", credentialConfigured: false, adminProtection: "LOCAL_ONLY",
        retrievalPolicyVersion: "retrieval-v1", promptVersion: "grounded-v1", answerCacheEnabled: false } };
    const health = { status: "UP", applicationName: "arxiv-rag", checkedAt: new Date().toISOString(), message: "Backend services are reachable.", activeProfiles: ["test"],
      database: { reachable: true, product: "H2", version: "test", statistics: { available: false,
        pgStatStatementsInstalled: false, databaseBytes: null, estimatedDeadTuples: null,
        normalizedStatementP95Milliseconds: null, slowStatementCount: null, topStatements: [] } },
      vectorStore: { status: "NOT_APPLICABLE", type: "memory" },
      provider: { provider: "OFFLINE", status: "LOCAL", chatDeployment: "test", embeddingDeployment: "test",
        circuitState: "NOT_APPLICABLE", circuits: {}, operations: { lastSuccess: null, lastFailure: null } },
      corpus: { papers: 0, vectorRows: 0, checkpointedPapers: 0, vectorCoveragePercent: 0 }, admission: { activeChats: 0, availableChatPermits: 16, maximumChats: 16 },
      caches: { retrieval: { hits: 0, misses: 0, hitRate: 1, evictions: 0, loadFailures: 0 }, answers: { hits: 0, misses: 0, hitRate: 1, evictions: 0, loadFailures: 0 }, queryEmbeddings: { hits: 0, misses: 0, hitRate: 1, evictions: 0, loadFailures: 0 } }, latestEmbeddingJob: null };
    const payload = path.endsWith("/capabilities") ? runtime : path.endsWith("/health") ? health
      : path.includes("/papers") ? { items: [], nextCursor: null, hasMore: false }
      : path.endsWith("/embeddings/status") ? { papers: 0, checkpoints: 0, checkpointedPapers: 0, coveragePercent: 0,
        embeddingModel: "test", embeddingDimension: 1536, vectorRows: 0, vectorCoveragePercent: 0,
        jobStatus: "IDLE", jobTotalPapers: 0, jobPapersScanned: 0, jobEmbedded: 0, jobSkipped: 0,
        jobFailures: 0, jobStartedAt: null, indexDiagnostics: { tableBytes: null, indexBytes: null, definitions: [] },
        providerCapacity: { chatWaiting: 0, queryEmbeddingWaiting: 0, bulkEmbeddingWaiting: 0,
          reservedInteractiveRequestsPerMinute: 0, reservedInteractiveTokensPerMinute: 0 } }
      : [];
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(payload) });
  });
});

test("primary navigation is keyboard usable on a narrow viewport", async ({ page }) => {
  await page.setViewportSize({ width: 720, height: 900 });
  await page.goto("http://arxiv-rag.local/");
  const first = page.locator('a[href="#/system-status"]');
  await first.focus();
  await page.keyboard.press("Tab");
  await page.keyboard.press("Enter");
  await expect(page).toHaveURL(/#\/import$/);
  await expect(page.getByRole("heading", { name: "Import", level: 2 })).toBeVisible();
});

test("all enabled tabs mount and the shell has no automated WCAG A/AA violations", async ({ page }) => {
  await page.goto("http://arxiv-rag.local/");
  await expect(page.getByRole("heading", { name: "System Status", level: 1 })).toBeVisible();
  const scan = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze();
  expect(scan.violations).toEqual([]);
  for (const route of ["import", "papers", "search", "chat", "embeddings", "benchmarks", "settings"]) {
    await page.locator(`a[href="#/${route}"]`).click();
    await expect(page.locator("#page-content h2")).toBeVisible();
  }
});

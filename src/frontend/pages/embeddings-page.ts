import type { EmbeddingJob, EmbeddingStatus } from "../api/contracts";
import { badge, button, field, h, metric, metricGrid, section } from "../components/dom";
import { empty, errorState, loading } from "../components/states";
import type { Page } from "./page";

export const embeddingsPage: Page = { title: "Embeddings & Indexes", feature: "Vector Stores", mount(root, context) {
  let stopped = false; const body = h("div");
  const refresh = async () => {
    try {
      const [status, jobs] = await Promise.all([context.api.get<EmbeddingStatus>("/embeddings/status"),
        context.api.get<EmbeddingJob[]>("/embeddings/jobs")]);
      if (stopped) return;
      const missing = Math.max(0, status.papers - status.checkpointedPapers);
      const progress = status.jobTotalPapers ? status.jobPapersScanned / status.jobTotalPapers : 0;
      const elapsedSeconds = status.jobStartedAt == null ? 0 : Math.max(1,
        (Date.now() - new Date(status.jobStartedAt).getTime()) / 1000);
      const rate = elapsedSeconds ? status.jobPapersScanned / elapsedSeconds : 0;
      const eta = rate > 0 ? Math.max(0, status.jobTotalPapers - status.jobPapersScanned) / rate : null;
      body.replaceChildren(
        h("div", { class: "health-panel" }, h("div", {}, badge(status.jobStatus, status.jobStatus === "FAILED" ? "error" : ["RUNNING", "WAITING_FOR_PROVIDER_CAPACITY"].includes(status.jobStatus) ? "working" : "ok"),
          h("h3", {}, `${status.jobPapersScanned}/${status.jobTotalPapers} papers scanned`),
          h("progress", { max: 1, value: progress, ariaLabel: "Embedding progress" }),
          h("small", {}, `${rate.toFixed(1)} papers/s${eta == null ? "" : ` · ETA ${duration(eta)}`}`)), button("Refresh", refresh)),
        metricGrid(metric("Papers", status.papers), metric("Current embeddings", status.checkpointedPapers),
          metric("Missing/stale", missing), metric("Vector rows", status.vectorRows),
          metric("Coverage", `${status.vectorCoveragePercent.toFixed(1)}%`), metric("Dimensions", status.embeddingDimension)),
        h("div", { class: "config-grid" },
          h("div", { class: "config-item" }, h("span", {}, "Embedding model"), h("strong", {}, status.embeddingModel)),
          h("div", { class: "config-item" }, h("span", {}, "Retrieval document"), h("strong", {}, status.retrievalDocumentVersion)),
          h("div", { class: "config-item" }, h("span", {}, "Index"), h("strong", {}, status.indexVersion)),
          h("div", { class: "config-item" }, h("span", {}, "Provider queue"), h("strong", {},
            `${status.providerCapacity.bulkEmbeddingWaiting} bulk / ${status.providerCapacity.queryEmbeddingWaiting + status.providerCapacity.chatWaiting} interactive waiting`)),
          h("div", { class: "config-item" }, h("span", {}, "Vector table size"), h("strong", {}, bytes(status.indexDiagnostics.tableBytes))),
          h("div", { class: "config-item" }, h("span", {}, "Vector indexes"), h("strong", {}, bytes(status.indexDiagnostics.indexBytes)))),
        controls(), h("h3", {}, "Recent durable jobs"),
        jobs.length ? h("div", { class: "result-list" }, ...jobs.map(jobCard)) : empty("No embedding jobs yet."));
    } catch (error) { if (!stopped) body.replaceChildren(errorState(error, refresh)); }
  };
  const controls = () => {
    const max = h("input", { type: "number", min: 1, max: 50_000, value: 100 });
    const form = h("form", { class: "job-controls" }, field("Maximum papers (only changed content is sent)", max),
      h("button", { class: "primary-button", type: "submit" }, "Embed changed"),
      h("small", {}, "Foundry cost depends on uncached changed documents; unchanged checkpoints are skipped."));
    form.addEventListener("submit", async event => { event.preventDefault();
      const maximum = Number(max.value);
      if (!window.confirm(`Embed changed content for at most ${maximum.toLocaleString()} papers? Unchanged documents are free of model calls; changed documents consume Foundry embedding quota and cost.`)) return;
      try { await context.api.post("/embeddings/jobs", { maxPapers: maximum, dryRun: false }); await refresh(); }
      catch (error) { context.reportError(error); }
    }); return form;
  };
  const jobCard = (job: EmbeddingJob) => h("article", { class: "result-card" },
    h("div", { class: "card-heading" }, badge(job.status, job.status === "FAILED" ? "error" : ["RUNNING", "WAITING_FOR_PROVIDER_CAPACITY"].includes(job.status) ? "working" : "ok"),
      h("strong", {}, job.jobId)), h("span", {}, `${job.papersScanned}/${job.totalPapers} scanned · ${job.embedded} embedded · ${job.skipped} skipped · ${job.providerRequests} provider calls`),
    job.lastError ? h("p", { class: "error-copy" }, job.lastError) : null,
    h("div", { class: "chat-actions" }, ["RUNNING", "QUEUED", "WAITING_FOR_PROVIDER_CAPACITY"].includes(job.status)
      ? button("Cancel", () => void context.api.delete(`/embeddings/jobs/${job.jobId}`).then(refresh), true) : null,
      ["FAILED", "CANCELLED", "INTERRUPTED"].includes(job.status)
        ? button("Resume", () => void context.api.post(`/embeddings/jobs/${job.jobId}/resume`).then(refresh), true) : null));
  root.replaceChildren(section("Embeddings & Indexes", "Current coverage, version facts, and safe durable job controls.", body));
  body.replaceChildren(loading("Loading embedding state…")); void refresh(); const timer = window.setInterval(refresh, 4000);
  return () => { stopped = true; window.clearInterval(timer); };
}};

function bytes(value: number | null): string {
  if (value === null) return "Unavailable for this database";
  return new Intl.NumberFormat(undefined, { style: "unit", unit: "megabyte", maximumFractionDigits: 1 })
    .format(value / 1_048_576);
}

function duration(seconds: number): string {
  if (!Number.isFinite(seconds)) return "unknown";
  if (seconds < 60) return `${Math.ceil(seconds)}s`;
  return `${Math.floor(seconds / 60)}m ${Math.ceil(seconds % 60)}s`;
}

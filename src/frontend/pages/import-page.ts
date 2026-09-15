import type { ImportJob, ImportPreview } from "../api/contracts";
import { badge, button, field, h, metric, metricGrid, section } from "../components/dom";
import { empty, errorState, loading } from "../components/states";
import type { Page } from "./page";

export const importPage: Page = { title: "Import", feature: "Import", mount(root, context) {
  let stopped = false;
  const jobsHost = h("div");
  const resultHost = h("div");
  const path = h("input", { name: "sourcePath", type: "text", placeholder: "C:\\data\\arxiv-metadata-oai-snapshot.json", required: true });
  const categories = h("input", { name: "categories", type: "text", value: "cs.AI,cs.CL,cs.LG,cs.CV" });
  const fromDate = h("input", { name: "fromDate", type: "date", value: "2022-01-01" });
  const maxRecords = h("input", { name: "maxRecords", type: "number", min: 1, max: 1_000_000, value: 50_000 });
  const form = h("form", { class: "preview-form" }, field("Server-side JSONL path", path),
    field("Categories (comma separated)", categories), field("Submitted from", fromDate),
    field("Maximum accepted records", maxRecords),
    h("div", { class: "chat-actions" }, button("Dry-run preview"), button("Start durable import", undefined, true)));
  const values = () => ({ sourcePath: path.value, categories: categories.value.split(",").map(v => v.trim()).filter(Boolean),
    fromDate: fromDate.value || null, maxRecords: Number(maxRecords.value) });
  const actions = form.querySelectorAll<HTMLButtonElement>("button");
  actions[0]?.addEventListener("click", async () => {
    if (!form.reportValidity()) return; resultHost.replaceChildren(loading("Scanning without writing papers…"));
    try { const value = await context.api.post<ImportPreview>("/imports/preview", values(), {}, 120_000);
      const diagnostics = value.malformedLineSamples.length
        ? [h("p", { class: "safe-config-note" }, `Malformed sample lines: ${value.malformedLineSamples.join(", ")}`)]
        : [];
      resultHost.replaceChildren(metricGrid(metric("Read", value.recordsRead), metric("Accepted", value.accepted),
        metric("Filtered", value.filtered), metric("Invalid", value.invalidRecords),
        metric("Duplicates in file", value.duplicateInFile), metric("Already stored", value.alreadyStored)),
        ...diagnostics);
    } catch (error) { resultHost.replaceChildren(errorState(error)); }
  });
  actions[1]?.addEventListener("click", async () => {
    if (!form.reportValidity()) return;
    try { await context.api.post<ImportJob>("/imports/jobs", values()); await refreshJobs(); }
    catch (error) { resultHost.replaceChildren(errorState(error)); }
  });
  const upload = h("input", { type: "file", accept: ".jsonl,application/json" });
  const uploadForm = h("form", { class: "job-controls" }, field("Upload a JSONL slice (maximum 25 MiB)", upload),
    h("button", { type: "submit", class: "primary-button" }, "Upload and import"));
  uploadForm.addEventListener("submit", async event => {
    event.preventDefault(); const file = upload.files?.[0]; if (!file) return;
    const body = new FormData(); body.set("file", file); body.set("categories", categories.value);
    if (fromDate.value) body.set("fromDate", fromDate.value); body.set("maxRecords", maxRecords.value);
    try { await context.api.post<ImportJob>("/imports/uploads", body, {}, 120_000); await refreshJobs(); }
    catch (error) { resultHost.replaceChildren(errorState(error)); }
  });
  async function refreshJobs() {
    try {
      const jobs = await context.api.get<ImportJob[]>("/imports/jobs?limit=10");
      if (stopped) return;
      jobsHost.replaceChildren(jobs.length ? h("div", { class: "result-list" }, ...jobs.map(job => {
        const rate = Math.max(0, job.imported + job.updated);
        const elapsedSeconds = Math.max(1, (Date.now() - new Date(job.startedAt).getTime()) / 1000);
        const recordsPerSecond = job.recordsRead / elapsedSeconds;
        const remaining = job.maxRecords == null ? null : Math.max(0, job.maxRecords - job.accepted);
        const eta = remaining == null || recordsPerSecond <= 0 ? null : remaining / recordsPerSecond;
        return h("article", { class: "result-card" },
          h("div", { class: "card-heading" }, badge(job.status, job.status === "FAILED" ? "error" : job.status === "RUNNING" ? "working" : "ok"), h("strong", {}, job.sourcePath)),
          h("span", {}, `${job.recordsRead} read · ${rate} written · ${job.skipped} filtered · ${job.invalidRecords} invalid`),
          h("small", {}, `${recordsPerSecond.toFixed(1)} records/s${eta == null ? "" : ` · ETA ${duration(eta)}`}`),
          job.malformedLineSamples?.length ? h("small", {}, `Malformed sample lines: ${job.malformedLineSamples.join(", ")}`) : null,
          job.sourceSha256 ? h("small", { class: "hash-value" }, `SHA-256 ${job.sourceSha256}`) : null,
          job.lastError ? h("p", { class: "error-copy" }, job.lastError) : null,
          h("div", { class: "chat-actions" },
            ["RUNNING", "QUEUED"].includes(job.status) ? button("Cancel", () => void context.api.delete(`/imports/jobs/${job.jobId}`).then(refreshJobs), true) : null,
            ["FAILED", "CANCELLED", "INTERRUPTED"].includes(job.status) ? button("Resume", () => void context.api.post(`/imports/jobs/${job.jobId}/resume`).then(refreshJobs), true) : null));
      })) : empty("No import history yet."));
    } catch (error) { if (!stopped) jobsHost.replaceChildren(errorState(error, refreshJobs)); }
  }
  root.replaceChildren(section("Import", "Preview a local file, then run an idempotent durable import. Administrative access is required outside localhost.", form, uploadForm, resultHost, h("h3", {}, "Recent imports"), jobsHost));
  void refreshJobs(); const timer = window.setInterval(refreshJobs, 3000);
  return () => { stopped = true; window.clearInterval(timer); };
}};

function duration(seconds: number): string {
  if (!Number.isFinite(seconds)) return "unknown";
  if (seconds < 60) return `${Math.ceil(seconds)}s`;
  return `${Math.floor(seconds / 60)}m ${Math.ceil(seconds % 60)}s`;
}

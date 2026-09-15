import type { BenchmarkRun } from "../api/contracts";
import { badge, button, h, metric, metricGrid, section } from "../components/dom";
import { empty, errorState, loading } from "../components/states";
import type { Page } from "./page";

type Summary = { deployment: string; requests: number; latencyP50Ms: number; latencyP95Ms: number;
  ttftP50Ms: number; ttftP95Ms: number; searchP50Ms: number; searchP95Ms: number;
  meanRecallAtK: number; meanReciprocalRank: number; meanNdcg: number; citationValidityRate: number;
  totalPromptTokens: number; totalCompletionTokens: number; averageEstimatedCostUsd: number | null };
type CaseResult = { caseId: string; latencyMilliseconds: number; ttftMilliseconds: number;
  searchMilliseconds: number; recallAtK: number; normalizedDiscountedCumulativeGain: number;
  citationUrlsValid: boolean; unsupportedCitationCount: number; expectedPapersPresent: boolean;
  noEvidenceCorrect: boolean; inlineCitationsValid: boolean; citedArxivIds: string[] };
type Report = { createdAt: string; applicationCommit: string; promptVersion: string;
  retrievalPolicyVersion: string; embeddingModel: string; summaries: Summary[]; results: CaseResult[] };

export const benchmarksPage: Page = { title: "Benchmarks", feature: "Benchmarks", mount(root, context) {
  const body = h("div", {}, loading("Loading saved benchmark runs…"));
  let reports = new Map<string, Report>();

  const loadReport = async (name: string): Promise<Report> => {
    const cached = reports.get(name); if (cached) return cached;
    const value = await context.api.get<Report>(`/benchmarks/${encodeURIComponent(name)}`);
    reports.set(name, value); return value;
  };

  const render = async () => {
    try {
      const runs = await context.api.get<BenchmarkRun[]>("/benchmarks");
      if (!runs.length) {
        body.replaceChildren(empty("No benchmark JSON exists under benchmarks/results yet. Use the bounded CLI command in docs/operations/benchmarking.md."));
        return;
      }
      const left = select(runs); const right = select(runs); if (runs[1]) right.value = runs[1].name;
      const comparison = h("div", { class: "preview-result" }, empty("Choose two runs and compare them."));
      const compare = async () => {
        comparison.replaceChildren(loading("Comparing benchmark runs…"));
        try { comparison.replaceChildren(comparisonView(await loadReport(left.value), await loadReport(right.value))); }
        catch (error) { comparison.replaceChildren(errorState(error, compare)); }
      };
      const controls = h("div", { class: "job-controls" }, left, right, button("Compare runs", () => void compare()),
        h("small", {}, "Live Foundry execution remains a bounded CLI/manual-CI action so opening this page cannot spend tokens."));
      const cards = await Promise.all(runs.map(async run => runCard(run, await loadReport(run.name))));
      body.replaceChildren(controls, comparison, h("h3", {}, "Saved runs"), h("div", { class: "result-list" }, ...cards));
    } catch (error) { body.replaceChildren(errorState(error, render)); }
  };

  function runCard(run: BenchmarkRun, report: Report): HTMLElement {
    const summary = report.summaries[0];
    const failures = report.results.filter(value => !qualityPassed(value));
    const drill = h("div", { class: "hidden" });
    return h("article", { class: "result-card" },
      h("div", { class: "card-heading" }, h("strong", {}, run.name), badge(run.variant)),
      h("span", {}, `${new Date(run.modifiedAt).toLocaleString()} · commit ${run.applicationCommit || "working-tree"}`),
      summary ? metricGrid(metric("Search p95", `${summary.searchP95Ms ?? "n/a"} ms`),
        metric("TTFT p95", `${summary.ttftP95Ms} ms`), metric("Total p95", `${summary.latencyP95Ms} ms`),
        metric("Recall@k", percent(summary.meanRecallAtK)), metric("nDCG", percent(summary.meanNdcg ?? 0)),
        metric("Citation validity", percent(summary.citationValidityRate))) : null,
      h("div", { class: "chat-actions" },
        button(`${failures.length} failed case(s)`, () => {
          drill.classList.toggle("hidden");
          drill.replaceChildren(...(failures.length ? failures.map(failureCard) : [empty("All cases passed.")]));
        }, true),
        button("Export JSON", () => download(run.name, JSON.stringify(report, null, 2), "application/json"), true),
        button("Export CSV", () => download(run.name.replace(/\.json$/, ".csv"), csv(report), "text/csv"), true)),
      drill);
  }

  function comparisonView(left: Report, right: Report): HTMLElement {
    const a = left.summaries[0]; const b = right.summaries[0];
    if (!a || !b) return empty("One of the selected runs has no summary.");
    const rows: Array<[string, number, number, string]> = [
      ["Search p95", a.searchP95Ms ?? 0, b.searchP95Ms ?? 0, "ms"],
      ["TTFT p95", a.ttftP95Ms, b.ttftP95Ms, "ms"], ["Total p95", a.latencyP95Ms, b.latencyP95Ms, "ms"],
      ["Recall@k", a.meanRecallAtK, b.meanRecallAtK, "quality"],
      ["nDCG", a.meanNdcg ?? 0, b.meanNdcg ?? 0, "quality"],
      ["Citation validity", a.citationValidityRate, b.citationValidityRate, "quality"]
    ];
    return h("article", { class: "result-card" }, h("h3", {}, `${a.deployment} compared with ${b.deployment}`),
      ...rows.map(([label, first, second, unit]) => chartRow(label, first, second, unit)),
      h("small", {}, `Prompt ${left.promptVersion} / ${right.promptVersion} · retrieval ${left.retrievalPolicyVersion} / ${right.retrievalPolicyVersion}`));
  }

  function chartRow(label: string, first: number, second: number, unit: string): HTMLElement {
    const maximum = Math.max(first, second, 0.0001);
    const format = (value: number) => unit === "quality" ? percent(value) : `${value.toFixed(0)} ms`;
    return h("div", { class: "benchmark-row" }, h("strong", {}, label),
      h("div", { class: "benchmark-bar", style: `--value:${first / maximum}` }, format(first)),
      h("div", { class: "benchmark-bar comparison", style: `--value:${second / maximum}` }, format(second)));
  }

  root.replaceChildren(section("Benchmarks", "Compare saved cold/warm latency, retrieval quality, citation validity, tokens, and estimated cost.", body));
  void render(); return () => { reports = new Map(); };
}};

function select(runs: BenchmarkRun[]): HTMLSelectElement {
  return h("select", {}, ...runs.map(run => h("option", { value: run.name }, run.name)));
}
function qualityPassed(value: CaseResult): boolean {
  return value.expectedPapersPresent && value.noEvidenceCorrect && value.inlineCitationsValid
    && value.citationUrlsValid && value.unsupportedCitationCount === 0;
}
function failureCard(value: CaseResult): HTMLElement {
  return h("article", { class: "result-card" }, h("strong", {}, value.caseId),
    h("span", {}, `Recall ${percent(value.recallAtK)} · nDCG ${percent(value.normalizedDiscountedCumulativeGain ?? 0)} · ${value.latencyMilliseconds} ms`),
    h("small", {}, `Citations: ${value.citedArxivIds.join(", ") || "none"}`));
}
function percent(value: number): string { return `${(100 * (Number.isFinite(value) ? value : 0)).toFixed(1)}%`; }
function download(name: string, content: string, type: string): void {
  const url = URL.createObjectURL(new Blob([content], { type }));
  const link = h("a", { href: url, download: name }); link.click(); URL.revokeObjectURL(url);
}
function csv(report: Report): string {
  const columns: Array<keyof CaseResult> = ["caseId", "searchMilliseconds", "ttftMilliseconds",
    "latencyMilliseconds", "recallAtK", "normalizedDiscountedCumulativeGain", "citationUrlsValid",
    "unsupportedCitationCount", "expectedPapersPresent", "noEvidenceCorrect", "inlineCitationsValid"];
  return [columns.join(","), ...report.results.map(row => columns.map(column => JSON.stringify(row[column] ?? "")).join(","))].join("\n");
}

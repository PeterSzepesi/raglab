import type { Health } from "../api/contracts";
import { badge, button, h, metric, metricGrid, section } from "../components/dom";
import { errorState, loading } from "../components/states";
import type { Page } from "./page";

export const statusPage: Page = { title: "System Status", mount(root, context) {
  let cancelled = false;
  const load = async () => {
    root.replaceChildren(section("System Status", "Passive readiness and operational state; this view never spends Foundry tokens.", loading("Reading local dependency state…")));
    try {
      const value = await context.api.get<Health>("/health");
      if (cancelled) return;
      const tone = value.status === "UP" ? "ok" : value.status === "DOWN" ? "error" : "working";
      const cache = value.caches.retrieval;
      const success = value.provider.operations?.lastSuccess;
      const failure = value.provider.operations?.lastFailure;
      const dbStats = value.database.statistics;
      root.replaceChildren(section("System Status", "Passive readiness and operational state; this view never spends Foundry tokens.",
        h("div", { class: `health-panel ${tone}` }, h("div", {}, badge(value.status, tone), h("h3", {}, value.message),
          h("small", {}, `Checked ${new Date(value.checkedAt).toLocaleString()}`)), button("Refresh", load)),
        metricGrid(metric("PostgreSQL", value.database.reachable ? "Reachable" : "Down"),
          metric("pgvector", value.vectorStore.status), metric("Provider", value.provider.status),
          metric("Circuit", value.provider.circuitState), metric("Active chats", `${value.admission.activeChats}/${value.admission.maximumChats}`),
          metric("Active searches", `${value.admission.activeRetrievals}/${value.admission.maximumRetrievals}`),
          metric("Vector coverage", `${value.corpus.vectorCoveragePercent.toFixed(1)}%`)),
        h("div", { class: "config-grid" },
          h("div", { class: "config-item" }, h("span", {}, "Database"), h("strong", {}, `${value.database.product} ${value.database.version ?? ""}`)),
          h("div", { class: "config-item" }, h("span", {}, "Chat deployment"), h("strong", {}, value.provider.chatDeployment)),
          h("div", { class: "config-item" }, h("span", {}, "Last Foundry success"), h("strong", {}, success ? `${success.operation} · ${new Date(success.at).toLocaleString()}` : "No call observed")),
          h("div", { class: "config-item" }, h("span", {}, "Last Foundry failure"), h("strong", {}, failure ? `${failure.operation} · HTTP ${failure.statusCode ?? "network"}` : "None observed"),
            failure?.requestId ? h("div", { class: "chat-actions" }, h("small", {}, `Request ID: ${failure.requestId}`),
              button("Copy", () => void navigator.clipboard.writeText(failure.requestId ?? ""), true)) : null),
          h("div", { class: "config-item" }, h("span", {}, "Retrieval-cache hit rate"), h("strong", {}, `${(cache.hitRate * 100).toFixed(1)}%`)),
          h("div", { class: "config-item" }, h("span", {}, "Query statistics"), h("strong", {}, dbStats.pgStatStatementsInstalled ? "pg_stat_statements active" : "Unavailable"),
            dbStats.normalizedStatementP95Milliseconds == null ? null : h("small", {}, `Statement mean-time p95 ${dbStats.normalizedStatementP95Milliseconds.toFixed(1)} ms · ${dbStats.slowStatementCount ?? 0} slow`)),
          h("div", { class: "config-item" }, h("span", {}, "Database maintenance"), h("strong", {}, dbStats.estimatedDeadTuples == null ? "Unavailable" : `${dbStats.estimatedDeadTuples.toLocaleString()} estimated dead tuples`),
            dbStats.databaseBytes == null ? null : h("small", {}, `${(dbStats.databaseBytes / 1_048_576).toFixed(1)} MiB database size`)),
          h("div", { class: "config-item" }, h("span", {}, "Latest embedding job"), h("strong", {}, value.latestEmbeddingJob?.status ?? "None")))));
    } catch (error) { if (!cancelled) root.replaceChildren(section("System Status", "Dependency state", errorState(error, load))); }
  };
  void load();
  return () => { cancelled = true; };
}};

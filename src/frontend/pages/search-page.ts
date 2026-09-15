import type { SearchResponse, SearchResult } from "../api/contracts";
import { badge, button, externalLink, field, h, section } from "../components/dom";
import { empty, errorState, loading } from "../components/states";
import { safeArxivUrl } from "../components/citation";
import type { Page } from "./page";

export const searchPage: Page = { title: "Search", feature: "Search", mount(root, context) {
  let controller: AbortController | null = null;
  const output = h("aside", { class: "preview-result" }, empty("Enter a query to search the indexed corpus."));
  const query = h("input", { name: "query", required: true, placeholder: "papers about neural retrieval",
    value: context.store.pendingSearch ?? "" }); context.store.pendingSearch = null;
  const mode = h("select", { name: "mode" }, ...["auto", "lexical", "semantic", "hybrid"].map(value =>
    h("option", { value, selected: context.store.preferences.searchMode === value }, value)));
  const topK = h("input", { type: "number", min: 1, max: 50, value: context.store.preferences.resultCount });
  const category = h("input", { placeholder: "cs.AI" });
  const fromDate = h("input", { type: "date" });
  const toDate = h("input", { type: "date" });
  const form = h("form", { class: "preview-form" }, field("Query", query), field("Mode", mode), field("Results", topK),
    field("Category (optional)", category), field("Submitted from", fromDate), field("Submitted to", toDate),
    h("button", { class: "primary-button", type: "submit" }, "Search papers"));
  const run = async (selectedMode = mode.value) => {
    if (!form.reportValidity()) return; controller?.abort(); controller = new AbortController();
    output.replaceChildren(loading(selectedMode === "lexical" ? "Searching PostgreSQL full text…" : "Embedding and retrieving papers…"));
    try {
      const value = await context.api.post<SearchResponse>("/search", { query: query.value.trim(), topK: Number(topK.value),
        mode: selectedMode, category: category.value.trim() || null, fromDate: fromDate.value || null,
        toDate: toDate.value || null }, { signal: controller.signal });
      context.store.rememberSearch(query.value.trim()); renderResults(value);
    } catch (error) { if (!(error instanceof DOMException && error.name === "AbortError")) output.replaceChildren(errorState(error, () => void run(selectedMode))); }
  };
  const renderResults = (value: SearchResponse) => {
    const header = h("div", {}, badge(`${value.results.length} result(s)`, "ok"),
      h("p", { class: "placeholder-copy" }, `${value.mode} · ${value.embeddingModel} · ${value.diagnostics.elapsedMilliseconds} ms`),
      h("div", { class: "suggestions" }, ...[category.value && `category:${category.value}`,
        fromDate.value && `from:${fromDate.value}`, toDate.value && `to:${toDate.value}`]
        .filter((item): item is string => Boolean(item)).map(item => badge(item))));
    const cards = value.results.map(result => resultCard(result, value.mode));
    const details = h("details", { class: "chat-options" }, h("summary", {}, "Retrieval diagnostics"),
      h("pre", { class: "diagnostic-block" }, JSON.stringify(value.diagnostics, null, 2)));
    output.replaceChildren(header, cards.length ? h("div", { class: "result-list" }, ...cards) : empty("No results. Broaden terms, remove filters, or try hybrid mode."),
      ...(context.store.preferences.diagnostics ? [details] : []),
      h("div", { class: "chat-actions" }, button("Send these results to Chat", () => {
        const ids = value.results.map(item => item.arxivId).join(", ");
        context.store.pendingChatQuestion = `Using the retrieved papers ${ids}, answer: ${query.value}`; context.navigate("chat");
      }, true),
        button("Compare lexical / semantic / hybrid", compare, true)));
  };
  const resultCard = (result: SearchResult, source: string) => h("article", { class: "result-card" },
    h("div", { class: "card-heading" }, h("strong", {}, result.title), badge(result.retrievalSource || source)),
    h("span", {}, `[arXiv:${result.arxivId}] · ${scoreLabel(result.retrievalSource || source)} ${result.score.toFixed(3)}`),
    h("p", {}, result.abstractText), h("div", { class: "chat-actions" },
      externalLink("Open on arXiv", safeArxivUrl(result.arxivId) ?? "https://arxiv.org"),
      button("Ask about this", () => { context.store.pendingChatQuestion = `Explain arXiv ${result.arxivId}.`; context.navigate("chat"); }, true)));
  const compare = () => { output.replaceChildren(loading("Comparing three retrieval modes…"));
    Promise.all(["lexical", "semantic", "hybrid"].map(async candidate => ({ candidate,
      value: await context.api.post<SearchResponse>("/search", { query: query.value.trim(), topK: Number(topK.value),
        mode: candidate, category: category.value.trim() || null, fromDate: fromDate.value || null,
        toDate: toDate.value || null }) })))
      .then(values => output.replaceChildren(...values.map(({ candidate, value }) => h("article", { class: "result-card" },
        h("h3", {}, `${candidate}: ${value.diagnostics.elapsedMilliseconds} ms`),
        h("ol", {}, ...value.results.map(item => h("li", {}, `${item.arxivId} — ${item.title}`)))))))
      .catch(error => output.replaceChildren(errorState(error, compare)));
  };
  form.addEventListener("submit", event => { event.preventDefault(); void run(); });
  const recent = context.store.recentSearches.length ? h("div", { class: "suggestions" }, ...context.store.recentSearches.map(value =>
    button(value, () => { query.value = value; void run(); }, true))) : null;
  root.replaceChildren(section("Search", "Automatic routing uses lexical fast paths and hybrid retrieval for broad topics.",
    h("div", { class: "search-layout" }, h("div", {}, form, recent), output)));
  return () => controller?.abort();
}};

function scoreLabel(source: string): string {
  if (source === "vector") return "cosine similarity";
  if (source === "both") return "weighted RRF score";
  if (source === "lexical") return "full-text rank";
  return "retrieval score";
}

import type { Paper, PaperPage, PaperSummary } from "../api/contracts";
import { badge, button, externalLink, field, h, section } from "../components/dom";
import { empty, errorState, loading } from "../components/states";
import type { Page } from "./page";

export const papersPage: Page = { title: "Papers", feature: "Papers", mount(root, context) {
  let stopped = false; let cursor: number | null = null;
  const list = h("div", { class: "result-list" });
  const detail = h("aside", { class: "preview-result" });
  const query = h("input", { name: "query", placeholder: "Any paper text" });
  const arxivId = h("input", { name: "arxivId", placeholder: "2401.00001" });
  const title = h("input", { name: "title", placeholder: "retrieval" });
  const author = h("input", { name: "author", placeholder: "Ada Lovelace" });
  const category = h("input", { name: "category", placeholder: "cs.AI" });
  const fromDate = h("input", { name: "fromDate", type: "date" });
  const toDate = h("input", { name: "toDate", type: "date" });
  const embeddingState = h("select", { name: "embeddingState" },
    h("option", { value: "" }, "Any embedding state"), h("option", { value: "CURRENT" }, "Current"),
    h("option", { value: "MISSING" }, "Missing"), h("option", { value: "STALE" }, "Stale"));
  const form = h("form", { class: "preview-form" }, field("Any text", query), field("arXiv ID", arxivId),
    field("Title", title), field("Author", author), field("Category", category), field("From", fromDate),
    field("To", toDate), field("Embedding", embeddingState),
    h("button", { class: "primary-button", type: "submit" }, "Browse papers"));
  const load = async (append = false) => {
    if (!append) { cursor = null; list.replaceChildren(loading("Loading papers…")); }
    const params = new URLSearchParams({ limit: "25" });
    if (query.value.trim()) params.set("query", query.value.trim());
    if (arxivId.value.trim()) params.set("arxivId", arxivId.value.trim());
    if (title.value.trim()) params.set("title", title.value.trim());
    if (author.value.trim()) params.set("author", author.value.trim());
    if (category.value.trim()) params.set("category", category.value.trim());
    if (fromDate.value) params.set("fromDate", fromDate.value);
    if (toDate.value) params.set("toDate", toDate.value);
    if (embeddingState.value) params.set("embeddingState", embeddingState.value);
    if (cursor) params.set("after", String(cursor));
    try {
      const page = await context.api.get<PaperPage>(`/papers?${params}`); if (stopped) return;
      const cards = page.items.map(paperCard);
      if (!append) list.replaceChildren(...cards); else list.append(...cards);
      if (!page.items.length && !append) list.replaceChildren(empty("No papers match these filters. Try a broader query."));
      cursor = page.nextCursor;
      if (page.hasMore) list.append(button("Load more", () => void load(true), true));
    } catch (error) { if (!stopped) list.replaceChildren(errorState(error, () => void load())); }
  };
  function paperCard(paper: PaperSummary): HTMLElement {
    return h("article", { class: "result-card" },
      h("div", { class: "card-heading" }, h("strong", {}, paper.title),
        badge(paper.embeddingStatus, paper.embeddingStatus === "CURRENT" ? "ok" : "working")),
      h("span", {}, `[arXiv:${paper.arxivId}] · ${paper.authors?.slice(0, 3).join(", ") || "Unknown authors"}`),
      h("p", {}, paper.abstractPreview),
      h("div", { class: "chat-actions" }, button("Details", () => void loadDetail(paper.arxivId), true),
        externalLink("Abstract", paper.abstractUrl), externalLink("PDF", paper.pdfUrl),
        button("Ask about this", () => ask(`Summarize and explain arXiv ${paper.arxivId}.`), true),
        button("Find similar", () => { context.store.pendingSearch = paper.title; context.navigate("search"); }, true)));
  }
  async function loadDetail(id: string): Promise<void> {
    detail.replaceChildren(loading("Loading full paper details…"));
    try { showDetail(await context.api.get<Paper>(`/papers/${encodeURIComponent(id)}`)); }
    catch (error) { detail.replaceChildren(errorState(error, () => void loadDetail(id))); }
  }
  function showDetail(paper: Paper): void {
    history.replaceState(null, "", `#/papers/${encodeURIComponent(paper.arxivId)}`);
    detail.replaceChildren(h("article", { class: "result-card paper-detail" }, h("h3", {}, paper.title),
      h("p", {}, paper.abstractText), h("span", {}, paper.categories.join(" · ")),
      h("small", {}, `Submitted ${paper.submittedDate ?? "unknown"}; updated ${paper.updatedDate ?? "unknown"}`),
      h("small", {}, `Embedding ${paper.embeddingStatus.toLowerCase()} · ${paper.embeddingModel} · ${paper.retrievalDocumentVersion}`),
      h("small", {}, `Content hash ${paper.contentHash}`),
      externalLink("Open canonical arXiv page", paper.abstractUrl)));
  }
  function ask(question: string): void { context.store.pendingChatQuestion = question; context.navigate("chat"); }
  form.addEventListener("submit", event => { event.preventDefault(); void load(); });
  root.replaceChildren(section("Papers", "Cursor-paginated canonical records with embedding state and arXiv deep links.",
    form, h("div", { class: "search-layout" }, list, detail)));
  const deepId = decodeURIComponent(location.hash.split("/").slice(2).join("/"));
  if (deepId) void loadDetail(deepId);
  void load();
  return () => { stopped = true; };
}};

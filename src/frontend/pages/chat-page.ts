import type { ChatRequest, Citation, Paper } from "../api/contracts";
import { streamChat, type SseFrame } from "../api/chat-stream";
import { badge, button, externalLink, field, h, section } from "../components/dom";
import { safeArxivUrl } from "../components/citation";
import type { ChatEntry } from "../state/app-store";
import type { Page } from "./page";

type ActiveNodes = { article: HTMLElement; answer: HTMLParagraphElement; stage: HTMLElement;
  evidence: HTMLElement; diagnostics: HTMLElement; entry: ChatEntry; started: number; firstToken: number | null;
  pendingFrame: number | null };

export const chatPage: Page = { title: "Chat", feature: "Chat", mount(root, context) {
  let active: AbortController | null = null;
  const transcript = h("div", { class: "chat-transcript", ariaLive: "polite", ariaRelevant: "additions text" });
  context.store.transcript.forEach(entry => transcript.append(renderEntry(entry)));
  const question = h("textarea", { rows: 4, required: true, placeholder: "Which papers discuss neural retrieval?",
    value: context.store.pendingChatQuestion ?? "" }); context.store.pendingChatQuestion = null;
  const mode = h("select", {}, ...["", "lexical", "semantic", "hybrid"].map(value => h("option", { value }, value || "Automatic")));
  const category = h("input", { placeholder: "cs.AI" });
  const agentic = h("input", { type: "checkbox" });
  const submit = h("button", { class: "primary-button", type: "submit" }, "Ask Foundry");
  const stop = button("Stop generation", () => active?.abort(), true); stop.hidden = true;
  const form = h("form", { class: "chat-form" }, field("Question", question),
    h("details", { class: "chat-options" }, h("summary", {}, "Advanced retrieval"),
      h("div", { class: "chat-option-grid" }, field("Mode", mode), field("Category", category), field("Agentic tools", agentic))),
    h("div", { class: "chat-actions" }, submit, stop));
  const suggestions = h("div", { class: "suggestions" }, ...[
    "Compare the retrieval approaches represented in this corpus.",
    "Give me a concise literature survey of retrieval-augmented generation.",
    "Which recent cs.AI papers discuss vector search?"
  ].map(value => button(value, () => { question.value = value; question.focus(); }, true)));
  const toolbar = h("div", { class: "chat-actions" },
    button("Regenerate", () => { const previous = [...context.store.transcript].reverse().find(entry => entry.role === "user");
      if (previous) { question.value = previous.content; void ask(); } }, true),
    button("Copy last answer", () => { const answer = [...context.store.transcript].reverse().find(entry => entry.role === "assistant");
      if (answer) void navigator.clipboard.writeText(answer.content); }, true),
    button("Clear", () => { context.store.clearTranscript(); transcript.replaceChildren(); }, true),
    button("Export Markdown", () => exportTranscript("markdown"), true),
    button("Export JSON", () => exportTranscript("json"), true));

  function renderEntry(entry: ChatEntry): HTMLElement {
    const evidence = h("div", { class: "citation-list" });
    renderEvidence(evidence, entry.citations ?? []);
    const answer = h("p"); renderAnswerText(answer, entry.content, evidence, entry.citations ?? []);
    return h("article", { class: `chat-message ${entry.role}` }, h("strong", {}, entry.role === "user" ? "You" : "Grounded assistant"),
      entry.stage ? h("small", { class: "stream-status" }, entry.stage) : null, answer, evidence,
      entry.toolsUsed?.length ? h("small", { class: "tool-trace" }, `Tools: ${entry.toolsUsed.join(", ")}`) : null);
  }

  async function ask(): Promise<void> {
    const message = question.value.trim(); if (!message || active) return;
    const request: ChatRequest = { message, retrievalMode: mode.value || null, category: category.value.trim() || null,
      agentic: agentic.checked || null };
    const user: ChatEntry = { role: "user", content: message };
    const assistant: ChatEntry = { role: "assistant", content: "", citations: [], toolsUsed: [], streaming: true, stage: "Starting retrieval…" };
    context.store.transcript.push(user, assistant); context.store.saveTranscript();
    transcript.append(renderEntry(user));
    const nodes = activeEntry(assistant); transcript.append(nodes.article); nodes.article.scrollIntoView({ behavior: "smooth", block: "end" });
    active = new AbortController(); submit.disabled = true; stop.hidden = false;
    try {
      let completed = false;
      await streamChat(request, active.signal, frame => { if (handleFrame(frame, nodes)) completed = true; });
      if (!completed) throw new Error("The chat stream ended without a completion event.");
    } catch (error) {
      assistant.streaming = false; assistant.stage = undefined;
      if (error instanceof DOMException && error.name === "AbortError") assistant.content ||= "Generation stopped.";
      else { assistant.content ||= `Chat failed: ${error instanceof Error ? error.message : "unknown error"}`; context.reportError(error); }
      flushActive(nodes);
    } finally {
      active = null; submit.disabled = false; stop.hidden = true; context.store.saveTranscript(); question.disabled = false; question.focus();
    }
  }

  function activeEntry(entry: ChatEntry): ActiveNodes {
    const answer = h("p", {}, "Waiting for retrieval…");
    const stage = h("small", { class: "stream-status" }, entry.stage ?? "");
    const evidence = h("div", { class: "citation-list" });
    const diagnostics = h("details", { class: "chat-options hidden" }, h("summary", {}, "Request diagnostics"), h("pre", { class: "diagnostic-block" }));
    const article = h("article", { class: "chat-message assistant streaming" }, h("strong", {}, "Grounded assistant"), stage, answer, evidence, diagnostics);
    return { article, answer, stage, evidence, diagnostics, entry, started: performance.now(), firstToken: null,
      pendingFrame: null };
  }

  function handleFrame(frame: SseFrame, nodes: ActiveNodes): boolean {
    const data = (frame.data ?? {}) as Record<string, unknown>;
    if (frame.event === "request") nodes.entry.diagnostics = { requestId: data.requestId };
    else if (frame.event === "retrieval") nodes.entry.stage = "Searching the local corpus…";
    else if (frame.event === "citations") {
      nodes.entry.citations = frame.data as Citation[]; nodes.entry.stage = `${nodes.entry.citations.length} evidence item(s) selected`;
      renderEvidence(nodes.evidence, nodes.entry.citations); void hydrateEvidence(nodes.evidence, nodes.entry.citations);
    } else if (frame.event === "delta") {
      if (nodes.firstToken == null) nodes.firstToken = performance.now();
      nodes.entry.content += String(data.text ?? "");
      nodes.entry.stage = `Foundry is streaming · first token ${Math.round(nodes.firstToken - nodes.started)} ms`;
    } else if (frame.event === "metrics") {
      nodes.entry.diagnostics = { ...data, clientTtftMilliseconds: nodes.firstToken == null ? null : Math.round(nodes.firstToken - nodes.started) };
      const pre = nodes.diagnostics.querySelector("pre"); if (pre) pre.textContent = JSON.stringify(nodes.entry.diagnostics, null, 2);
      nodes.diagnostics.classList.toggle("hidden", !context.store.preferences.diagnostics);
    } else if (frame.event === "complete") {
      nodes.entry.content = String(data.answer ?? nodes.entry.content); nodes.entry.toolsUsed = (data.toolsUsed as string[] | undefined) ?? [];
      nodes.entry.groundingStatus = String(data.groundingStatus ?? "GROUNDED"); nodes.entry.cacheStatus = String(data.cacheStatus ?? "BYPASS");
      nodes.entry.streaming = false;
      const total = Math.round(performance.now() - nodes.started);
      const ttft = nodes.firstToken == null ? "local response" : `first token ${Math.round(nodes.firstToken - nodes.started)} ms`;
      nodes.entry.stage = `Completed in ${total} ms · ${ttft}`;
      flushActive(nodes); return true;
    } else if (frame.event === "error") throw new Error(String(data.message ?? "Chat stream failed"));
    scheduleActive(nodes); return false;
  }

  function scheduleActive(nodes: ActiveNodes): void {
    if (nodes.pendingFrame != null) return;
    const keepAtBottom = isNearBottom(transcript);
    nodes.pendingFrame = requestAnimationFrame(() => {
      nodes.pendingFrame = null;
      updateActive(nodes);
      if (keepAtBottom) nodes.article.scrollIntoView({ block: "end" });
    });
  }

  function flushActive(nodes: ActiveNodes): void {
    if (nodes.pendingFrame != null) cancelAnimationFrame(nodes.pendingFrame);
    nodes.pendingFrame = null;
    updateActive(nodes);
  }

  function updateActive(nodes: ActiveNodes): void {
    nodes.stage.textContent = nodes.entry.stage ?? ""; nodes.stage.hidden = !nodes.entry.stage;
    nodes.article.classList.toggle("streaming", Boolean(nodes.entry.streaming));
    renderAnswerText(nodes.answer, nodes.entry.content || (nodes.entry.streaming ? "Waiting for Foundry…" : ""),
      nodes.evidence, nodes.entry.citations ?? []);
    if (nodes.entry.toolsUsed?.length && !nodes.article.querySelector(".tool-trace")) nodes.article.append(h("small", { class: "tool-trace" }, `Tools: ${nodes.entry.toolsUsed.join(", ")}`));
  }

  function renderEvidence(host: HTMLElement, citations: Citation[]): void {
    host.replaceChildren(); if (!citations.length) return;
    host.append(h("strong", {}, "Evidence (metadata and abstracts only)"), ...citations.map((citation, index) =>
      h("details", { class: "evidence-card", dataset: { evidence: citation.arxivId } },
        h("summary", {}, `[${index + 1}] ${citation.title} · ${citation.retrievalSource} · ${citation.score?.toFixed(3) ?? "n/a"}`),
        externalLink(`arXiv:${citation.arxivId}`, citation.url), h("div", { class: "evidence-extra" }, "Loading metadata…"))));
  }

  async function hydrateEvidence(host: HTMLElement, citations: Citation[]): Promise<void> {
    await Promise.all(citations.map(async citation => {
      try { const paper = await context.api.get<Paper>(`/papers/${encodeURIComponent(citation.arxivId)}`);
        const card = host.querySelector<HTMLElement>(`[data-evidence="${CSS.escape(citation.arxivId)}"] .evidence-extra`);
        if (card) card.replaceChildren(h("p", {}, paper.abstractText.slice(0, 500) + (paper.abstractText.length > 500 ? "…" : "")),
          h("small", {}, paper.authors.join(", ")));
      } catch { /* citation remains usable even when Papers feature is disabled */ }
    }));
  }

  function renderAnswerText(host: HTMLElement, value: string, evidence: HTMLElement,
                            citations: Citation[]): void {
    host.replaceChildren(); const regex = /\[arXiv:([^\]\s]+)]/gi; let offset = 0;
    for (let match; (match = regex.exec(value));) {
      host.append(value.slice(offset, match.index)); const id = match[1] ?? "";
      const citation = citations.find(value => value.arxivId.toLowerCase() === id.toLowerCase());
      const url = safeArxivUrl(id, citation?.url);
      const ref = url
        ? h("a", { href: url, target: "_blank", rel: "noopener noreferrer", class: "inline-citation",
          ariaLabel: `Open arXiv paper ${id}` }, match[0])
        : h("span", { class: "inline-citation" }, match[0]);
      const highlight = (active: boolean) => evidence.querySelector(`[data-evidence="${CSS.escape(id)}"]`)?.classList.toggle("highlight", active);
      ref.addEventListener("mouseenter", () => highlight(true)); ref.addEventListener("mouseleave", () => highlight(false));
      ref.addEventListener("focus", () => highlight(true)); ref.addEventListener("blur", () => highlight(false));
      host.append(ref); offset = regex.lastIndex;
    }
    host.append(value.slice(offset));
  }

  function isNearBottom(host: HTMLElement): boolean {
    return host.scrollHeight - host.scrollTop - host.clientHeight < 96;
  }

  function exportTranscript(format: "markdown" | "json"): void {
    const content = format === "json" ? JSON.stringify({ schemaVersion: 1, transcript: context.store.transcript }, null, 2)
      : context.store.transcript.map(entry => `## ${entry.role === "user" ? "You" : "Grounded assistant"}\n\n${entry.content}\n\n${(entry.citations ?? []).map(c => `- [arXiv:${c.arxivId}](${c.url})`).join("\n")}`).join("\n\n");
    const url = URL.createObjectURL(new Blob([content], { type: format === "json" ? "application/json" : "text/markdown" }));
    const link = h("a", { href: url, download: `arxiv-rag-transcript.${format === "json" ? "json" : "md"}` }); link.click(); URL.revokeObjectURL(url);
  }

  form.addEventListener("submit", event => { event.preventDefault(); void ask(); });
  root.replaceChildren(section("Chat", "Grounded chat streams tokens, exposes retrieval stages, and links every authoritative citation to arXiv.",
    suggestions, toolbar, transcript, form,
    h("p", { class: "safe-config-note" }, "Answers are grounded in imported metadata and abstracts, not full paper PDFs.")));
  return () => active?.abort();
}};

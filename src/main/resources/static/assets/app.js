//#region src/frontend/api/http-client.ts
var e = class extends Error {
	status;
	problem;
	requestId;
	constructor(e, t, n) {
		super(t.detail ?? t.error ?? `Request failed with status ${e}`), this.status = e, this.problem = t, this.requestId = n;
	}
}, t = new class {
	base;
	defaultTimeoutMs;
	constructor(e = "/api/v1", t = 15e3) {
		this.base = e, this.defaultTimeoutMs = t;
	}
	get(e, t = {}) {
		return this.json(e, t);
	}
	post(e, t, n = {}, r) {
		return this.json(e, {
			...n,
			method: "POST",
			body: t instanceof FormData ? t : JSON.stringify(t ?? {})
		}, r);
	}
	delete(e, t = {}) {
		return this.json(e, {
			...t,
			method: "DELETE"
		});
	}
	async json(t, n = {}, r = this.defaultTimeoutMs) {
		let i = new AbortController(), a = window.setTimeout(() => i.abort("deadline exceeded"), r), o = n.signal ? AbortSignal.any([n.signal, i.signal]) : i.signal, s = new Headers(n.headers);
		s.set("Accept", "application/json"), n.body && !(n.body instanceof FormData) && s.set("Content-Type", "application/json");
		try {
			let r = await fetch(this.base + t, {
				...n,
				headers: s,
				signal: o
			}), i = r.headers.get("X-Request-ID");
			if (!r.ok) {
				let t = {
					status: r.status,
					detail: r.statusText
				};
				try {
					t = await r.json();
				} catch {}
				throw new e(r.status, t, i ?? t.requestId ?? null);
			}
			return r.status === 204 ? void 0 : await r.json();
		} finally {
			window.clearTimeout(a);
		}
	}
}();
//#endregion
//#region src/frontend/components/dom.ts
function n(e, t = {}, ...n) {
	let r = document.createElement(e);
	for (let [e, n] of Object.entries(t)) n != null && n !== !1 && (e === "class" ? r.className = String(n) : e === "dataset" && typeof n == "object" ? Object.assign(r.dataset, n) : e === "text" ? r.textContent = String(n) : e.startsWith("on") && typeof n == "function" ? r.addEventListener(e.slice(2).toLowerCase(), n) : e in r ? r[e] = n : r.setAttribute(e, String(n)));
	for (let e of n.flat()) e != null && e !== !1 && r.append(e instanceof Node ? e : document.createTextNode(String(e)));
	return r;
}
function r(e, t, ...r) {
	return n("div", {}, n("h2", {}, e), n("p", { class: "placeholder-copy" }, t), ...r);
}
function i(e, t = "") {
	return n("span", { class: `status-badge ${t}`.trim() }, e);
}
function a(e, t) {
	return n("div", { class: "metric" }, n("strong", {}, t), n("span", {}, e));
}
function o(...e) {
	return n("div", { class: "metric-grid" }, ...e);
}
function s(e, t) {
	return n("label", {}, e, t);
}
function c(e, t, r = !1) {
	return n("button", {
		type: "button",
		class: r ? "secondary-button" : "primary-button",
		onclick: t
	}, e);
}
function l(e, t) {
	return n("a", {
		href: t,
		target: "_blank",
		rel: "noopener noreferrer",
		class: "citation citation-link"
	}, e, " ↗");
}
//#endregion
//#region src/frontend/state/persistence.ts
var u = "arxiv-rag:";
function d(e, t, n) {
	try {
		let r = JSON.parse(localStorage.getItem(u + e) ?? "null");
		return r?.version === t ? r.value : n;
	} catch {
		return n;
	}
}
function f(e, t, n) {
	localStorage.setItem(u + e, JSON.stringify({
		version: t,
		value: n
	}));
}
var p = new class extends EventTarget {
	runtime = null;
	preferences = d("preferences", 1, {
		searchMode: "auto",
		resultCount: 5,
		diagnostics: !1,
		saveTranscript: !0,
		theme: "system"
	});
	transcript = this.preferences.saveTranscript ? d("transcript", 1, []) : [];
	recentSearches = d("searches", 1, []);
	pendingChatQuestion = null;
	pendingSearch = null;
	updatePreferences(e) {
		this.preferences = {
			...this.preferences,
			...e
		}, this.preferences.saveTranscript || (this.transcript = [], localStorage.removeItem("arxiv-rag:transcript")), f("preferences", 1, this.preferences), document.documentElement.dataset.theme = this.preferences.theme, this.changed();
	}
	saveTranscript() {
		this.preferences.saveTranscript && f("transcript", 1, this.transcript), this.changed();
	}
	clearTranscript() {
		this.transcript = [], localStorage.removeItem("arxiv-rag:transcript"), this.changed();
	}
	rememberSearch(e) {
		this.recentSearches = [e, ...this.recentSearches.filter((t) => t !== e)].slice(0, 8), f("searches", 1, this.recentSearches);
	}
	changed() {
		this.dispatchEvent(new Event("change"));
	}
}();
//#endregion
//#region src/frontend/router.ts
function m() {
	return (location.hash.replace(/^#\/?/, "").split("/")[0] || "system-status").toLowerCase();
}
function h(e) {
	location.hash = `#/${e}`;
}
//#endregion
//#region src/frontend/components/states.ts
function g(e) {
	return n("div", {
		class: "health-panel",
		role: "status"
	}, n("span", { class: "status-badge working" }, "Working"), n("p", {}, e));
}
function _(e) {
	return n("div", { class: "health-panel" }, n("span", { class: "status-badge" }, "No data"), n("p", {}, e));
}
function v(t, r) {
	let i = t instanceof Error ? t.message : "The operation failed.", a = t instanceof e ? t.requestId : null;
	return n("div", {
		class: "health-panel error",
		role: "alert",
		tabIndex: -1
	}, n("div", {}, n("span", { class: "status-badge error" }, "Failed"), n("p", {}, i), a ? n("small", {}, `Request ID: ${a}`) : null), r ? n("button", {
		type: "button",
		class: "primary-button",
		onclick: r
	}, "Retry") : null);
}
//#endregion
//#region src/frontend/pages/status-page.ts
var y = {
	title: "System Status",
	mount(e, t) {
		let s = !1, l = async () => {
			e.replaceChildren(r("System Status", "Passive readiness and operational state; this view never spends Foundry tokens.", g("Reading local dependency state…")));
			try {
				let u = await t.api.get("/health");
				if (s) return;
				let d = u.status === "UP" ? "ok" : u.status === "DOWN" ? "error" : "working", f = u.caches.retrieval, p = u.provider.operations?.lastSuccess, m = u.provider.operations?.lastFailure, h = u.database.statistics;
				e.replaceChildren(r("System Status", "Passive readiness and operational state; this view never spends Foundry tokens.", n("div", { class: `health-panel ${d}` }, n("div", {}, i(u.status, d), n("h3", {}, u.message), n("small", {}, `Checked ${new Date(u.checkedAt).toLocaleString()}`)), c("Refresh", l)), o(a("PostgreSQL", u.database.reachable ? "Reachable" : "Down"), a("pgvector", u.vectorStore.status), a("Provider", u.provider.status), a("Circuit", u.provider.circuitState), a("Active chats", `${u.admission.activeChats}/${u.admission.maximumChats}`), a("Vector coverage", `${u.corpus.vectorCoveragePercent.toFixed(1)}%`)), n("div", { class: "config-grid" }, n("div", { class: "config-item" }, n("span", {}, "Database"), n("strong", {}, `${u.database.product} ${u.database.version ?? ""}`)), n("div", { class: "config-item" }, n("span", {}, "Chat deployment"), n("strong", {}, u.provider.chatDeployment)), n("div", { class: "config-item" }, n("span", {}, "Last Foundry success"), n("strong", {}, p ? `${p.operation} · ${new Date(p.at).toLocaleString()}` : "No call observed")), n("div", { class: "config-item" }, n("span", {}, "Last Foundry failure"), n("strong", {}, m ? `${m.operation} · HTTP ${m.statusCode ?? "network"}` : "None observed"), m?.requestId ? n("div", { class: "chat-actions" }, n("small", {}, `Request ID: ${m.requestId}`), c("Copy", () => void navigator.clipboard.writeText(m.requestId ?? ""), !0)) : null), n("div", { class: "config-item" }, n("span", {}, "Retrieval-cache hit rate"), n("strong", {}, `${(f.hitRate * 100).toFixed(1)}%`)), n("div", { class: "config-item" }, n("span", {}, "Query statistics"), n("strong", {}, h.pgStatStatementsInstalled ? "pg_stat_statements active" : "Unavailable"), h.normalizedStatementP95Milliseconds == null ? null : n("small", {}, `Statement mean-time p95 ${h.normalizedStatementP95Milliseconds.toFixed(1)} ms · ${h.slowStatementCount ?? 0} slow`)), n("div", { class: "config-item" }, n("span", {}, "Database maintenance"), n("strong", {}, h.estimatedDeadTuples == null ? "Unavailable" : `${h.estimatedDeadTuples.toLocaleString()} estimated dead tuples`), h.databaseBytes == null ? null : n("small", {}, `${(h.databaseBytes / 1048576).toFixed(1)} MiB database size`)), n("div", { class: "config-item" }, n("span", {}, "Latest embedding job"), n("strong", {}, u.latestEmbeddingJob?.status ?? "None")))));
			} catch (t) {
				s || e.replaceChildren(r("System Status", "Dependency state", v(t, l)));
			}
		};
		return l(), () => {
			s = !0;
		};
	}
}, b = {
	title: "Import",
	feature: "Import",
	mount(e, t) {
		let l = !1, u = n("div"), d = n("div"), f = n("input", {
			name: "sourcePath",
			type: "text",
			placeholder: "C:\\data\\arxiv-metadata-oai-snapshot.json",
			required: !0
		}), p = n("input", {
			name: "categories",
			type: "text",
			value: "cs.AI,cs.CL,cs.LG,cs.CV"
		}), m = n("input", {
			name: "fromDate",
			type: "date",
			value: "2022-01-01"
		}), h = n("input", {
			name: "maxRecords",
			type: "number",
			min: 1,
			max: 1e6,
			value: 5e4
		}), y = n("form", { class: "preview-form" }, s("Server-side JSONL path", f), s("Categories (comma separated)", p), s("Submitted from", m), s("Maximum accepted records", h), n("div", { class: "chat-actions" }, c("Dry-run preview"), c("Start durable import", void 0, !0))), b = () => ({
			sourcePath: f.value,
			categories: p.value.split(",").map((e) => e.trim()).filter(Boolean),
			fromDate: m.value || null,
			maxRecords: Number(h.value)
		}), S = y.querySelectorAll("button");
		S[0]?.addEventListener("click", async () => {
			if (y.reportValidity()) {
				d.replaceChildren(g("Scanning without writing papers…"));
				try {
					let e = await t.api.post("/imports/preview", b(), {}, 12e4), r = e.malformedLineSamples.length ? [n("p", { class: "safe-config-note" }, `Malformed sample lines: ${e.malformedLineSamples.join(", ")}`)] : [];
					d.replaceChildren(o(a("Read", e.recordsRead), a("Accepted", e.accepted), a("Filtered", e.filtered), a("Invalid", e.invalidRecords), a("Duplicates in file", e.duplicateInFile), a("Already stored", e.alreadyStored)), ...r);
				} catch (e) {
					d.replaceChildren(v(e));
				}
			}
		}), S[1]?.addEventListener("click", async () => {
			if (y.reportValidity()) try {
				await t.api.post("/imports/jobs", b()), await T();
			} catch (e) {
				d.replaceChildren(v(e));
			}
		});
		let C = n("input", {
			type: "file",
			accept: ".jsonl,application/json"
		}), w = n("form", { class: "job-controls" }, s("Upload a JSONL slice (maximum 25 MiB)", C), n("button", {
			type: "submit",
			class: "primary-button"
		}, "Upload and import"));
		w.addEventListener("submit", async (e) => {
			e.preventDefault();
			let n = C.files?.[0];
			if (!n) return;
			let r = new FormData();
			r.set("file", n), r.set("categories", p.value), m.value && r.set("fromDate", m.value), r.set("maxRecords", h.value);
			try {
				await t.api.post("/imports/uploads", r, {}, 12e4), await T();
			} catch (e) {
				d.replaceChildren(v(e));
			}
		});
		async function T() {
			try {
				let e = await t.api.get("/imports/jobs?limit=10");
				if (l) return;
				u.replaceChildren(e.length ? n("div", { class: "result-list" }, ...e.map((e) => {
					let r = Math.max(0, e.imported + e.updated), a = Math.max(1, (Date.now() - new Date(e.startedAt).getTime()) / 1e3), o = e.recordsRead / a, s = e.maxRecords == null ? null : Math.max(0, e.maxRecords - e.accepted), l = s == null || o <= 0 ? null : s / o;
					return n("article", { class: "result-card" }, n("div", { class: "card-heading" }, i(e.status, e.status === "FAILED" ? "error" : e.status === "RUNNING" ? "working" : "ok"), n("strong", {}, e.sourcePath)), n("span", {}, `${e.recordsRead} read · ${r} written · ${e.skipped} filtered · ${e.invalidRecords} invalid`), n("small", {}, `${o.toFixed(1)} records/s${l == null ? "" : ` · ETA ${x(l)}`}`), e.malformedLineSamples?.length ? n("small", {}, `Malformed sample lines: ${e.malformedLineSamples.join(", ")}`) : null, e.sourceSha256 ? n("small", { class: "hash-value" }, `SHA-256 ${e.sourceSha256}`) : null, e.lastError ? n("p", { class: "error-copy" }, e.lastError) : null, n("div", { class: "chat-actions" }, ["RUNNING", "QUEUED"].includes(e.status) ? c("Cancel", () => void t.api.delete(`/imports/jobs/${e.jobId}`).then(T), !0) : null, [
						"FAILED",
						"CANCELLED",
						"INTERRUPTED"
					].includes(e.status) ? c("Resume", () => void t.api.post(`/imports/jobs/${e.jobId}/resume`).then(T), !0) : null));
				})) : _("No import history yet."));
			} catch (e) {
				l || u.replaceChildren(v(e, T));
			}
		}
		e.replaceChildren(r("Import", "Preview a local file, then run an idempotent durable import. Administrative access is required outside localhost.", y, w, d, n("h3", {}, "Recent imports"), u)), T();
		let E = window.setInterval(T, 3e3);
		return () => {
			l = !0, window.clearInterval(E);
		};
	}
};
function x(e) {
	return Number.isFinite(e) ? e < 60 ? `${Math.ceil(e)}s` : `${Math.floor(e / 60)}m ${Math.ceil(e % 60)}s` : "unknown";
}
//#endregion
//#region src/frontend/pages/papers-page.ts
var S = {
	title: "Papers",
	feature: "Papers",
	mount(e, t) {
		let a = !1, o = null, u = n("div", { class: "result-list" }), d = n("aside", { class: "preview-result" }), f = n("input", {
			name: "query",
			placeholder: "Any paper text"
		}), p = n("input", {
			name: "arxivId",
			placeholder: "2401.00001"
		}), m = n("input", {
			name: "title",
			placeholder: "retrieval"
		}), h = n("input", {
			name: "author",
			placeholder: "Ada Lovelace"
		}), y = n("input", {
			name: "category",
			placeholder: "cs.AI"
		}), b = n("input", {
			name: "fromDate",
			type: "date"
		}), x = n("input", {
			name: "toDate",
			type: "date"
		}), S = n("select", { name: "embeddingState" }, n("option", { value: "" }, "Any embedding state"), n("option", { value: "CURRENT" }, "Current"), n("option", { value: "MISSING" }, "Missing"), n("option", { value: "STALE" }, "Stale")), C = n("form", { class: "preview-form" }, s("Any text", f), s("arXiv ID", p), s("Title", m), s("Author", h), s("Category", y), s("From", b), s("To", x), s("Embedding", S), n("button", {
			class: "primary-button",
			type: "submit"
		}, "Browse papers")), w = async (e = !1) => {
			e || (o = null, u.replaceChildren(g("Loading papers…")));
			let n = new URLSearchParams({ limit: "25" });
			f.value.trim() && n.set("query", f.value.trim()), p.value.trim() && n.set("arxivId", p.value.trim()), m.value.trim() && n.set("title", m.value.trim()), h.value.trim() && n.set("author", h.value.trim()), y.value.trim() && n.set("category", y.value.trim()), b.value && n.set("fromDate", b.value), x.value && n.set("toDate", x.value), S.value && n.set("embeddingState", S.value), o && n.set("after", String(o));
			try {
				let r = await t.api.get(`/papers?${n}`);
				if (a) return;
				let i = r.items.map(T);
				e ? u.append(...i) : u.replaceChildren(...i), !r.items.length && !e && u.replaceChildren(_("No papers match these filters. Try a broader query.")), o = r.nextCursor, r.hasMore && u.append(c("Load more", () => void w(!0), !0));
			} catch (e) {
				a || u.replaceChildren(v(e, () => void w()));
			}
		};
		function T(e) {
			return n("article", { class: "result-card" }, n("div", { class: "card-heading" }, n("strong", {}, e.title), i(e.embeddingStatus, e.embeddingStatus === "CURRENT" ? "ok" : "working")), n("span", {}, `[arXiv:${e.arxivId}] · ${e.authors?.slice(0, 3).join(", ") || "Unknown authors"}`), n("p", {}, e.abstractPreview), n("div", { class: "chat-actions" }, c("Details", () => void E(e.arxivId), !0), l("Abstract", e.abstractUrl), l("PDF", e.pdfUrl), c("Ask about this", () => O(`Summarize and explain arXiv ${e.arxivId}.`), !0), c("Find similar", () => {
				t.store.pendingSearch = e.title, t.navigate("search");
			}, !0)));
		}
		async function E(e) {
			d.replaceChildren(g("Loading full paper details…"));
			try {
				D(await t.api.get(`/papers/${encodeURIComponent(e)}`));
			} catch (t) {
				d.replaceChildren(v(t, () => void E(e)));
			}
		}
		function D(e) {
			history.replaceState(null, "", `#/papers/${encodeURIComponent(e.arxivId)}`), d.replaceChildren(n("article", { class: "result-card paper-detail" }, n("h3", {}, e.title), n("p", {}, e.abstractText), n("span", {}, e.categories.join(" · ")), n("small", {}, `Submitted ${e.submittedDate ?? "unknown"}; updated ${e.updatedDate ?? "unknown"}`), n("small", {}, `Embedding ${e.embeddingStatus.toLowerCase()} · ${e.embeddingModel} · ${e.retrievalDocumentVersion}`), n("small", {}, `Content hash ${e.contentHash}`), l("Open canonical arXiv page", e.abstractUrl)));
		}
		function O(e) {
			t.store.pendingChatQuestion = e, t.navigate("chat");
		}
		C.addEventListener("submit", (e) => {
			e.preventDefault(), w();
		}), e.replaceChildren(r("Papers", "Cursor-paginated canonical records with embedding state and arXiv deep links.", C, n("div", { class: "search-layout" }, u, d)));
		let k = decodeURIComponent(location.hash.split("/").slice(2).join("/"));
		return k && E(k), w(), () => {
			a = !0;
		};
	}
}, C = /^(?:\d{4}\.\d{4,5}|[A-Za-z][A-Za-z0-9.-]*\/\d{7})(?:v\d+)?$/;
function w(e, t) {
	if (!C.test(e)) return null;
	try {
		let n = new URL(t ?? `https://arxiv.org/abs/${e}`);
		return n.protocol !== "https:" || n.hostname !== "arxiv.org" || n.username || n.password || n.port || n.pathname !== `/abs/${e}` ? null : n.href;
	} catch {
		return null;
	}
}
//#endregion
//#region src/frontend/pages/search-page.ts
var T = {
	title: "Search",
	feature: "Search",
	mount(e, t) {
		let a = null, o = n("aside", { class: "preview-result" }, _("Enter a query to search the indexed corpus.")), u = n("input", {
			name: "query",
			required: !0,
			placeholder: "papers about neural retrieval",
			value: t.store.pendingSearch ?? ""
		});
		t.store.pendingSearch = null;
		let d = n("select", { name: "mode" }, ...[
			"auto",
			"lexical",
			"semantic",
			"hybrid"
		].map((e) => n("option", {
			value: e,
			selected: t.store.preferences.searchMode === e
		}, e))), f = n("input", {
			type: "number",
			min: 1,
			max: 50,
			value: t.store.preferences.resultCount
		}), p = n("input", { placeholder: "cs.AI" }), m = n("input", { type: "date" }), h = n("input", { type: "date" }), y = n("form", { class: "preview-form" }, s("Query", u), s("Mode", d), s("Results", f), s("Category (optional)", p), s("Submitted from", m), s("Submitted to", h), n("button", {
			class: "primary-button",
			type: "submit"
		}, "Search papers")), b = async (e = d.value) => {
			if (y.reportValidity()) {
				a?.abort(), a = new AbortController(), o.replaceChildren(g(e === "lexical" ? "Searching PostgreSQL full text…" : "Embedding and retrieving papers…"));
				try {
					let n = await t.api.post("/search", {
						query: u.value.trim(),
						topK: Number(f.value),
						mode: e,
						category: p.value.trim() || null,
						fromDate: m.value || null,
						toDate: h.value || null
					}, { signal: a.signal });
					t.store.rememberSearch(u.value.trim()), x(n);
				} catch (t) {
					t instanceof DOMException && t.name === "AbortError" || o.replaceChildren(v(t, () => void b(e)));
				}
			}
		}, x = (e) => {
			let r = n("div", {}, i(`${e.results.length} result(s)`, "ok"), n("p", { class: "placeholder-copy" }, `${e.mode} · ${e.embeddingModel} · ${e.diagnostics.elapsedMilliseconds} ms`), n("div", { class: "suggestions" }, ...[
				p.value && `category:${p.value}`,
				m.value && `from:${m.value}`,
				h.value && `to:${h.value}`
			].filter((e) => !!e).map((e) => i(e)))), a = e.results.map((t) => S(t, e.mode)), s = n("details", { class: "chat-options" }, n("summary", {}, "Retrieval diagnostics"), n("pre", { class: "diagnostic-block" }, JSON.stringify(e.diagnostics, null, 2)));
			o.replaceChildren(r, a.length ? n("div", { class: "result-list" }, ...a) : _("No results. Broaden terms, remove filters, or try hybrid mode."), ...t.store.preferences.diagnostics ? [s] : [], n("div", { class: "chat-actions" }, c("Send these results to Chat", () => {
				let n = e.results.map((e) => e.arxivId).join(", ");
				t.store.pendingChatQuestion = `Using the retrieved papers ${n}, answer: ${u.value}`, t.navigate("chat");
			}, !0), c("Compare lexical / semantic / hybrid", C, !0)));
		}, S = (e, r) => n("article", { class: "result-card" }, n("div", { class: "card-heading" }, n("strong", {}, e.title), i(e.retrievalSource || r)), n("span", {}, `[arXiv:${e.arxivId}] · ${E(e.retrievalSource || r)} ${e.score.toFixed(3)}`), n("p", {}, e.abstractText), n("div", { class: "chat-actions" }, l("Open on arXiv", w(e.arxivId) ?? "https://arxiv.org"), c("Ask about this", () => {
			t.store.pendingChatQuestion = `Explain arXiv ${e.arxivId}.`, t.navigate("chat");
		}, !0))), C = () => {
			o.replaceChildren(g("Comparing three retrieval modes…")), Promise.all([
				"lexical",
				"semantic",
				"hybrid"
			].map(async (e) => ({
				candidate: e,
				value: await t.api.post("/search", {
					query: u.value.trim(),
					topK: Number(f.value),
					mode: e,
					category: p.value.trim() || null,
					fromDate: m.value || null,
					toDate: h.value || null
				})
			}))).then((e) => o.replaceChildren(...e.map(({ candidate: e, value: t }) => n("article", { class: "result-card" }, n("h3", {}, `${e}: ${t.diagnostics.elapsedMilliseconds} ms`), n("ol", {}, ...t.results.map((e) => n("li", {}, `${e.arxivId} — ${e.title}`))))))).catch((e) => o.replaceChildren(v(e, C)));
		};
		y.addEventListener("submit", (e) => {
			e.preventDefault(), b();
		});
		let T = t.store.recentSearches.length ? n("div", { class: "suggestions" }, ...t.store.recentSearches.map((e) => c(e, () => {
			u.value = e, b();
		}, !0))) : null;
		return e.replaceChildren(r("Search", "Automatic routing uses lexical fast paths and hybrid retrieval for broad topics.", n("div", { class: "search-layout" }, n("div", {}, y, T), o))), () => a?.abort();
	}
};
function E(e) {
	return e === "vector" ? "cosine similarity" : e === "both" ? "weighted RRF score" : e === "lexical" ? "full-text rank" : "retrieval score";
}
//#endregion
//#region src/frontend/api/chat-stream.ts
function D(e) {
	let t = e.replaceAll("\r\n", "\n"), n = [];
	for (let e; (e = t.indexOf("\n\n")) >= 0;) {
		let r = t.slice(0, e);
		if (t = t.slice(e + 2), !r.trim() || r.startsWith(":")) continue;
		let i = "message", a = [];
		for (let e of r.split("\n")) e.startsWith("event:") && (i = e.slice(6).trim()), e.startsWith("data:") && a.push(e.slice(5).trimStart());
		if (a.length) try {
			n.push({
				event: i,
				data: JSON.parse(a.join("\n"))
			});
		} catch {
			throw Error(`Malformed ${i} SSE event`);
		}
	}
	return {
		frames: n,
		remainder: t
	};
}
async function O(t, n, r) {
	let i = await fetch("/api/v1/chat/stream", {
		method: "POST",
		headers: {
			Accept: "text/event-stream",
			"Content-Type": "application/json"
		},
		body: JSON.stringify(t),
		signal: n
	});
	if (!i.ok || !i.body) {
		let t = {
			status: i.status,
			detail: i.statusText
		};
		try {
			t = await i.json();
		} catch {}
		throw new e(i.status, t, i.headers.get("X-Request-ID"));
	}
	let a = i.body.getReader(), o = new TextDecoder(), s = "";
	for (;;) {
		let { value: e, done: t } = await a.read(), n = D(s + o.decode(e ?? /* @__PURE__ */ new Uint8Array(), { stream: !t }));
		if (s = n.remainder, n.frames.forEach(r), t) break;
	}
	s.trim() && D(s + "\n\n").frames.forEach(r);
}
//#endregion
//#region src/frontend/pages/chat-page.ts
var k = {
	title: "Chat",
	feature: "Chat",
	mount(e, t) {
		let i = null, a = n("div", {
			class: "chat-transcript",
			ariaLive: "polite",
			ariaRelevant: "additions text"
		});
		t.store.transcript.forEach((e) => a.append(v(e)));
		let o = n("textarea", {
			rows: 4,
			required: !0,
			placeholder: "Which papers discuss neural retrieval?",
			value: t.store.pendingChatQuestion ?? ""
		});
		t.store.pendingChatQuestion = null;
		let u = n("select", {}, ...[
			"",
			"lexical",
			"semantic",
			"hybrid"
		].map((e) => n("option", { value: e }, e || "Automatic"))), d = n("input", { placeholder: "cs.AI" }), f = n("input", { type: "checkbox" }), p = n("button", {
			class: "primary-button",
			type: "submit"
		}, "Ask Foundry"), m = c("Stop generation", () => i?.abort(), !0);
		m.hidden = !0;
		let h = n("form", { class: "chat-form" }, s("Question", o), n("details", { class: "chat-options" }, n("summary", {}, "Advanced retrieval"), n("div", { class: "chat-option-grid" }, s("Mode", u), s("Category", d), s("Agentic tools", f))), n("div", { class: "chat-actions" }, p, m)), g = n("div", { class: "suggestions" }, ...[
			"Compare the retrieval approaches represented in this corpus.",
			"Give me a concise literature survey of retrieval-augmented generation.",
			"Which recent cs.AI papers discuss vector search?"
		].map((e) => c(e, () => {
			o.value = e, o.focus();
		}, !0))), _ = n("div", { class: "chat-actions" }, c("Regenerate", () => {
			let e = [...t.store.transcript].reverse().find((e) => e.role === "user");
			e && (o.value = e.content, y());
		}, !0), c("Copy last answer", () => {
			let e = [...t.store.transcript].reverse().find((e) => e.role === "assistant");
			e && navigator.clipboard.writeText(e.content);
		}, !0), c("Clear", () => {
			t.store.clearTranscript(), a.replaceChildren();
		}, !0), c("Export Markdown", () => j("markdown"), !0), c("Export JSON", () => j("json"), !0));
		function v(e) {
			let t = n("div", { class: "citation-list" });
			E(t, e.citations ?? []);
			let r = n("p");
			return k(r, e.content, t, e.citations ?? []), n("article", { class: `chat-message ${e.role}` }, n("strong", {}, e.role === "user" ? "You" : "Grounded assistant"), e.stage ? n("small", { class: "stream-status" }, e.stage) : null, r, t, e.toolsUsed?.length ? n("small", { class: "tool-trace" }, `Tools: ${e.toolsUsed.join(", ")}`) : null);
		}
		async function y() {
			let e = o.value.trim();
			if (!e || i) return;
			let n = {
				message: e,
				retrievalMode: u.value || null,
				category: d.value.trim() || null,
				agentic: f.checked || null
			}, r = {
				role: "user",
				content: e
			}, s = {
				role: "assistant",
				content: "",
				citations: [],
				toolsUsed: [],
				streaming: !0,
				stage: "Starting retrieval…"
			};
			t.store.transcript.push(r, s), t.store.saveTranscript(), a.append(v(r));
			let c = b(s);
			a.append(c.article), c.article.scrollIntoView({
				behavior: "smooth",
				block: "end"
			}), i = new AbortController(), p.disabled = !0, m.hidden = !1;
			try {
				let e = !1;
				if (await O(n, i.signal, (t) => {
					x(t, c) && (e = !0);
				}), !e) throw Error("The chat stream ended without a completion event.");
			} catch (e) {
				s.streaming = !1, s.stage = void 0, e instanceof DOMException && e.name === "AbortError" ? s.content ||= "Generation stopped." : (s.content ||= `Chat failed: ${e instanceof Error ? e.message : "unknown error"}`, t.reportError(e)), C(c);
			} finally {
				i = null, p.disabled = !1, m.hidden = !0, t.store.saveTranscript(), o.disabled = !1, o.focus();
			}
		}
		function b(e) {
			let t = n("p", {}, "Waiting for retrieval…"), r = n("small", { class: "stream-status" }, e.stage ?? ""), i = n("div", { class: "citation-list" }), a = n("details", { class: "chat-options hidden" }, n("summary", {}, "Request diagnostics"), n("pre", { class: "diagnostic-block" }));
			return {
				article: n("article", { class: "chat-message assistant streaming" }, n("strong", {}, "Grounded assistant"), r, t, i, a),
				answer: t,
				stage: r,
				evidence: i,
				diagnostics: a,
				entry: e,
				started: performance.now(),
				firstToken: null,
				pendingFrame: null
			};
		}
		function x(e, n) {
			let r = e.data ?? {};
			if (e.event === "request") n.entry.diagnostics = { requestId: r.requestId };
			else if (e.event === "retrieval") n.entry.stage = "Searching the local corpus…";
			else if (e.event === "citations") n.entry.citations = e.data, n.entry.stage = `${n.entry.citations.length} evidence item(s) selected`, E(n.evidence, n.entry.citations), D(n.evidence, n.entry.citations);
			else if (e.event === "delta") n.firstToken ??= performance.now(), n.entry.content += String(r.text ?? ""), n.entry.stage = `Foundry is streaming · first token ${Math.round(n.firstToken - n.started)} ms`;
			else if (e.event === "metrics") {
				n.entry.diagnostics = {
					...r,
					clientTtftMilliseconds: n.firstToken == null ? null : Math.round(n.firstToken - n.started)
				};
				let e = n.diagnostics.querySelector("pre");
				e && (e.textContent = JSON.stringify(n.entry.diagnostics, null, 2)), n.diagnostics.classList.toggle("hidden", !t.store.preferences.diagnostics);
			} else if (e.event === "complete") {
				n.entry.content = String(r.answer ?? n.entry.content), n.entry.toolsUsed = r.toolsUsed ?? [], n.entry.groundingStatus = String(r.groundingStatus ?? "GROUNDED"), n.entry.cacheStatus = String(r.cacheStatus ?? "BYPASS"), n.entry.streaming = !1;
				let e = Math.round(performance.now() - n.started), t = n.firstToken == null ? "local response" : `first token ${Math.round(n.firstToken - n.started)} ms`;
				return n.entry.stage = `Completed in ${e} ms · ${t}`, C(n), !0;
			} else if (e.event === "error") throw Error(String(r.message ?? "Chat stream failed"));
			return S(n), !1;
		}
		function S(e) {
			if (e.pendingFrame != null) return;
			let t = A(a);
			e.pendingFrame = requestAnimationFrame(() => {
				e.pendingFrame = null, T(e), t && e.article.scrollIntoView({ block: "end" });
			});
		}
		function C(e) {
			e.pendingFrame != null && cancelAnimationFrame(e.pendingFrame), e.pendingFrame = null, T(e);
		}
		function T(e) {
			e.stage.textContent = e.entry.stage ?? "", e.stage.hidden = !e.entry.stage, e.article.classList.toggle("streaming", !!e.entry.streaming), k(e.answer, e.entry.content || (e.entry.streaming ? "Waiting for Foundry…" : ""), e.evidence, e.entry.citations ?? []), e.entry.toolsUsed?.length && !e.article.querySelector(".tool-trace") && e.article.append(n("small", { class: "tool-trace" }, `Tools: ${e.entry.toolsUsed.join(", ")}`));
		}
		function E(e, t) {
			e.replaceChildren(), t.length && e.append(n("strong", {}, "Evidence (metadata and abstracts only)"), ...t.map((e, t) => n("details", {
				class: "evidence-card",
				dataset: { evidence: e.arxivId }
			}, n("summary", {}, `[${t + 1}] ${e.title} · ${e.retrievalSource} · ${e.score?.toFixed(3) ?? "n/a"}`), l(`arXiv:${e.arxivId}`, e.url), n("div", { class: "evidence-extra" }, "Loading metadata…"))));
		}
		async function D(e, r) {
			await Promise.all(r.map(async (r) => {
				try {
					let i = await t.api.get(`/papers/${encodeURIComponent(r.arxivId)}`), a = e.querySelector(`[data-evidence="${CSS.escape(r.arxivId)}"] .evidence-extra`);
					a && a.replaceChildren(n("p", {}, i.abstractText.slice(0, 500) + (i.abstractText.length > 500 ? "…" : "")), n("small", {}, i.authors.join(", ")));
				} catch {}
			}));
		}
		function k(e, t, r, i) {
			e.replaceChildren();
			let a = /\[arXiv:([^\]\s]+)]/gi, o = 0;
			for (let s; s = a.exec(t);) {
				e.append(t.slice(o, s.index));
				let c = s[1] ?? "", l = w(c, i.find((e) => e.arxivId.toLowerCase() === c.toLowerCase())?.url), u = l ? n("a", {
					href: l,
					target: "_blank",
					rel: "noopener noreferrer",
					class: "inline-citation",
					ariaLabel: `Open arXiv paper ${c}`
				}, s[0]) : n("span", { class: "inline-citation" }, s[0]), d = (e) => r.querySelector(`[data-evidence="${CSS.escape(c)}"]`)?.classList.toggle("highlight", e);
				u.addEventListener("mouseenter", () => d(!0)), u.addEventListener("mouseleave", () => d(!1)), u.addEventListener("focus", () => d(!0)), u.addEventListener("blur", () => d(!1)), e.append(u), o = a.lastIndex;
			}
			e.append(t.slice(o));
		}
		function A(e) {
			return e.scrollHeight - e.scrollTop - e.clientHeight < 96;
		}
		function j(e) {
			let r = e === "json" ? JSON.stringify({
				schemaVersion: 1,
				transcript: t.store.transcript
			}, null, 2) : t.store.transcript.map((e) => `## ${e.role === "user" ? "You" : "Grounded assistant"}\n\n${e.content}\n\n${(e.citations ?? []).map((e) => `- [arXiv:${e.arxivId}](${e.url})`).join("\n")}`).join("\n\n"), i = URL.createObjectURL(new Blob([r], { type: e === "json" ? "application/json" : "text/markdown" }));
			n("a", {
				href: i,
				download: `arxiv-rag-transcript.${e === "json" ? "json" : "md"}`
			}).click(), URL.revokeObjectURL(i);
		}
		return h.addEventListener("submit", (e) => {
			e.preventDefault(), y();
		}), e.replaceChildren(r("Chat", "Grounded chat streams tokens, exposes retrieval stages, and links every authoritative citation to arXiv.", g, _, a, h, n("p", { class: "safe-config-note" }, "Answers are grounded in imported metadata and abstracts, not full paper PDFs."))), () => i?.abort();
	}
}, A = {
	title: "Embeddings & Indexes",
	feature: "Vector Stores",
	mount(e, t) {
		let l = !1, u = n("div"), d = async () => {
			try {
				let [e, r] = await Promise.all([t.api.get("/embeddings/status"), t.api.get("/embeddings/jobs")]);
				if (l) return;
				let s = Math.max(0, e.papers - e.checkpointedPapers), m = e.jobTotalPapers ? e.jobPapersScanned / e.jobTotalPapers : 0, h = e.jobStartedAt == null ? 0 : Math.max(1, (Date.now() - new Date(e.jobStartedAt).getTime()) / 1e3), g = h ? e.jobPapersScanned / h : 0, v = g > 0 ? Math.max(0, e.jobTotalPapers - e.jobPapersScanned) / g : null;
				u.replaceChildren(n("div", { class: "health-panel" }, n("div", {}, i(e.jobStatus, e.jobStatus === "FAILED" ? "error" : e.jobStatus === "RUNNING" ? "working" : "ok"), n("h3", {}, `${e.jobPapersScanned}/${e.jobTotalPapers} papers scanned`), n("progress", {
					max: 1,
					value: m,
					ariaLabel: "Embedding progress"
				}), n("small", {}, `${g.toFixed(1)} papers/s${v == null ? "" : ` · ETA ${M(v)}`}`)), c("Refresh", d)), o(a("Papers", e.papers), a("Current embeddings", e.checkpointedPapers), a("Missing/stale", s), a("Vector rows", e.vectorRows), a("Coverage", `${e.vectorCoveragePercent.toFixed(1)}%`), a("Dimensions", e.embeddingDimension)), n("div", { class: "config-grid" }, n("div", { class: "config-item" }, n("span", {}, "Embedding model"), n("strong", {}, e.embeddingModel)), n("div", { class: "config-item" }, n("span", {}, "Retrieval document"), n("strong", {}, e.retrievalDocumentVersion)), n("div", { class: "config-item" }, n("span", {}, "Index"), n("strong", {}, e.indexVersion)), n("div", { class: "config-item" }, n("span", {}, "Provider queue"), n("strong", {}, `${e.providerCapacity.bulkEmbeddingWaiting} bulk / ${e.providerCapacity.queryEmbeddingWaiting + e.providerCapacity.chatWaiting} interactive waiting`)), n("div", { class: "config-item" }, n("span", {}, "Vector table size"), n("strong", {}, j(e.indexDiagnostics.tableBytes))), n("div", { class: "config-item" }, n("span", {}, "Vector indexes"), n("strong", {}, j(e.indexDiagnostics.indexBytes)))), f(), n("h3", {}, "Recent durable jobs"), r.length ? n("div", { class: "result-list" }, ...r.map(p)) : _("No embedding jobs yet."));
			} catch (e) {
				l || u.replaceChildren(v(e, d));
			}
		}, f = () => {
			let e = n("input", {
				type: "number",
				min: 1,
				max: 5e4,
				value: 100
			}), r = n("form", { class: "job-controls" }, s("Maximum papers (only changed content is sent)", e), n("button", {
				class: "primary-button",
				type: "submit"
			}, "Embed changed"), n("small", {}, "Foundry cost depends on uncached changed documents; unchanged checkpoints are skipped."));
			return r.addEventListener("submit", async (n) => {
				n.preventDefault();
				let r = Number(e.value);
				if (window.confirm(`Embed changed content for at most ${r.toLocaleString()} papers? Unchanged documents are free of model calls; changed documents consume Foundry embedding quota and cost.`)) try {
					await t.api.post("/embeddings/jobs", {
						maxPapers: r,
						dryRun: !1
					}), await d();
				} catch (e) {
					t.reportError(e);
				}
			}), r;
		}, p = (e) => n("article", { class: "result-card" }, n("div", { class: "card-heading" }, i(e.status, e.status === "FAILED" ? "error" : e.status === "RUNNING" ? "working" : "ok"), n("strong", {}, e.jobId)), n("span", {}, `${e.papersScanned}/${e.totalPapers} scanned · ${e.embedded} embedded · ${e.skipped} skipped · ${e.providerRequests} provider calls`), e.lastError ? n("p", { class: "error-copy" }, e.lastError) : null, n("div", { class: "chat-actions" }, ["RUNNING", "QUEUED"].includes(e.status) ? c("Cancel", () => void t.api.delete(`/embeddings/jobs/${e.jobId}`).then(d), !0) : null, [
			"FAILED",
			"CANCELLED",
			"INTERRUPTED"
		].includes(e.status) ? c("Resume", () => void t.api.post(`/embeddings/jobs/${e.jobId}/resume`).then(d), !0) : null));
		e.replaceChildren(r("Embeddings & Indexes", "Current coverage, version facts, and safe durable job controls.", u)), u.replaceChildren(g("Loading embedding state…")), d();
		let m = window.setInterval(d, 4e3);
		return () => {
			l = !0, window.clearInterval(m);
		};
	}
};
function j(e) {
	return e === null ? "Unavailable for this database" : new Intl.NumberFormat(void 0, {
		style: "unit",
		unit: "megabyte",
		maximumFractionDigits: 1
	}).format(e / 1048576);
}
function M(e) {
	return Number.isFinite(e) ? e < 60 ? `${Math.ceil(e)}s` : `${Math.floor(e / 60)}m ${Math.ceil(e % 60)}s` : "unknown";
}
//#endregion
//#region src/frontend/pages/benchmarks-page.ts
var N = {
	title: "Benchmarks",
	feature: "Benchmarks",
	mount(e, t) {
		let s = n("div", {}, g("Loading saved benchmark runs…")), l = /* @__PURE__ */ new Map(), u = async (e) => {
			let n = l.get(e);
			if (n) return n;
			let r = await t.api.get(`/benchmarks/${encodeURIComponent(e)}`);
			return l.set(e, r), r;
		}, d = async () => {
			try {
				let e = await t.api.get("/benchmarks");
				if (!e.length) {
					s.replaceChildren(_("No benchmark JSON exists under benchmarks/results yet. Use the bounded CLI command in docs/operations/benchmarking.md."));
					return;
				}
				let r = P(e), i = P(e);
				e[1] && (i.value = e[1].name);
				let a = n("div", { class: "preview-result" }, _("Choose two runs and compare them.")), o = async () => {
					a.replaceChildren(g("Comparing benchmark runs…"));
					try {
						a.replaceChildren(p(await u(r.value), await u(i.value)));
					} catch (e) {
						a.replaceChildren(v(e, o));
					}
				}, l = n("div", { class: "job-controls" }, r, i, c("Compare runs", () => void o()), n("small", {}, "Live Foundry execution remains a bounded CLI/manual-CI action so opening this page cannot spend tokens.")), d = await Promise.all(e.map(async (e) => f(e, await u(e.name))));
				s.replaceChildren(l, a, n("h3", {}, "Saved runs"), n("div", { class: "result-list" }, ...d));
			} catch (e) {
				s.replaceChildren(v(e, d));
			}
		};
		function f(e, t) {
			let r = t.summaries[0], s = t.results.filter((e) => !F(e)), l = n("div", { class: "hidden" });
			return n("article", { class: "result-card" }, n("div", { class: "card-heading" }, n("strong", {}, e.name), i(e.variant)), n("span", {}, `${new Date(e.modifiedAt).toLocaleString()} · commit ${e.applicationCommit || "working-tree"}`), r ? o(a("Search p95", `${r.searchP95Ms ?? "n/a"} ms`), a("TTFT p95", `${r.ttftP95Ms} ms`), a("Total p95", `${r.latencyP95Ms} ms`), a("Recall@k", L(r.meanRecallAtK)), a("nDCG", L(r.meanNdcg ?? 0)), a("Citation validity", L(r.citationValidityRate))) : null, n("div", { class: "chat-actions" }, c(`${s.length} failed case(s)`, () => {
				l.classList.toggle("hidden"), l.replaceChildren(...s.length ? s.map(I) : [_("All cases passed.")]);
			}, !0), c("Export JSON", () => R(e.name, JSON.stringify(t, null, 2), "application/json"), !0), c("Export CSV", () => R(e.name.replace(/\.json$/, ".csv"), z(t), "text/csv"), !0)), l);
		}
		function p(e, t) {
			let r = e.summaries[0], i = t.summaries[0];
			if (!r || !i) return _("One of the selected runs has no summary.");
			let a = [
				[
					"Search p95",
					r.searchP95Ms ?? 0,
					i.searchP95Ms ?? 0,
					"ms"
				],
				[
					"TTFT p95",
					r.ttftP95Ms,
					i.ttftP95Ms,
					"ms"
				],
				[
					"Total p95",
					r.latencyP95Ms,
					i.latencyP95Ms,
					"ms"
				],
				[
					"Recall@k",
					r.meanRecallAtK,
					i.meanRecallAtK,
					"quality"
				],
				[
					"nDCG",
					r.meanNdcg ?? 0,
					i.meanNdcg ?? 0,
					"quality"
				],
				[
					"Citation validity",
					r.citationValidityRate,
					i.citationValidityRate,
					"quality"
				]
			];
			return n("article", { class: "result-card" }, n("h3", {}, `${r.deployment} compared with ${i.deployment}`), ...a.map(([e, t, n, r]) => m(e, t, n, r)), n("small", {}, `Prompt ${e.promptVersion} / ${t.promptVersion} · retrieval ${e.retrievalPolicyVersion} / ${t.retrievalPolicyVersion}`));
		}
		function m(e, t, r, i) {
			let a = Math.max(t, r, 1e-4), o = (e) => i === "quality" ? L(e) : `${e.toFixed(0)} ms`;
			return n("div", { class: "benchmark-row" }, n("strong", {}, e), n("div", {
				class: "benchmark-bar",
				style: `--value:${t / a}`
			}, o(t)), n("div", {
				class: "benchmark-bar comparison",
				style: `--value:${r / a}`
			}, o(r)));
		}
		return e.replaceChildren(r("Benchmarks", "Compare saved cold/warm latency, retrieval quality, citation validity, tokens, and estimated cost.", s)), d(), () => {
			l = /* @__PURE__ */ new Map();
		};
	}
};
function P(e) {
	return n("select", {}, ...e.map((e) => n("option", { value: e.name }, e.name)));
}
function F(e) {
	return e.expectedPapersPresent && e.noEvidenceCorrect && e.inlineCitationsValid && e.citationUrlsValid && e.unsupportedCitationCount === 0;
}
function I(e) {
	return n("article", { class: "result-card" }, n("strong", {}, e.caseId), n("span", {}, `Recall ${L(e.recallAtK)} · nDCG ${L(e.normalizedDiscountedCumulativeGain ?? 0)} · ${e.latencyMilliseconds} ms`), n("small", {}, `Citations: ${e.citedArxivIds.join(", ") || "none"}`));
}
function L(e) {
	return `${(100 * (Number.isFinite(e) ? e : 0)).toFixed(1)}%`;
}
function R(e, t, r) {
	let i = URL.createObjectURL(new Blob([t], { type: r }));
	n("a", {
		href: i,
		download: e
	}).click(), URL.revokeObjectURL(i);
}
function z(e) {
	let t = [
		"caseId",
		"searchMilliseconds",
		"ttftMilliseconds",
		"latencyMilliseconds",
		"recallAtK",
		"normalizedDiscountedCumulativeGain",
		"citationUrlsValid",
		"unsupportedCitationCount",
		"expectedPapersPresent",
		"noEvidenceCorrect",
		"inlineCitationsValid"
	];
	return [t.join(","), ...e.results.map((e) => t.map((t) => JSON.stringify(e[t] ?? "")).join(","))].join("\n");
}
//#endregion
//#region src/frontend/main.ts
var B = /* @__PURE__ */ new Map([
	["system-status", y],
	["import", b],
	["papers", S],
	["search", T],
	["chat", k],
	["embeddings", A],
	["vector-stores", A],
	["benchmarks", N],
	["settings", {
		title: "Settings",
		mount(e, { store: t }) {
			let i = t.runtime, a = t.preferences, o = n("form", { class: "preview-form" }, s("Default search mode", n("select", { name: "searchMode" }, ...[
				"auto",
				"lexical",
				"semantic",
				"hybrid"
			].map((e) => n("option", {
				value: e,
				selected: a.searchMode === e
			}, e)))), s("Default result count", n("input", {
				name: "resultCount",
				type: "number",
				min: 1,
				max: 50,
				value: a.resultCount
			})), s("Theme", n("select", { name: "theme" }, ...[
				"system",
				"light",
				"dark"
			].map((e) => n("option", {
				value: e,
				selected: a.theme === e
			}, e)))), s("Show developer diagnostics", n("input", {
				name: "diagnostics",
				type: "checkbox",
				checked: a.diagnostics
			})), s("Save chat transcript locally", n("input", {
				name: "saveTranscript",
				type: "checkbox",
				checked: a.saveTranscript
			})), n("button", {
				class: "primary-button",
				type: "submit"
			}, "Save preferences"));
			o.addEventListener("submit", (e) => {
				e.preventDefault();
				let n = new FormData(o);
				t.updatePreferences({
					searchMode: String(n.get("searchMode")),
					resultCount: Number(n.get("resultCount")),
					theme: String(n.get("theme")),
					diagnostics: n.get("diagnostics") === "on",
					saveTranscript: n.get("saveTranscript") === "on"
				});
			});
			let c = i?.facts;
			return e.replaceChildren(r("Settings", "Runtime facts are read-only. Credentials are never returned or accepted here.", n("div", { class: "config-grid" }, n("div", { class: "config-item" }, n("span", {}, "Profile"), n("strong", {}, i?.configuredProfile ?? "Unavailable")), n("div", { class: "config-item" }, n("span", {}, "Foundry host"), n("strong", {}, c?.endpointHost ?? "Unavailable")), n("div", { class: "config-item" }, n("span", {}, "Authentication"), n("strong", {}, c?.authenticationMode ?? "Unavailable")), n("div", { class: "config-item" }, n("span", {}, "Credential"), n("strong", {}, c?.credentialConfigured ? "Configured" : "Missing")), n("div", { class: "config-item" }, n("span", {}, "Embedding"), n("strong", {}, `${c?.embeddingDeployment ?? "?"} / ${c?.embeddingDimensions ?? "?"}d`)), n("div", { class: "config-item" }, n("span", {}, "Admin protection"), n("strong", {}, c?.adminProtection ?? "Unavailable"))), o)), () => {};
		}
	}]
]), V = {
	"system-status": "S",
	import: "I",
	papers: "P",
	search: "Q",
	chat: "C",
	embeddings: "E",
	benchmarks: "B",
	settings: "G"
}, H = Y("navigation"), U = Y("page-title"), W = Y("page-content"), G = Y("environment-pill"), K = Y("global-error"), q = () => {}, J = {
	api: t,
	store: p,
	navigate: h,
	reportError: Q
};
function Y(e) {
	let t = document.getElementById(e);
	if (!t) throw Error(`Missing UI host #${e}`);
	return t;
}
function X(e) {
	return !e.feature || p.runtime?.features.find((t) => t.name === e.feature)?.enabled !== !1;
}
function ee(e) {
	H.replaceChildren(...[...B.entries()].filter(([e]) => e !== "vector-stores").filter(([, e]) => X(e)).map(([t, r]) => n("a", {
		class: `nav-link ${t === e ? "active" : ""}`,
		href: `#/${t}`,
		ariaCurrent: t === e ? "page" : null
	}, n("span", {}, V[t] ?? "•"), n("span", {}, r.title))));
}
function Z() {
	q();
	let e = m(), t = B.get(e) ?? y;
	if (U.textContent = t.title, ee(e), !X(t)) {
		W.replaceChildren(n("div", {}, n("h2", {}, t.title), n("p", {}, "This capability is disabled in the active backend profile."))), q = () => {};
		return;
	}
	q = t.mount(W, J);
}
function Q(t) {
	let n = t instanceof e ? t.requestId : null;
	K.textContent = `${t instanceof Error ? t.message : "The operation failed."}${n ? ` Request ID: ${n}` : ""}`, K.classList.remove("hidden"), K.tabIndex = -1, K.focus();
}
async function $() {
	document.documentElement.dataset.theme = p.preferences.theme;
	try {
		p.runtime = await t.get("/capabilities"), G.textContent = `${p.runtime.configuredProfile} · ${p.runtime.facts.provider}`, G.classList.add("ok");
	} catch (e) {
		G.textContent = "Backend unavailable", G.classList.add("error"), Q(e);
	}
	Z();
}
window.addEventListener("hashchange", Z), window.addEventListener("unhandledrejection", (e) => Q(e.reason)), document.getElementById("health-refresh-button")?.addEventListener("click", () => void $()), $();
//#endregion

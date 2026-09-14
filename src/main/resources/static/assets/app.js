const pages = [
    {
        id: "system-status",
        title: "System Status",
        icon: "S",
        summary: "Health and runtime readiness for the Spring Boot backend.",
        details: ["Backend health probe", "Application identity", "Future database/vector-store status"],
        metrics: [
            ["Backend", "Checking..."],
            ["Relational DB", "Configured"],
            ["Vector store", "pgvector"]
        ]
    },
    {
        id: "import",
        title: "Import",
        icon: "I",
        summary: "Dataset import controls and statistics for the arXiv metadata slice.",
        details: ["Upload or reference the dataset file", "Show category/date filters", "Display import progress and counts"]
    },
    {
        id: "papers",
        title: "Papers",
        icon: "P",
        summary: "Browse canonical paper records stored in PostgreSQL.",
        details: ["List imported papers", "Filter by category, author, and submitted date", "Open paper metadata and citations"]
    },
    {
        id: "search",
        title: "Search",
        icon: "Q",
        summary: "Search the imported paper corpus using the configured embedding model and local vector store.",
        details: ["Run top-K semantic search", "Show vector scores", "Resolve vector hits back to paper records"]
    },
    {
        id: "chat",
        title: "Chat",
        icon: "C",
        summary: "Ask questions grounded in the imported arXiv papers through Microsoft AI Foundry.",
        details: ["Ask research questions", "Render cited source papers", "Show retrieval/debug context"]
    },
    {
        id: "vector-stores",
        title: "Vector Stores",
        icon: "V",
        summary: "Inspect and switch between pgvector, Milvus, and Chroma experiments.",
        details: ["Show active backend", "Compare index/config settings", "Display ingestion coverage by store"]
    },
    {
        id: "benchmarks",
        title: "Benchmarks",
        icon: "B",
        summary: "Capture ingestion, query latency, and quality comparison results.",
        details: ["Track p50/p95 retrieval latency", "Compare vector-store operations", "Record answer-quality evaluations"]
    },
    {
        id: "settings",
        title: "Settings",
        icon: "G",
        summary: "Central location for runtime configuration visibility.",
        details: ["Show active Spring profiles", "Expose non-secret feature flags", "Keep secrets and tokens hidden"]
    }
];

const navigation = document.querySelector("#navigation");
const pageTitle = document.querySelector("#page-title");
const pageContent = document.querySelector("#page-content");
const environmentPill = document.querySelector("#environment-pill");
const globalError = document.querySelector("#global-error");
const healthRefreshButton = document.querySelector("#health-refresh-button");
let runtimeConfig = null;
let runtimeConfigError = null;
let backendHealth = {
    status: "checking",
    message: "Checking backend health...",
    checkedAt: null
};
let embeddingStatus = null;
let embeddingStatusError = null;
let chatTranscript = [];
let chatAbortController = null;
let chatRenderPending = false;

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function safeArxivCitationUrl(citation) {
    const arxivId = String(citation?.arxivId ?? "").trim();
    const candidate = citation?.url ?? `https://arxiv.org/abs/${arxivId}`;
    try {
        const url = new URL(candidate);
        if (url.protocol !== "https:" || url.hostname !== "arxiv.org" || !url.pathname.startsWith("/abs/")) {
            return null;
        }
        return url.href;
    } catch {
        return null;
    }
}

function renderChatCitation(citation) {
    const score = citation.score == null ? "" : ` · score ${Number(citation.score).toFixed(3)}`;
    const label = `[arXiv:${escapeHtml(citation.arxivId)}] ${escapeHtml(citation.title)}${score}`;
    const url = safeArxivCitationUrl(citation);
    if (!url) {
        return `<span class="citation">${label}</span>`;
    }
    return `<a class="citation citation-link" href="${escapeHtml(url)}" target="_blank" rel="noopener noreferrer" aria-label="Open cited paper on arXiv">${label}<span class="external-link-icon" aria-hidden="true">↗</span></a>`;
}

function formatCheckedAt(value) {
    return value ? new Date(value).toLocaleTimeString([], {hour: "2-digit", minute: "2-digit", second: "2-digit"}) : "Not checked yet";
}

function showGlobalError(message) {
    globalError.textContent = message;
    globalError.classList.remove("hidden");
}

function clearGlobalError() {
    globalError.textContent = "";
    globalError.classList.add("hidden");
}

function renderNavigation(activePageId) {
    navigation.innerHTML = pages
        .map((page) => `
            <a class="nav-link ${page.id === activePageId ? "active" : ""}" href="#/${page.id}">
                <span>${page.icon}</span>
                <span>${page.title}</span>
            </a>
        `)
        .join("");
}

function renderPage() {
    const route = window.location.hash.replace("#/", "") || "system-status";
    const page = pages.find((candidate) => candidate.id === route) ?? pages[0];

    pageTitle.textContent = page.title;
    renderNavigation(page.id);

    if (page.id === "settings") {
        renderSettingsPage(page);
        return;
    }

    if (page.id === "system-status") {
        renderSystemStatusPage(page);
        return;
    }

    if (page.id === "search") {
        renderSearchPage(page);
        return;
    }

    if (page.id === "chat") {
        renderChatPage(page);
        return;
    }

    if (page.id === "vector-stores") {
        renderEmbeddingStatusPage(page);
        return;
    }

    const metrics = page.metrics
        ? `<div class="metric-grid">${page.metrics.map(([label, value]) => `
            <div class="metric">
                <strong>${value}</strong>
                <span>${label}</span>
            </div>
        `).join("")}</div>`
        : "";

    pageContent.innerHTML = `
        <div class="hero-grid">
            <div>
                <h2>${page.title}</h2>
                <p class="placeholder-copy">${page.summary}</p>
                <ul class="feature-list">
                    ${page.details.map((detail) => `<li><strong>${detail}</strong><br><span>Placeholder area reserved for the upcoming feature story.</span></li>`).join("")}
                </ul>
            </div>
            <aside>
                <span class="status-badge">Placeholder</span>
                ${metrics}
            </aside>
        </div>
    `;
}

function renderSystemStatusPage(page) {
    const isUp = backendHealth.status === "UP";
    const isChecking = backendHealth.status === "checking";
    const statusLabel = backendHealth.status === "checking" ? "Checking..." : backendHealth.status;

    pageContent.innerHTML = `
        <div class="hero-grid">
            <div>
                <h2>${page.title}</h2>
                <p class="placeholder-copy">${page.summary}</p>
                <div class="health-panel ${isUp ? "ok" : isChecking ? "" : "error"}">
                    <div>
                        <span class="status-badge ${isUp ? "ok" : isChecking ? "" : "error"}">${escapeHtml(statusLabel)}</span>
                        <h3>Backend API</h3>
                        <p>${escapeHtml(backendHealth.message)}</p>
                        <small>Last checked: ${escapeHtml(formatCheckedAt(backendHealth.checkedAt))}</small>
                    </div>
                    <button class="primary-button" type="button" data-health-refresh>Refresh</button>
                </div>
                <ul class="feature-list">
                    ${page.details.map((detail) => `<li><strong>${detail}</strong><br><span>Placeholder area reserved for the upcoming feature story.</span></li>`).join("")}
                </ul>
            </div>
            <aside>
                <span class="status-badge ${isUp ? "ok" : isChecking ? "" : "error"}">${isUp ? "Ready" : isChecking ? "Checking" : "Needs attention"}</span>
                <div class="metric-grid">
                    <div class="metric">
                        <strong>${escapeHtml(statusLabel)}</strong>
                        <span>Backend</span>
                    </div>
                    <div class="metric">
                        <strong>Configured</strong>
                        <span>Relational DB</span>
                    </div>
                    <div class="metric">
                        <strong>pgvector</strong>
                        <span>Vector store</span>
                    </div>
                </div>
            </aside>
        </div>
    `;
}

function renderSettingsPage(page) {
    const configMarkup = runtimeConfig
        ? `
            <div class="config-grid">
                <div class="config-item">
                    <span>Application</span>
                    <strong>${escapeHtml(runtimeConfig.applicationName)}</strong>
                </div>
                <div class="config-item">
                    <span>Configured profile</span>
                    <strong>${escapeHtml(runtimeConfig.configuredProfile)}</strong>
                </div>
                <div class="config-item">
                    <span>Active Spring profiles</span>
                    <strong>${escapeHtml(runtimeConfig.activeProfiles.join(", "))}</strong>
                </div>
            </div>
            <h3>Feature flags</h3>
            <ul class="feature-flags">
                ${runtimeConfig.features.map((feature) => `
                    <li>
                        <span>${escapeHtml(feature.name)}</span>
                        <strong class="${feature.enabled ? "enabled" : "disabled"}">
                            ${feature.enabled ? "Enabled" : "Disabled"}
                        </strong>
                    </li>
                `).join("")}
            </ul>
            <p class="safe-config-note">Only whitelisted runtime values are shown. Secrets, API keys, passwords, and tokens are never returned by this endpoint.</p>
        `
        : `<p class="placeholder-copy">${runtimeConfigError ?? "Loading runtime configuration..."}</p>`;

    pageContent.innerHTML = `
        <div>
            <h2>${page.title}</h2>
            <p class="placeholder-copy">${page.summary}</p>
            ${configMarkup}
        </div>
    `;
}

function renderEmbeddingStatusPage(page) {
    const jobStatus = embeddingStatus?.jobStatus ?? "IDLE";
    const jobProgress = embeddingStatus
        ? `${embeddingStatus.jobPapersScanned ?? 0}/${embeddingStatus.jobTotalPapers ?? embeddingStatus.papers} papers scanned · ${embeddingStatus.jobEmbedded ?? 0} embedded · ${embeddingStatus.jobSkipped ?? 0} skipped`
        : "No ingestion job status available.";
    const statusMarkup = embeddingStatus
        ? `
            <div class="metric-grid">
                <div class="metric">
                    <strong>${escapeHtml(embeddingStatus.papers)}</strong>
                    <span>Papers</span>
                </div>
                <div class="metric">
                    <strong>${escapeHtml(embeddingStatus.checkpointedPapers)}</strong>
                    <span>Checkpointed papers</span>
                </div>
                <div class="metric">
                    <strong>${escapeHtml(embeddingStatus.checkpoints)}</strong>
                    <span>Total checkpoints</span>
                </div>
                <div class="metric">
                    <strong>${escapeHtml(embeddingStatus.vectorRows)}</strong>
                    <span>Vector rows</span>
                </div>
                <div class="metric">
                    <strong>${escapeHtml(embeddingStatus.vectorCoveragePercent.toFixed(1))}%</strong>
                    <span>Vector coverage</span>
                </div>
                <div class="metric">
                    <strong>${escapeHtml(jobStatus)}</strong>
                    <span>Current job</span>
                </div>
            </div>
            <div class="config-grid">
                <div class="config-item">
                    <span>Embedding model</span>
                    <strong>${escapeHtml(embeddingStatus.embeddingModel)}</strong>
                </div>
                <div class="config-item">
                    <span>Embedding dimension</span>
                    <strong>${escapeHtml(embeddingStatus.embeddingDimension)}</strong>
                </div>
            </div>
            <form class="job-controls" data-embedding-job-form>
                <label>
                    Bounded run size
                    <input name="maxPapers" type="number" min="1" max="5000" value="25">
                </label>
                <button class="primary-button" type="submit">Start embedding job</button>
                <button class="icon-button" type="button" data-embedding-job-cancel title="Cancel current embedding job" aria-label="Cancel current embedding job">&#9632;</button>
            </form>
        `
        : `<p class="placeholder-copy">${embeddingStatusError ?? "Loading embedding checkpoint status..."}</p>`;

    pageContent.innerHTML = `
        <div class="hero-grid">
            <div>
                <h2>${page.title}</h2>
                <p class="placeholder-copy">Embedding checkpointing and vector persistence are active. Use the bounded <code>embed-papers --max-papers=N</code> command or start a background job through the local API.</p>
                <div class="health-panel ${embeddingStatusError ? "error" : ""}">
                    <div>
                        <span class="status-badge ${embeddingStatusError || jobStatus === "FAILED" ? "error" : jobStatus === "RUNNING" ? "working" : "ok"}">${embeddingStatusError ? "Unavailable" : escapeHtml(jobStatus)}</span>
                        <h3>Embedding ingestion status</h3>
                        <p>${embeddingStatusError ? escapeHtml(embeddingStatusError) : escapeHtml(jobProgress)}</p>
                    </div>
                    <button class="primary-button" type="button" data-embedding-status-refresh>Refresh</button>
                </div>
            </div>
            <aside>
                ${statusMarkup}
            </aside>
        </div>
    `;
}

function renderSearchPage(page) {
    pageContent.innerHTML = `
        <div class="search-layout">
            <div>
                <h2>${page.title}</h2>
                <p class="placeholder-copy">Semantic retrieval is performed locally. The browser calls Spring Boot; Spring Boot calls the Foundry embedding deployment and searches pgvector.</p>
                <form class="preview-form" data-search-form>
                    <label>
                        Query
                        <input name="query" type="text" placeholder="papers about neural retrieval" required>
                    </label>
                    <label>
                        Results
                        <input name="topK" type="number" value="5" min="1" max="50">
                    </label>
                    <label>
                        Search mode
                        <select name="mode">
                            <option value="semantic">Semantic (pgvector)</option>
                            <option value="lexical">Lexical (PostgreSQL full text)</option>
                            <option value="hybrid">Hybrid (semantic + lexical)</option>
                        </select>
                    </label>
                    <label>
                        Category (optional)
                        <input name="category" type="text" placeholder="cs.AI">
                    </label>
                    <div class="date-fields">
                        <label>
                            From date
                            <input name="fromDate" type="date">
                        </label>
                        <label>
                            To date
                            <input name="toDate" type="date">
                        </label>
                    </div>
                    <button class="primary-button" type="submit">Search papers</button>
                </form>
            </div>
            <aside class="preview-result" id="search-result">
                <span class="status-badge">Ready</span>
                <p class="placeholder-copy">Submit a query to retrieve papers from the local vector store.</p>
            </aside>
        </div>
    `;
}

function renderChatPage(page) {
    const transcriptMarkup = chatTranscript.length === 0
        ? `<p class="placeholder-copy">Ask a question about the imported papers. The model will request local retrieval tools before answering.</p>`
        : chatTranscript.map(entry => {
            const citations = entry.citations?.length
                ? `<div class="citation-list"><strong>Sources</strong>${entry.citations.map(renderChatCitation).join("")}</div>`
                : "";
            const tools = entry.toolsUsed?.length
                ? `<small class="tool-trace">Tools: ${entry.toolsUsed.map(escapeHtml).join(", ")}</small>`
                : "";
            const status = entry.status
                ? `<small class="stream-status">${escapeHtml(entry.status)}</small>`
                : "";
            return `<div class="chat-message ${entry.role === "user" ? "user" : "assistant"} ${entry.streaming ? "streaming" : ""}">
                <strong>${entry.role === "user" ? "You" : "Grounded assistant"}</strong>
                ${status}<p>${escapeHtml(entry.content || (entry.streaming ? "Waiting for Foundry…" : ""))}</p>${citations}${tools}
            </div>`;
        }).join("");

    pageContent.innerHTML = `
        <div>
            <h2>${page.title}</h2>
            <p class="placeholder-copy">${page.summary}</p>
            <div class="chat-transcript">${transcriptMarkup}</div>
            <form class="chat-form" data-chat-form>
                <label>
                    Question
                    <textarea name="message" rows="4" placeholder="Which papers discuss neural retrieval?" required></textarea>
                </label>
                <details class="chat-options">
                    <summary>Retrieval options</summary>
                    <div class="chat-option-grid">
                        <label>Mode
                            <select name="retrievalMode">
                                <option value="">Automatic</option>
                                <option value="semantic">Semantic</option>
                                <option value="lexical">Lexical</option>
                                <option value="hybrid">Hybrid</option>
                            </select>
                        </label>
                        <label>Category <input name="category" placeholder="cs.AI"></label>
                        <label>From <input name="fromDate" type="date"></label>
                        <label>To <input name="toDate" type="date"></label>
                        <label class="checkbox-label"><input name="agentic" type="checkbox"> Agentic tool mode</label>
                    </div>
                </details>
                <div class="chat-actions">
                    <button class="primary-button" type="submit" ${chatAbortController ? "disabled" : ""}>Ask Foundry</button>
                    ${chatAbortController ? `<button class="icon-button chat-cancel" type="button" data-chat-cancel>Cancel</button>` : ""}
                </div>
            </form>
        </div>
    `;
}

async function submitSearch(form) {
    const result = document.querySelector("#search-result");
    const formData = new FormData(form);

    result.innerHTML = `
        <span class="status-badge">Working</span>
        <p class="placeholder-copy">Embedding query and searching the local vector store...</p>
    `;

    try {
        const response = await fetch("/api/search", {
            method: "POST",
            headers: {
                "Accept": "application/json",
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                query: formData.get("query"),
                topK: Number(formData.get("topK")),
                mode: formData.get("mode"),
                category: formData.get("category") || null,
                fromDate: formData.get("fromDate") || null,
                toDate: formData.get("toDate") || null
            })
        });

        if (!response.ok) {
            throw new Error(`Search endpoint returned ${response.status}`);
        }

        const searchResponse = await response.json();
        result.innerHTML = `
            <span class="status-badge ok">${searchResponse.results.length} result(s)</span>
            <p class="placeholder-copy">Mode: ${escapeHtml(searchResponse.mode)} · Backend: ${escapeHtml(searchResponse.embeddingModel)}</p>
            <div class="result-list">${searchResponse.results.map(resultItem => `
                <article class="result-card">
                    <strong>${escapeHtml(resultItem.title)}</strong>
                    <span>[arXiv:${escapeHtml(resultItem.arxivId)}] · score ${Number(resultItem.score).toFixed(3)}</span>
                    <p>${escapeHtml(resultItem.abstractText)}</p>
                </article>
            `).join("") || `<p class="placeholder-copy">No matching papers were found. Confirm that papers were imported and embedded.</p>`}</div>
        `;
        clearGlobalError();
    } catch {
        result.innerHTML = `
            <span class="status-badge error">Failed</span>
            <p class="placeholder-copy">The search endpoint is unavailable or rejected the request.</p>
        `;
        showGlobalError("Semantic search failed. Check database, embeddings, and Foundry configuration.");
    }
}

async function submitChat(form) {
    const formData = new FormData(form);
    const message = String(formData.get("message") ?? "").trim();
    if (!message) {
        return;
    }
    const request = {
        message,
        retrievalMode: formData.get("retrievalMode") || null,
        category: formData.get("category") || null,
        fromDate: formData.get("fromDate") || null,
        toDate: formData.get("toDate") || null,
        agentic: formData.get("agentic") === "on" ? true : null
    };
    if (chatAbortController) {
        chatAbortController.abort();
    }
    const requestController = new AbortController();
    chatAbortController = requestController;
    const assistant = {role: "assistant", content: "", citations: [], toolsUsed: [], status: "Starting retrieval…", streaming: true};
    chatTranscript.push({role: "user", content: message});
    chatTranscript.push(assistant);
    renderPage();
    const chatForm = document.querySelector("[data-chat-form]");
    if (chatForm) {
        chatForm.querySelector("textarea").disabled = true;
        chatForm.querySelector("button").disabled = true;
    }
    try {
        await submitStreamingChat(request, assistant, requestController.signal);
        clearGlobalError();
    } catch (error) {
        if (error.name === "AbortError") {
            assistant.status = null;
            assistant.streaming = false;
            assistant.content = assistant.content || "Request cancelled.";
        } else if (error.fallbackAllowed) {
            try {
                await submitNonStreamingChat(request, assistant, requestController.signal);
                clearGlobalError();
            } catch (fallbackError) {
                assistant.status = null;
                assistant.streaming = false;
                assistant.content = `Chat failed: ${fallbackError.message}`;
                showGlobalError("Grounded chat failed. Check the Foundry key, deployment, database, and embedding coverage.");
            }
        } else {
            assistant.status = null;
            assistant.streaming = false;
            assistant.content = `Chat failed: ${error.message}`;
            showGlobalError("Grounded chat failed. Check the Foundry key, deployment, database, and embedding coverage.");
        }
    } finally {
        if (chatAbortController === requestController) {
            chatAbortController = null;
        }
    }
    if ((window.location.hash.replace("#/", "") || "system-status") === "chat") {
        renderPage();
    }
}

async function submitNonStreamingChat(request, assistant, signal) {
    const response = await fetch("/api/chat", {
            method: "POST",
            headers: {"Accept": "application/json", "Content-Type": "application/json"},
            body: JSON.stringify(request),
            signal
    });
    const payload = await response.json();
    if (!response.ok) {
        throw new Error(payload.error ?? `Chat endpoint returned ${response.status}`);
    }
    Object.assign(assistant, {
        content: payload.answer,
        citations: payload.citations ?? [],
        toolsUsed: payload.toolsUsed ?? [],
        status: null,
        streaming: false
    });
}

async function submitStreamingChat(request, assistant, signal) {
    const response = await fetch("/api/chat/stream", {
        method: "POST",
        headers: {"Accept": "text/event-stream", "Content-Type": "application/json"},
        body: JSON.stringify(request),
        signal
    });
    if ([404, 405, 406, 415].includes(response.status)) {
        const error = new Error("Streaming is unavailable");
        error.fallbackAllowed = true;
        throw error;
    }
    if (!response.ok || !response.body) {
        let detail = `Chat stream returned ${response.status}`;
        try {
            const payload = await response.json();
            detail = payload.error ?? detail;
        } catch {
            // Keep the status-based message when the response is not JSON.
        }
        throw new Error(detail);
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    while (true) {
        const {value, done} = await reader.read();
        buffer = (buffer + decoder.decode(value ?? new Uint8Array(), {stream: !done}))
            .replaceAll("\r\n", "\n");
        let boundary;
        while ((boundary = buffer.indexOf("\n\n")) >= 0) {
            const frame = buffer.slice(0, boundary);
            buffer = buffer.slice(boundary + 2);
            handleChatEvent(parseSseFrame(frame), assistant);
        }
        if (done) {
            if (buffer.trim()) {
                handleChatEvent(parseSseFrame(buffer), assistant);
            }
            break;
        }
    }
    if (assistant.streaming) {
        throw new Error("Chat stream ended before its completion event");
    }
}

function parseSseFrame(frame) {
    let event = "message";
    const data = [];
    for (const line of frame.split("\n")) {
        if (line.startsWith("event:")) {
            event = line.slice(6).trim();
        } else if (line.startsWith("data:")) {
            data.push(line.slice(5).trimStart());
        }
    }
    return {event, data: data.length ? JSON.parse(data.join("\n")) : null};
}

function handleChatEvent(frame, assistant) {
    if (frame.event === "retrieval") {
        assistant.status = "Searching the local corpus…";
    } else if (frame.event === "citations") {
        assistant.citations = frame.data ?? [];
        assistant.status = assistant.citations.length
            ? `Found ${assistant.citations.length} source(s); Foundry is answering…`
            : "No matching evidence found; Foundry is answering…";
    } else if (frame.event === "delta") {
        assistant.content += frame.data?.text ?? "";
        assistant.status = "Foundry is streaming…";
    } else if (frame.event === "complete") {
        assistant.content = frame.data?.answer ?? assistant.content;
        assistant.toolsUsed = frame.data?.toolsUsed ?? [];
        assistant.status = null;
        assistant.streaming = false;
    } else if (frame.event === "error") {
        throw new Error(frame.data?.message ?? "Chat stream failed");
    }
    scheduleChatRender();
}

function scheduleChatRender() {
    if (chatRenderPending || (window.location.hash.replace("#/", "") || "system-status") !== "chat") {
        return;
    }
    chatRenderPending = true;
    requestAnimationFrame(() => {
        chatRenderPending = false;
        renderPage();
    });
}

async function refreshBackendStatus() {
    backendHealth = {
        status: "checking",
        message: "Checking backend health...",
        checkedAt: null
    };
    updateBackendHealthUi();

    try {
        const response = await fetch("/api/health", {headers: {"Accept": "application/json"}});
        if (!response.ok) {
            throw new Error(`Health endpoint returned ${response.status}`);
        }

        const health = await response.json();
        backendHealth = {
            status: health.status ?? "UNKNOWN",
            message: health.message ?? "Backend responded, but did not include a health message.",
            checkedAt: health.checkedAt ?? new Date().toISOString()
        };
        clearGlobalError();
    } catch {
        backendHealth = {
            status: "UNAVAILABLE",
            message: "The backend API is not reachable. Confirm the Spring Boot service is running, then refresh the health check.",
            checkedAt: new Date().toISOString()
        };
        showGlobalError("Backend services are unavailable. Demo data may not load until the backend is running again.");
    } finally {
        updateBackendHealthUi();
    }
}

function updateBackendHealthUi() {
    const isUp = backendHealth.status === "UP";
    environmentPill.textContent = backendHealth.status === "checking" ? "Backend checking" : `Backend ${backendHealth.status}`;
    environmentPill.classList.toggle("ok", isUp);
    environmentPill.classList.toggle("error", backendHealth.status !== "checking" && !isUp);

    if ((window.location.hash.replace("#/", "") || "system-status") === "system-status") {
        renderPage();
    }
}

async function refreshRuntimeConfig() {
    try {
        const response = await fetch("/api/runtime-config", {headers: {"Accept": "application/json"}});
        if (!response.ok) {
            throw new Error(`Runtime config endpoint returned ${response.status}`);
        }

        runtimeConfig = await response.json();
        runtimeConfigError = null;
    } catch {
        runtimeConfig = null;
        runtimeConfigError = "Runtime configuration is unavailable. Check backend health, then use Refresh to try again.";
    }

    if ((window.location.hash.replace("#/", "") || "system-status") === "settings") {
        renderPage();
    }
}

async function refreshEmbeddingStatus() {
    try {
        const response = await fetch("/api/embeddings/status", {headers: {"Accept": "application/json"}});
        if (!response.ok) {
            throw new Error(`Embedding status endpoint returned ${response.status}`);
        }

        embeddingStatus = await response.json();
        embeddingStatusError = null;
    } catch {
        embeddingStatus = null;
        embeddingStatusError = "Embedding checkpoint status is unavailable. Check backend health and database configuration.";
    }

    if ((window.location.hash.replace("#/", "") || "system-status") === "vector-stores") {
        renderPage();
    }
}

async function startEmbeddingJob(form) {
    const maxPapers = Number(new FormData(form).get("maxPapers"));
    try {
        const response = await fetch("/api/embeddings/jobs", {
            method: "POST",
            headers: {"Accept": "application/json", "Content-Type": "application/json"},
            body: JSON.stringify({maxPapers})
        });
        const payload = await response.json();
        if (!response.ok) {
            throw new Error(payload.error ?? `Embedding job endpoint returned ${response.status}`);
        }
        clearGlobalError();
        await refreshEmbeddingStatus();
    } catch (error) {
        showGlobalError(`Could not start embedding job: ${error.message}`);
    }
}

async function cancelEmbeddingJob() {
    try {
        const response = await fetch("/api/embeddings/jobs/current", {method: "DELETE", headers: {"Accept": "application/json"}});
        if (!response.ok) {
            throw new Error(`Embedding cancellation returned ${response.status}`);
        }
        clearGlobalError();
        await refreshEmbeddingStatus();
    } catch (error) {
        showGlobalError(`Could not cancel embedding job: ${error.message}`);
    }
}

window.addEventListener("hashchange", () => {
    if ((window.location.hash.replace("#/", "") || "system-status") !== "chat" && chatAbortController) {
        chatAbortController.abort();
    }
    renderPage();
});
window.addEventListener("error", () => {
    showGlobalError("Something went wrong in the demo UI. Refresh the page or retry the last action.");
});
window.addEventListener("unhandledrejection", () => {
    showGlobalError("A backend request failed unexpectedly. Check service health and retry.");
});
healthRefreshButton.addEventListener("click", () => {
    refreshBackendStatus();
    refreshRuntimeConfig();
    refreshEmbeddingStatus();
});
pageContent.addEventListener("click", (event) => {
    if (event.target instanceof Element && event.target.matches("[data-health-refresh]")) {
        refreshBackendStatus();
        refreshRuntimeConfig();
    }
    if (event.target instanceof Element && event.target.matches("[data-embedding-status-refresh]")) {
        refreshEmbeddingStatus();
    }
    if (event.target instanceof Element && event.target.matches("[data-embedding-job-cancel]")) {
        cancelEmbeddingJob();
    }
    if (event.target instanceof Element && event.target.matches("[data-chat-cancel]") && chatAbortController) {
        chatAbortController.abort();
    }
});
pageContent.addEventListener("submit", (event) => {
    if (event.target instanceof HTMLFormElement && event.target.matches("[data-search-form]")) {
        event.preventDefault();
        submitSearch(event.target);
    }
    if (event.target instanceof HTMLFormElement && event.target.matches("[data-chat-form]")) {
        event.preventDefault();
        submitChat(event.target);
    }
    if (event.target instanceof HTMLFormElement && event.target.matches("[data-embedding-job-form]")) {
        event.preventDefault();
        startEmbeddingJob(event.target);
    }
});
renderPage();
refreshBackendStatus();
refreshRuntimeConfig();
refreshEmbeddingStatus();
setInterval(() => {
    if ((window.location.hash.replace("#/", "") || "system-status") === "vector-stores") {
        refreshEmbeddingStatus();
    }
}, 5000);

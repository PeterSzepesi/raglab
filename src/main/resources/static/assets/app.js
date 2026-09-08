const pages = [
    {
        id: "system-status",
        title: "System Status",
        icon: "S",
        summary: "Health and runtime readiness for the Spring Boot backend.",
        details: ["Backend health probe", "Application identity", "Future database/vector-store status"],
        metrics: [
            ["Backend", "Checking..."],
            ["Relational DB", "Not wired"],
            ["Vector store", "Not wired"]
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
        summary: "Exercise semantic and structured retrieval before chat generation is added.",
        details: ["Run top-K semantic search", "Show vector scores", "Resolve vector hits back to paper records"]
    },
    {
        id: "chat",
        title: "Chat",
        icon: "C",
        summary: "Future grounded RAG chat interface with citations and retrieval transparency.",
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

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
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
        renderRetrievalPreviewPage(page);
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
                        <strong>Not wired</strong>
                        <span>Relational DB</span>
                    </div>
                    <div class="metric">
                        <strong>Not wired</strong>
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
                    <strong>${escapeHtml(embeddingStatus.coveragePercent.toFixed(1))}%</strong>
                    <span>Coverage</span>
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
        `
        : `<p class="placeholder-copy">${embeddingStatusError ?? "Loading embedding checkpoint status..."}</p>`;

    pageContent.innerHTML = `
        <div class="hero-grid">
            <div>
                <h2>${page.title}</h2>
                <p class="placeholder-copy">Embedding checkpointing and vector persistence are active. Run <code>embed-papers</code> with the local profile to upsert vectors into pgvector, then use <code>search-vector &lt;query&gt;</code> for retrieval-only smoke testing.</p>
                <div class="health-panel ${embeddingStatusError ? "error" : ""}">
                    <div>
                        <span class="status-badge ${embeddingStatusError ? "error" : "ok"}">${embeddingStatusError ? "Unavailable" : "Checkpointing"}</span>
                        <h3>Embedding ingestion status</h3>
                        <p>${embeddingStatusError ? escapeHtml(embeddingStatusError) : "Offline deterministic embeddings can be checkpointed locally without provider keys."}</p>
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

function renderRetrievalPreviewPage(page) {
    pageContent.innerHTML = `
        <div class="preview-layout">
            <div>
                <h2>${page.title}</h2>
                <p class="placeholder-copy">Build the exact title+abstract retrieval document used by embedding ingestion. This preview is deterministic and does not call an LLM or vector database.</p>
                <form class="preview-form" data-retrieval-preview-form>
                    <label>
                        arXiv ID
                        <input name="arxivId" type="text" value="2401.00001" required>
                    </label>
                    <label>
                        Title
                        <input name="title" type="text" value="Retrieval Testing" required>
                    </label>
                    <label>
                        Abstract
                        <textarea name="abstractText" rows="8" required>A concise abstract for repository testing.</textarea>
                    </label>
                    <button class="primary-button" type="submit">Generate Retrieval Document</button>
                </form>
            </div>
            <aside class="preview-result" id="retrieval-preview-result">
                <span class="status-badge">Ready</span>
                <p class="placeholder-copy">Submit the form to see the generated chunk ID, SHA-256 content hash, and embedding text.</p>
            </aside>
        </div>
    `;
}

async function submitRetrievalPreview(form) {
    const result = document.querySelector("#retrieval-preview-result");
    const formData = new FormData(form);

    result.innerHTML = `
        <span class="status-badge">Working</span>
        <p class="placeholder-copy">Generating deterministic retrieval document...</p>
    `;

    try {
        const response = await fetch("/api/retrieval-documents/preview", {
            method: "POST",
            headers: {
                "Accept": "application/json",
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                arxivId: formData.get("arxivId"),
                title: formData.get("title"),
                abstractText: formData.get("abstractText")
            })
        });

        if (!response.ok) {
            throw new Error(`Preview endpoint returned ${response.status}`);
        }

        const documentPreview = await response.json();
        result.innerHTML = `
            <span class="status-badge ok">Generated</span>
            <div class="config-grid">
                <div class="config-item">
                    <span>Chunk ID</span>
                    <strong>${escapeHtml(documentPreview.chunkId)}</strong>
                </div>
                <div class="config-item">
                    <span>Content hash</span>
                    <strong class="hash-value">${escapeHtml(documentPreview.contentHash)}</strong>
                </div>
            </div>
            <h3>Embedding text</h3>
            <pre class="document-preview">${escapeHtml(documentPreview.text)}</pre>
        `;
        clearGlobalError();
    } catch {
        result.innerHTML = `
            <span class="status-badge error">Failed</span>
            <p class="placeholder-copy">The preview endpoint is unavailable or rejected the request.</p>
        `;
        showGlobalError("Retrieval document preview failed. Check backend health and required form values.");
    }
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

window.addEventListener("hashchange", renderPage);
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
});
pageContent.addEventListener("submit", (event) => {
    if (event.target instanceof HTMLFormElement && event.target.matches("[data-retrieval-preview-form]")) {
        event.preventDefault();
        submitRetrievalPreview(event.target);
    }
});
renderPage();
refreshBackendStatus();
refreshRuntimeConfig();
refreshEmbeddingStatus();

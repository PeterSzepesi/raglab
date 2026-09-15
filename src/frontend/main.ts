import { api, ApiError } from "./api/http-client";
import type { RuntimeConfig } from "./api/contracts";
import { h } from "./components/dom";
import { store } from "./state/app-store";
import { currentRoute, navigate } from "./router";
import type { Page, PageContext } from "./pages/page";
import { statusPage } from "./pages/status-page";
import { importPage } from "./pages/import-page";
import { papersPage } from "./pages/papers-page";
import { searchPage } from "./pages/search-page";
import { chatPage } from "./pages/chat-page";
import { embeddingsPage } from "./pages/embeddings-page";
import { benchmarksPage } from "./pages/benchmarks-page";
import { settingsPage } from "./pages/settings-page";

const pages = new Map<string, Page>([
  ["system-status", statusPage], ["import", importPage], ["papers", papersPage], ["search", searchPage],
  ["chat", chatPage], ["embeddings", embeddingsPage], ["vector-stores", embeddingsPage],
  ["benchmarks", benchmarksPage], ["settings", settingsPage]
]);
const icons: Record<string, string> = { "system-status": "S", import: "I", papers: "P", search: "Q", chat: "C", embeddings: "E", benchmarks: "B", settings: "G" };
const navigation = required("navigation"); const title = required("page-title"); const content = required("page-content");
const pill = required("environment-pill"); const errorBanner = required("global-error");
let unmount: () => void = () => {};

const context: PageContext = { api, store, navigate, reportError };

function required(id: string): HTMLElement {
  const value = document.getElementById(id); if (!value) throw new Error(`Missing UI host #${id}`); return value;
}

function enabled(page: Page): boolean {
  return !page.feature || store.runtime?.features.find(feature => feature.name === page.feature)?.enabled !== false;
}

function renderNavigation(active: string): void {
  navigation.replaceChildren(...[...pages.entries()].filter(([route]) => route !== "vector-stores")
    .filter(([, page]) => enabled(page)).map(([route, page]) => h("a", { class: `nav-link ${route === active ? "active" : ""}`,
      href: `#/${route}`, ariaCurrent: route === active ? "page" : null }, h("span", {}, icons[route] ?? "•"), h("span", {}, page.title))));
}

function render(): void {
  unmount(); const route = currentRoute(); const page = pages.get(route) ?? statusPage;
  title.textContent = page.title; renderNavigation(route);
  if (!enabled(page)) {
    content.replaceChildren(h("div", {}, h("h2", {}, page.title), h("p", {}, "This capability is disabled in the active backend profile.")));
    unmount = () => {}; return;
  }
  unmount = page.mount(content, context);
}

function reportError(error: unknown): void {
  const requestId = error instanceof ApiError ? error.requestId : null;
  errorBanner.textContent = `${error instanceof Error ? error.message : "The operation failed."}${requestId ? ` Request ID: ${requestId}` : ""}`;
  errorBanner.classList.remove("hidden"); errorBanner.tabIndex = -1; errorBanner.focus();
}

async function bootstrap(): Promise<void> {
  document.documentElement.dataset.theme = store.preferences.theme;
  try {
    store.runtime = await api.get<RuntimeConfig>("/capabilities");
    pill.textContent = `${store.runtime.configuredProfile} · ${store.runtime.facts.provider}`;
    pill.classList.add("ok");
  } catch (error) { pill.textContent = "Backend unavailable"; pill.classList.add("error"); reportError(error); }
  render();
}

window.addEventListener("hashchange", render);
window.addEventListener("unhandledrejection", event => reportError(event.reason));
document.getElementById("health-refresh-button")?.addEventListener("click", () => void bootstrap());
void bootstrap();

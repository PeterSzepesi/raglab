import { field, h, section } from "../components/dom";
import type { Page } from "./page";

export const settingsPage: Page = { title: "Settings", mount(root, { store }) {
  const runtime = store.runtime;
  const prefs = store.preferences;
  const form = h("form", { class: "preview-form" },
    field("Default search mode", h("select", { name: "searchMode" }, ...["auto", "lexical", "semantic", "hybrid"].map(value =>
      h("option", { value, selected: prefs.searchMode === value }, value)))),
    field("Default result count", h("input", { name: "resultCount", type: "number", min: 1, max: 50, value: prefs.resultCount })),
    field("Theme", h("select", { name: "theme" }, ...["system", "light", "dark"].map(value =>
      h("option", { value, selected: prefs.theme === value }, value)))),
    field("Show developer diagnostics", h("input", { name: "diagnostics", type: "checkbox", checked: prefs.diagnostics })),
    field("Save chat transcript locally", h("input", { name: "saveTranscript", type: "checkbox", checked: prefs.saveTranscript })),
    h("button", { class: "primary-button", type: "submit" }, "Save preferences"));
  form.addEventListener("submit", event => {
    event.preventDefault(); const data = new FormData(form);
    store.updatePreferences({ searchMode: String(data.get("searchMode")), resultCount: Number(data.get("resultCount")),
      theme: String(data.get("theme")), diagnostics: data.get("diagnostics") === "on", saveTranscript: data.get("saveTranscript") === "on" });
  });
  const facts = runtime?.facts;
  root.replaceChildren(section("Settings", "Runtime facts are read-only. Credentials are never returned or accepted here.",
    h("div", { class: "config-grid" },
      h("div", { class: "config-item" }, h("span", {}, "Profile"), h("strong", {}, runtime?.configuredProfile ?? "Unavailable")),
      h("div", { class: "config-item" }, h("span", {}, "Foundry host"), h("strong", {}, facts?.endpointHost ?? "Unavailable")),
      h("div", { class: "config-item" }, h("span", {}, "Authentication"), h("strong", {}, facts?.authenticationMode ?? "Unavailable")),
      h("div", { class: "config-item" }, h("span", {}, "Credential"), h("strong", {}, facts?.credentialConfigured ? "Configured" : "Missing")),
      h("div", { class: "config-item" }, h("span", {}, "Embedding"), h("strong", {}, `${facts?.embeddingDeployment ?? "?"} / ${facts?.embeddingDimensions ?? "?"}d`)),
      h("div", { class: "config-item" }, h("span", {}, "Admin protection"), h("strong", {}, facts?.adminProtection ?? "Unavailable"))), form));
  return () => {};
}};

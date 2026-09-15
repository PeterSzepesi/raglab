import { h } from "./dom";
import { ApiError } from "../api/http-client";

export function loading(message: string): HTMLElement {
  return h("div", { class: "health-panel", role: "status" }, h("span", { class: "status-badge working" }, "Working"), h("p", {}, message));
}

export function empty(message: string): HTMLElement {
  return h("div", { class: "health-panel" }, h("span", { class: "status-badge" }, "No data"), h("p", {}, message));
}

export function errorState(error: unknown, retry?: () => void): HTMLElement {
  const message = error instanceof Error ? error.message : "The operation failed.";
  const requestId = error instanceof ApiError ? error.requestId : null;
  return h("div", { class: "health-panel error", role: "alert", tabIndex: -1 },
    h("div", {}, h("span", { class: "status-badge error" }, "Failed"), h("p", {}, message),
      requestId ? h("small", {}, `Request ID: ${requestId}`) : null),
    retry ? h("button", { type: "button", class: "primary-button", onclick: retry }, "Retry") : null);
}

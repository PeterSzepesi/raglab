import type { Citation, RuntimeConfig } from "../api/contracts";
import { readVersioned, writeVersioned } from "./persistence";

export type Preferences = { searchMode: string; resultCount: number; diagnostics: boolean; saveTranscript: boolean; theme: string };
export type ChatEntry = { role: "user" | "assistant"; content: string; citations?: Citation[]; toolsUsed?: string[];
  streaming?: boolean; stage?: string; groundingStatus?: string; cacheStatus?: string; diagnostics?: Record<string, unknown> };

export class AppStore extends EventTarget {
  runtime: RuntimeConfig | null = null;
  preferences = readVersioned<Preferences>("preferences", 1,
    { searchMode: "auto", resultCount: 5, diagnostics: false, saveTranscript: true, theme: "system" });
  transcript = this.preferences.saveTranscript ? readVersioned<ChatEntry[]>("transcript", 1, []) : [];
  recentSearches = readVersioned<string[]>("searches", 1, []);
  pendingChatQuestion: string | null = null;
  pendingSearch: string | null = null;

  updatePreferences(values: Partial<Preferences>): void {
    this.preferences = { ...this.preferences, ...values };
    if (!this.preferences.saveTranscript) { this.transcript = []; localStorage.removeItem("arxiv-rag:transcript"); }
    writeVersioned("preferences", 1, this.preferences);
    document.documentElement.dataset.theme = this.preferences.theme;
    this.changed();
  }
  saveTranscript(): void {
    if (this.preferences.saveTranscript) writeVersioned("transcript", 1, this.transcript);
    this.changed();
  }
  clearTranscript(): void { this.transcript = []; localStorage.removeItem("arxiv-rag:transcript"); this.changed(); }
  rememberSearch(query: string): void {
    this.recentSearches = [query, ...this.recentSearches.filter(value => value !== query)].slice(0, 8);
    writeVersioned("searches", 1, this.recentSearches);
  }
  changed(): void { this.dispatchEvent(new Event("change")); }
}

export const store = new AppStore();

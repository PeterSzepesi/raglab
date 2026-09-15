import type { ApiClient } from "../api/http-client";
import type { AppStore } from "../state/app-store";

export type PageContext = { api: ApiClient; store: AppStore; navigate: (route: string) => void; reportError: (error: unknown) => void };
export type Page = { title: string; feature?: string; mount(root: HTMLElement, context: PageContext): () => void };

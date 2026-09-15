export type Child = Node | string | number | null | undefined | false;

export function h<K extends keyof HTMLElementTagNameMap>(tag: K,
    attributes: Record<string, unknown> = {}, ...children: Child[]): HTMLElementTagNameMap[K] {
  const element = document.createElement(tag);
  for (const [name, value] of Object.entries(attributes)) {
    if (value == null || value === false) continue;
    if (name === "class") element.className = String(value);
    else if (name === "dataset" && typeof value === "object") Object.assign(element.dataset, value);
    else if (name === "text") element.textContent = String(value);
    else if (name.startsWith("on") && typeof value === "function")
      element.addEventListener(name.slice(2).toLowerCase(), value as EventListener);
    else if (name in element) (element as unknown as Record<string, unknown>)[name] = value;
    else element.setAttribute(name, String(value));
  }
  for (const child of children.flat()) {
    if (child == null || child === false) continue;
    element.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return element;
}

export function section(title: string, summary: string, ...content: Node[]): HTMLElement {
  return h("div", {}, h("h2", {}, title), h("p", { class: "placeholder-copy" }, summary), ...content);
}

export function badge(value: string, tone = ""): HTMLElement {
  return h("span", { class: `status-badge ${tone}`.trim() }, value);
}

export function metric(label: string, value: string | number): HTMLElement {
  return h("div", { class: "metric" }, h("strong", {}, value), h("span", {}, label));
}

export function metricGrid(...metrics: HTMLElement[]): HTMLElement { return h("div", { class: "metric-grid" }, ...metrics); }

export function field(label: string, input: HTMLElement): HTMLLabelElement { return h("label", {}, label, input); }

export function button(label: string, action?: () => void, secondary = false): HTMLButtonElement {
  return h("button", { type: "button", class: secondary ? "secondary-button" : "primary-button", onclick: action }, label);
}

export function externalLink(label: string, url: string): HTMLAnchorElement {
  const link = h("a", { href: url, target: "_blank", rel: "noopener noreferrer", class: "citation citation-link" }, label, " ↗");
  return link;
}

export function clearAndFocus(root: HTMLElement, node: Node): void {
  root.replaceChildren(node);
  const focusable = root.querySelector<HTMLElement>("h2, [tabindex], input, button");
  if (focusable?.tagName === "H2") focusable.tabIndex = -1;
  focusable?.focus({ preventScroll: true });
}

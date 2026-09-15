export function currentRoute(): string {
  return (location.hash.replace(/^#\/?/, "").split("/")[0] || "system-status").toLowerCase();
}

export function navigate(route: string): void { location.hash = `#/${route}`; }

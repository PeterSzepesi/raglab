import http from "k6/http";
import { check, sleep } from "k6";

const baseUrl = __ENV.BASE_URL || "http://127.0.0.1:8080/api/v1";

export const options = {
  scenarios: {
    search: { executor: "constant-vus", exec: "search", vus: Number(__ENV.SEARCH_VUS || 5), duration: __ENV.DURATION || "30s" },
    chat: { executor: "constant-vus", exec: "chat", vus: Number(__ENV.CHAT_VUS || 1), duration: __ENV.DURATION || "30s" }
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    "http_req_duration{operation:search}": ["p(95)<1500"],
    "http_req_duration{operation:chat}": ["p(95)<15000"]
  }
};

const headers = { "Content-Type": "application/json" };

export function search() {
  const response = http.post(`${baseUrl}/search`, JSON.stringify({ query: "retrieval augmented generation", mode: "auto", topK: 5 }),
    { headers, tags: { operation: "search" } });
  check(response, { "search succeeds": value => value.status === 200 });
  sleep(0.2);
}

export function chat() {
  const response = http.post(`${baseUrl}/chat`, JSON.stringify({ message: "Summarize evidence about retrieval augmented generation.", retrievalMode: "auto" }),
    { headers, tags: { operation: "chat" }, timeout: "2m" });
  check(response, { "chat succeeds or is capacity limited": value => value.status === 200 || value.status === 429 });
  sleep(0.5);
}

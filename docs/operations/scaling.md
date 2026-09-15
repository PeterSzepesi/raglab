# Scale and conditional infrastructure gates

**Status:** gates defined; Redis, extra replicas, ingress, APIM balancing, alternate embeddings, and PDF chunks are intentionally not deployed

The current measured architecture is one Spring Boot process, Caffeine L1 caches, and PostgreSQL/pgvector. Adding distributed components before a trigger would increase latency and operational failure modes without improving a one-user local application.

| Trigger | Approved next change |
|---|---|
| >10 sustained chat streams or admission wait p95 >500 ms with saturated CPU | add an application replica |
| aggregate Hikari demand approaches PostgreSQL connection capacity | tune pools, then evaluate PgBouncer |
| replicas produce material duplicate provider calls or stale local cache behavior | PostgreSQL corpus version is already ready; add Redis L2/shared single-flight |
| one Foundry endpoint misses availability/quota objectives | add a compatible second deployment and APIM priority/weighted backend pool |
| tuned PostgreSQL query p95 misses SLO | scale PostgreSQL or evaluate another vector store with the same benchmark |
| prompt/context remains the measured latency/cost bottleneck | evaluate versioned full-PDF chunks/reranking |
| embedding network/size is a measured bottleneck and recall corpus exists | evaluate a smaller representation in a separate table/index |

Before replica two, verify job leases, PostgreSQL corpus generation, stateless HTTP/SSE behavior, bounded per-replica pools, graceful shutdown, and an ingress idle timeout longer than the maximum stream. SSE needs affinity only for the lifetime of its existing TCP connection; no HTTP session state is required.

When hosting begins, use the checked-in non-root container image behind platform-managed TLS ingress and Actuator probes. Do not add Nginx/Traefik to local Compose as a latency optimization. APIM response caching remains off until identity, corpus/prompt invalidation, streaming compatibility, and citation metadata are proven safe.

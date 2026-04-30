---
inclusion: manual
name: performance-guidelines
description: Performance — backend SLAs, DB/query patterns, Redis caching, frontend basics
---

# Performance

## Backend baselines

| Document size | Target | Mode |
|---------------|--------|------|
| ≤10 pages | ≤5s | Sync |
| 11–100 pages | ≤15s | Sync/async |
| 101–1000 pages | ≤30s | Async required |

API &lt; 100 TPS; DB pool max 20 / min idle 5; Redis client timeout 5000ms

## Database

- Every query must use indexes — no full scans on large tables
- Pagination via `Pageable`, default size 20
- Fix N+1 with `@EntityGraph` or `JOIN FETCH`
- Bulk updates via `@Modifying` batch SQL
- `tenant_id` in composite indexes for tenant-scoped queries

## Redis

| Use case | Key pattern | TTL |
|----------|-------------|-----|
| Rate limit | `rl:{apiKey}:{window}` | window duration |
| Refresh token | `rt:{userId}` | 7d |
| Template metadata | `tpl:{tenantId}:{templateId}` | 5m |

Keys must include `tenantId`; invalidate on writes; short TTL for negative cache to avoid storms

## Frontend

- Lazy routes + on-demand Element Plus + virtual scroll for huge lists
- Debounce/throttle search and scroll; `shallowRef` for large reactive trees

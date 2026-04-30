---
inclusion: auto
name: api-design
description: RESTful API design — use when adding or changing Controllers or frontend API modules
---

# API design

## URLs

- Lowercase, hyphens, plural resources: `/api/composite-templates/{id}/segments`
- At most two nesting levels; actions as verb sub-paths: `/api/templates/{id}/activate`

## HTTP methods → success codes

| Method | Use | Idempotent | Success |
|--------|-----|------------|---------|
| GET | Read | Yes | 200 |
| POST | Create | No | 201 |
| PUT | Full replace | Yes | 200 |
| PATCH | Partial update | No | 200 |
| DELETE | Delete | Yes | 204 |

## Response shapes

- Pagination: `{ content, totalElements, totalPages, number, size }` (Spring `Page`)
- Errors: `{ code, message, timestamp }` — `code` from #[[file:backend/src/main/java/com/docgen/exception/ErrorCode.java]]

## Frontend API layer

- One file per domain: `frontend/src/api/{module}.ts`
- Reference: #[[file:frontend/src/api/parameters.ts]]

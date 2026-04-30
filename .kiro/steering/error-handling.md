---
inclusion: auto
name: error-handling
description: Error handling — use when writing exceptions, Controllers, or ErrorCode entries
---

# Error handling

## ErrorCode

- Pattern: `{MODULE}_{ERROR_TYPE}`
- Defined in: #[[file:backend/src/main/java/com/docgen/exception/ErrorCode.java]]
- Prefixes include: AUTH_, TENANT_, TEMPLATE_, EXPRESSION_, GENERATE_, TASK_, DOCUMENT_, VALIDATION_, RATE_LIMIT_, REVIEW_, ENCRYPTION_, PIPELINE_, COVERAGE_, IMPORT_/EXPORT_, ONLYOFFICE_, WEBHOOK_, SCHEDULED_TASK_, AUDIT_, API_KEY_, WATERMARK_, MERGE_, TEST_CASE_, MARKET_, COMPOSITE_, PARAMETER_, MIGRATION_, INTERNAL_

## Exception → HTTP

| Exception | HTTP | When |
|-----------|------|------|
| `ResourceNotFoundException` | 404 | Missing resource |
| `BusinessException` | configurable | Business rule violations |
| `ValidationException` | 400 | Validation failures |
| `AccessDeniedException` | 403 | Insufficient permissions |
| `RateLimitExceededException` | 429 | Throttled |

- Reference: #[[file:backend/src/main/java/com/docgen/exception/GlobalExceptionHandler.java]]

## Error response

```json
{ "code": "TEMPLATE_NOT_FOUND", "message": "Template not found", "timestamp": "2025-01-01T00:00:00Z" }
```

## Log levels

ERROR — system / dependency failures; WARN — business degradation; INFO — important actions; DEBUG — detail (dev only)

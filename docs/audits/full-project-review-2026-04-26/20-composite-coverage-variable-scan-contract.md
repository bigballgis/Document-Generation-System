# WS-02-T06: Composite coverage variable scan contract

**Status:** Implemented (WS-02-T07): Node `POST /scan-variables`; Java `CompositeCoverageService` calls this endpoint.  
**Date:** 2026-04-26

## Problem statement (current mismatch)

`CompositeCoverageService` in the Java backend calls the Docxtemplater Node service at **`POST /evaluate`** with a JSON body `{ "templatePath": "<minio-path>" }` and expects a response containing **`variables: string[]`**.

The Node route **`POST /evaluate`** (`docxtemplater-service/src/routes/evaluate.js`) is a **sandboxed expression evaluator**: it requires an **`expression`** string (and optional `type`, `context`, limits). It never reads `templatePath` and never returns `variables`.

Evidence:

- Java: `backend/src/main/java/com/docgen/service/CompositeCoverageService.java` — `scanVariablesFromFile`.
- Node: `docxtemplater-service/src/routes/evaluate.js`.

**Observed consequence:** variable scanning fails or degrades to an empty list (exceptions are caught and logged in Java), so composite coverage metrics can be **silently wrong**.

This is also recorded as **C-004** in `14-java-node-contract-mismatch-inventory.md`.

## Decision: dedicated read-only scan endpoint

**Do not** overload `/evaluate` for template variable discovery. Expression evaluation must remain strictly separate from DOCX inspection.

### Normative HTTP contract

| Item | Value |
| --- | --- |
| Method | `POST` |
| Path | `/scan-variables` |
| Content-Type | `application/json` |

#### Request body (JSON)

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `templatePath` | `string` | Yes | MinIO object key for the segment `.docx`, same semantics as `POST /render` (`templatePath`). |

Example:

```json
{
  "templatePath": "tenant-1/templates/segment-header.docx"
}
```

#### Success response (`200`)

JSON object:

| Field | Type | Description |
| --- | --- | --- |
| `variables` | `string[]` | All distinct placeholder **root names** required for rendering that template with the same Docxtemplater configuration as production render (see below). |

Rules for `variables`:

1. **Uniqueness:** each logical placeholder appears at most once (set semantics).
2. **Ordering:** sorted **lexicographically** by Unicode code point ascending (stable, test-friendly).
3. **Naming:** entries are the **tag identifiers** Docxtemplater would bind when rendering (e.g. `user.name`, `items` for a loop). They MUST match what `/render` would resolve when supplied `data` keys, not raw XML fragments.
4. **No user code from the request:** the server MUST NOT interpret arbitrary JavaScript from the client for this endpoint. Only the stored `.docx` is parsed.

Example:

```json
{
  "variables": ["companyName", "effectiveDate", "items"]
}
```

#### Error responses

Use JSON shape `{ "error": { "code": string, "message": string, "details"?: object } }` consistent with other Docxtemplater routes unless a route already standardizes differently—then align with `/render` error style for operator familiarity.

| Condition | HTTP | Suggested `code` |
| --- | --- | --- |
| Missing or blank `templatePath` | `400` | `MISSING_TEMPLATE_PATH` |
| Object not found in MinIO / unreadable | `404` | `TEMPLATE_NOT_FOUND` (or reuse existing MinIO error code from `/render`) |
| File is not a valid DOCX for Docxtemplater | `422` | `TEMPLATE_PARSE_ERROR` |
| Unexpected internal failure | `500` | `SCAN_FAILED` |

## Implementation alignment (for WS-02-T07)

1. **Register** `POST /scan-variables` in `docxtemplater-service/server.js` alongside existing routes.
2. **Reuse** the same Docxtemplater construction as **`POST /render`**: `PizZip` buffer from `getFileBuffer(templatePath)`, `Docxtemplater` with the same `modules`, `paragraphLoop`, `linebreaks`, and `parser` (`expressionParser.configure(...)` from `render.js`). Extraction MUST NOT use a divergent parser stack or coverage will disagree with rendered output.
3. **Extract** variables by compiling / introspecting the template structure without executing untrusted remote expressions supplied in the HTTP body. (Exact internal API is an implementation detail of WS-02-T07; the observable contract is the `variables` array above.)
4. **Java migration:** `CompositeCoverageService.scanVariablesFromFile` MUST call `POST /scan-variables` and MUST **stop** calling `/evaluate` for this purpose.

## Security notes

- **Read-only:** MinIO read, no writes, no outbound URLs from user input beyond resolving the configured bucket object.
- **No sandbox for HTTP body:** the body only carries `templatePath`; there is no `expression` field on this route.
- **Size limits:** respect the existing global JSON body limit; if scan needs a smaller cap, define it in WS-02-T07 with tests.

## Test requirements for WS-02-T07

1. **Node integration tests** (similar style to `docxtemplater-service/src/__tests__/integration.test.js`):
   - `200` with non-empty `variables` for a fixture template that contains known tags.
   - `200` with empty `variables` for a template with no placeholders (if applicable).
   - `400` when `templatePath` is missing.
   - `404` (or documented equivalent) when path does not exist in MinIO test harness.
2. **Java unit or integration tests** for `CompositeCoverageService` (or a focused helper test):
   - Mocks `RestTemplate` to assert the **URL path ends with `/scan-variables`** and request body contains `templatePath`.
   - Asserts that successful responses map `variables` into the coverage aggregation.
3. **Regression:** confirm `/evaluate` tests remain about expression evaluation only (no `templatePath`-for-variables behavior).

## Related documents

- `14-java-node-contract-mismatch-inventory.md` — **C-004** (historical mismatch summary).
- **WS-02-T07** — implement this contract and switch Java caller off `/evaluate`.

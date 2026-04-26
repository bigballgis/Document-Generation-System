# Java-to-Node Contract Mismatch Inventory

Task: `WS-02-T01`  
Date: 2026-04-26

## Purpose

This document enumerates contract mismatches between the Java backend and the Node.js `docxtemplater-service`. It is intended to drive small, independent remediation tasks that can be executed by lower-tier implementation models without re-discovering the integration gaps.

This is an inventory only:

- No production code changes are made in this task.
- No tests are added in this task.

## System Boundary

Java backend calls the Node service using `docxtemplater.service-url` (default `http://localhost:3000`). Node service registers routes in `docxtemplater-service/server.js`.

## Summary of Mismatches (High Signal)

| ID | Area | Severity | Java Caller | Node Route | Mismatch Type |
| --- | --- | --- | --- | --- | --- |
| C-001 | Document merge | P0 | `DocumentMergeService` | none | Java calls a route that Node does not expose |
| C-002 | Watermark | P0 | `WatermarkService` | none | Java calls a route that Node does not expose |
| C-003 | Expression evaluation type | P0 | `ExpressionEngineImpl` | `/evaluate` | Java sends enum names, Node expects `'javascript' | 'excel'` |
| C-004 | Composite coverage variable scan | Resolved (WS-02-T07) | `CompositeCoverageService` | `/scan-variables` | Was: Java called `/evaluate` for variable scanning; now calls dedicated `/scan-variables` per contract |

## C-001: Document Merge Route and Payload

### Java Caller

- File: `backend/src/main/java/com/docgen/service/DocumentMergeService.java`
- Calls: `POST {docxtemplaterServiceUrl}/merge`
- Sends body:
  - `documents`: `List<String>` base64-encoded DOCX documents
  - `insertPageBreaks`: boolean
  - `generateToc`: boolean
  - `outputFormat`: `"DOCX" | "PDF"`
- Expects response: `byte[]` merged document

### Node Service Reality

- File: `docxtemplater-service/server.js`
- Registered routes:
  - `/render`
  - `/evaluate`
  - `/convert-pdf`
  - `/merge-segments`
- There is no `/merge` route registered.
- Existing merge implementation:
  - File: `docxtemplater-service/src/routes/merge-segments.js`
  - Route: `POST /merge-segments`
  - Body:
    - `segments`: array
    - Each entry: `{ buffer: base64Docx, pageBreakBefore: boolean }`
  - Response: merged DOCX bytes

### Mismatch

- Java calls `/merge`, Node exposes `/merge-segments`.
- Payloads are different:
  - Java sends `documents[]` + options.
  - Node expects `segments[]` with per-segment options.
- Node merge-segments returns DOCX only (no PDF output selection).

### Risk

- Document merge feature will fail with 404 or incompatible behavior in a real environment.

### Suggested Target Contract (for WS-02-T04)

Choose one:

1. **Option A (preferred for minimal change)**: modify Java `DocumentMergeService` to call `/merge-segments` and adapt payload:
   - Convert `documents[]` into `segments[]` with `pageBreakBefore` derived from `insertPageBreaks`.
   - Ignore `generateToc` or implement TOC separately.
   - Handle `outputFormat=PDF` by calling `/convert-pdf` after merge.
2. **Option B**: implement `/merge` on Node as a wrapper around `/merge-segments` plus optional PDF conversion.

Decision drivers:

- Whether `generateToc` is actually required and how it should behave.
- Whether merge must output PDF directly.

## C-002: Watermark Route and Payload

### Java Caller

- File: `backend/src/main/java/com/docgen/service/WatermarkService.java`
- Calls: `POST {docxtemplaterServiceUrl}/watermark`
- Sends body:
  - `document`: base64-encoded DOCX bytes
  - `type`: `"text" | "image"`
  - Plus type-specific fields, e.g. `text`, `fontSize`, `color`, `opacity`, `rotation`, or `imageSource`, `position`
- Expects response: `byte[]` DOCX with watermark applied

### Node Service Reality

- No `/watermark` route is registered in `docxtemplater-service/server.js`.
- Watermark is implemented as an optional field of `/render`:
  - File: `docxtemplater-service/src/routes/render.js`
  - Uses `watermark` in request body to apply watermark after rendering.

### Mismatch

- Java calls a non-existent Node route.
- Java watermark expects a post-processing route that accepts a base64 document; Node watermark is integrated into `/render` after templating.

### Risk

- Standalone watermark application will fail at runtime.

### Suggested Target Contract (for WS-02-T05)

Choose one:

1. **Option A (minimal)**: add a Node `/watermark` route that accepts the Java payload, decodes the DOCX, applies watermark, and returns DOCX bytes.
2. **Option B**: refactor Java to use `/render` watermark support and remove the standalone watermark call path (may not be feasible if watermark is applied to documents not rendered by Node).

Decision drivers:

- Whether watermarking should apply to already-rendered documents.
- Whether watermark behavior must be shared between single-template and composite flows.

## C-003: Expression Evaluation Type Mapping

### Java Caller

- File: `backend/src/main/java/com/docgen/service/ExpressionEngineImpl.java`
- Calls: `POST {serviceUrl}/evaluate`
- Sends:
  - `expression`: string
  - `type`: `ExpressionType.name()` (example values likely include `JAVASCRIPT`, `EXCEL_FORMULA`)
  - `context`: object
- Expects:
  - `success: true`
  - `result`

### Node Service Reality

- File: `docxtemplater-service/src/routes/evaluate.js`
- Contract:
  - `type`: `'javascript' | 'excel'` (default `'javascript'`)
  - Requires `expression` to be a string
- Behavior:
  - `type === 'excel'` routes to Formula.js evaluation
  - Otherwise routes to JavaScript evaluation sandbox

### Mismatch

- Java sends enum names (e.g. `EXCEL_FORMULA`) but Node checks only `type === 'excel'`.
- As a result, Excel formulas may be executed through the JavaScript evaluation path.

### Risk

- Incorrect evaluation semantics.
- Unexpected errors or security posture changes depending on sandbox behavior.

### Suggested Target Contract (for WS-02-T02)

- Java maps internal expression types to Node-recognized strings:
  - JavaScript expressions → `'javascript'`
  - Excel formulas → `'excel'`
- Node explicitly rejects unknown `type` values rather than treating them as JavaScript.

## C-004: Composite Coverage Variable Scanning

**Resolution (WS-02-T07):** Java calls `POST {docxtemplaterServiceUrl}/scan-variables` with `{ templatePath }`; Node implements `docxtemplater-service/src/routes/scan-variables.js` (same Docxtemplater/parser stack as `/render`). Contract: [20-composite-coverage-variable-scan-contract.md](20-composite-coverage-variable-scan-contract.md).

### Historical mismatch (pre-WS-02-T07)

### Java Caller (before)

- File: `backend/src/main/java/com/docgen/service/CompositeCoverageService.java`
- Called: `POST {docxtemplaterServiceUrl}/evaluate` (incorrect reuse)
- Sent body:
  - `templatePath`: MinIO path
- Expected response field:
  - `variables: string[]`

### Node `/evaluate` reality (unchanged; unrelated to scanning)

- File: `docxtemplater-service/src/routes/evaluate.js`
- Requires: `expression` string (sandbox evaluation only)

### Risk (mitigated)

- Prior behavior could return empty variable lists or swallow errors; coverage is now sourced from real template placeholders via `/scan-variables`.

### Suggested Target Contract (for WS-02-T06 and WS-02-T07)

**Adopted (WS-02-T06):** normative contract is documented in [20-composite-coverage-variable-scan-contract.md](20-composite-coverage-variable-scan-contract.md) — dedicated **`POST /scan-variables`** on Node (read-only DOCX inspection, no reuse of `/evaluate`).

Historical options retained for archive context:

- Option A (adopted): implement `POST /scan-variables` on Node:
  - Input: `{ templatePath }`
  - Output: `{ variables: string[] }`
  - Must not execute untrusted expressions.
- Option B (not selected): implement variable scanning in Java by parsing DOCX text parts for `{...}` tags with a constrained grammar.

## Additional Observations (Not Contract Breakers)

- Node `server.js` applies a global 50 MB JSON limit for all routes. Some endpoints (merge, render, convert-pdf) may need tighter per-route limits.
- Java callers rely on `RestTemplate` and currently do not guarantee timeouts by default unless `RestTemplateConfig` is updated.

## Next Steps

Proceed in this order:

1. `WS-02-T03`: Add Node tests for unknown evaluate type rejection.
2. `WS-02-T02`: Align expression type mapping.
3. `WS-02-T04`: Align merge route.
4. `WS-02-T05`: Align watermark route.
5. `WS-02-T06`: Define variable scan contract. (Done)
6. `WS-02-T07`: Implement variable scan contract. (Done)

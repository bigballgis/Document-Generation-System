# Iteration Log

This file records the staged remediation process. Each workstream start, fix, validation run, or review pass should append an entry.

## 2026-04-26: Review Baseline Established

Completed:

- Ran GPT-5.5 read-only review passes across:
  - Backend security and data protection.
  - Backend business flows.
  - Frontend architecture and interactions.
  - Docxtemplater rendering service.
  - Testing, documentation, and requirement governance.
  - Infrastructure, Docker, configuration, and delivery.
- Created local audit directory: `docs/audits/full-project-review-2026-04-26/`
- Created baseline documents:
  - `README.md`
  - `00-preflight-and-skills.md`
  - `01-executive-findings.md`
  - `02-remediation-and-optimization-roadmap.md`
  - `03-workstreams.md`
  - `04-evidence-index.md`
  - `05-traceability-matrix.md`
  - `06-validation-commands.md`

Completed after the language-policy update:

- Converted the audit package content to English.
- Added the rule that user-facing chat remains Simplified Chinese.
- Added the rule that all files, comments, documentation, and generated artifacts must be English.
- Added planning guidance for lower-tier implementation models.

Not completed:

- No production code remediation has started.
- No build or test command has been run.
- The full line-level requirement traceability matrix is not complete yet.

Recommended next steps:

1. Start WS-01 for OnlyOffice callback and SSRF risk.
2. Build a WS-02 Java-to-Node contract test baseline in parallel or immediately after WS-01 containment.
3. Switch to Agent mode before changing non-Markdown files.

## 2026-04-26: WS-07-T01 Completed

Task ID: WS-07-T01  
Summary: Classified dirty working tree changes into buckets for safe staged remediation execution.  
Output: `13-dirty-working-tree-classification.md`  
Files changed: audit documentation only.  
Validation: not applicable beyond documentation consistency checks.  
Notes: no files were deleted, committed, or cleaned up as part of this task.

## 2026-04-26: WS-02-T01 Completed

Task ID: WS-02-T01  
Summary: Documented the Java-to-Node contract mismatches as an explicit inventory for staged remediation.  
Output: `14-java-node-contract-mismatch-inventory.md`  
Files changed: audit documentation only.  
Validation: not applicable beyond documentation consistency checks.  
Notes: no production code, tests, or migrations were modified in this task.

## 2026-04-26: WS-01-T01 Completed

Task ID: WS-01-T01  
Summary: Added unit tests to characterize the current OnlyOffice main-template callback behavior (status gating, missing URL early return, empty download no-op).  
Output: `backend/src/test/java/com/docgen/service/OnlyOfficeServiceTest.java`  
Files changed: backend tests only.  
Validation commands: `mvn -Dtest=OnlyOfficeServiceTest test` (from `backend/`)  
Validation result: BUILD SUCCESS (6 tests).  
Remaining risks: this is characterization only; callback authentication / URL allowlisting / size limits are not addressed yet.  
Next step: start WS-01 hardening tasks (JWT/source validation, URL allowlist, size/type limits) with tests first where feasible.

## 2026-04-26: WS-01 Callback Hardening (Phase 1) Completed

Workstream: WS-01  
Summary: Added OnlyOffice callback hardening for main-template saves: require OnlyOffice JWT (configurable), enforce download URL host allowlist, validate response content-type, and enforce a maximum download size limit. Segment callbacks now reuse the same URL allowlist and JWT requirement.  
Files changed:
- `backend/src/main/java/com/docgen/service/OnlyOfficeService.java`
- `backend/src/main/java/com/docgen/controller/OnlyOfficeController.java`
- `backend/src/main/java/com/docgen/controller/CompositeTemplateController.java`
- `backend/src/main/resources/application.yml`
- `backend/src/test/java/com/docgen/service/OnlyOfficeServiceTest.java`
Validation commands:
- `mvn -Dtest=OnlyOfficeServiceTest test` (from `backend/`)
Validation result: BUILD SUCCESS (8 tests).  
Remaining risks: network-level source validation (e.g., reverse proxy IP allowlist) is not implemented; allowlist is host-based only.  
Next step: consider adding integration tests with a mock HTTP server for content-type and size enforcement paths, and tighten SecurityConfig exposure once deployment routing is confirmed.

## 2026-04-26: WS-01-T02 Completed

Task ID: WS-01-T02  
Summary: Configured the shared `RestTemplate` bean with connect/read timeouts to prevent outbound HTTP calls from blocking indefinitely.  
Files changed:
- `backend/src/main/java/com/docgen/config/RestTemplateConfig.java`
- `backend/src/main/resources/application.yml`
- `backend/src/test/java/com/docgen/config/RestTemplateConfigTest.java`
Validation commands:
- `mvn "-Dtest=RestTemplateConfigTest,OnlyOfficeServiceTest" test` (from `backend/`)
Validation result: BUILD SUCCESS.  
Next step: proceed with `WS-01-T05` (SSRF URL policy helper) to consolidate and reuse allowlist logic across callback paths.

## 2026-04-26: WS-01-T05 Completed

Task ID: WS-01-T05  
Summary: Added a reusable outbound URL validation helper (`OutboundUrlPolicy`) with deny-by-default host allowlists, scheme/port restrictions, DNS resolution, and classification of disallowed address ranges (including always-blocked metadata IPv4).  
Files changed:
- `backend/src/main/java/com/docgen/security/url/OutboundUrlPolicy.java`
- `backend/src/main/java/com/docgen/security/url/UrlPolicyProperties.java`
- `backend/src/main/java/com/docgen/security/url/UrlPolicyConfiguration.java`
- `backend/src/main/java/com/docgen/security/url/UrlValidationResult.java`
- `backend/src/test/java/com/docgen/security/url/OutboundUrlPolicyTest.java`
- `backend/src/main/resources/application.yml`
Validation commands:
- `mvn "-Dtest=OutboundUrlPolicyTest" test` (from `backend/`)
- `mvn test` (from `backend/`) — **FAILED** in this environment due to pre-existing issues (missing `org.h2.Driver` for `DocgenApplicationTests`, missing Docker for Testcontainers integration tests, and unrelated assertion failures in export/property tests).  
Next step: `WS-01-T06` / `WS-01-T07` / `WS-01-T08` — wire `OutboundUrlPolicy` into OnlyOffice main callback, segment callback, and webhook delivery respectively (remove duplicated string-prefix checks).

## 2026-04-26: WS-01-T06 / WS-01-T07 / WS-01-T08 Completed

Task IDs: WS-01-T06, WS-01-T07, WS-01-T08  
Summary: Integrated the shared `OutboundUrlPolicy` into outbound fetch paths:
- Main OnlyOffice callback downloads now validate via `validateHttpUrlWithMergedHosts` (global `url-policy.allowed-hosts` merged with `onlyoffice.url` host + `onlyoffice.callback.allowed-hosts`) including DNS + address classification.
- Segment OnlyOffice callback reuses `OnlyOfficeService.isAllowedCallbackDownloadUrl` (same policy path as main template).
- Webhook delivery validates URLs via `validatePublicEgressHttpUrl` before `RestTemplate` calls (https-only by default via `url-policy.webhook-require-https`).
Files changed:
- `backend/src/main/java/com/docgen/security/url/OutboundUrlPolicy.java`
- `backend/src/main/java/com/docgen/security/url/UrlPolicyProperties.java`
- `backend/src/main/java/com/docgen/service/OnlyOfficeService.java`
- `backend/src/main/java/com/docgen/service/WebhookService.java`
- `backend/src/main/resources/application.yml`
- `backend/src/test/java/com/docgen/security/url/OutboundUrlPolicyTest.java`
- `backend/src/test/java/com/docgen/service/OnlyOfficeServiceTest.java`
- `backend/src/test/java/com/docgen/service/WebhookServiceTest.java`
Validation commands:
- `mvn "-Dtest=OutboundUrlPolicyTest,OnlyOfficeServiceTest,WebhookServiceTest,RestTemplateConfigTest" test` (from `backend/`)
Validation result: BUILD SUCCESS.  
Notes: full `mvn test` may still fail in environments without Docker/H2 test dependencies; not re-run as part of this batch.

## 2026-04-26: WS-01-T09 Completed

Task ID: WS-01-T09  
Summary: Introduced `CallbackDocumentDownloadHelper` for bounded streaming downloads of OnlyOffice callback documents; `OnlyOfficeService` and composite segment callback now share it (no `getForObject(..., byte[].class)` on callback paths).  
Files changed:
- `backend/src/main/java/com/docgen/service/CallbackDocumentDownloadHelper.java`
- `backend/src/main/java/com/docgen/service/OnlyOfficeService.java`
- `backend/src/main/java/com/docgen/controller/CompositeTemplateController.java`
- `backend/src/test/java/com/docgen/service/CallbackDocumentDownloadHelperTest.java`
- `backend/src/test/java/com/docgen/service/OnlyOfficeServiceTest.java`
Validation commands:
- `mvn "-Dtest=CallbackDocumentDownloadHelperTest,OnlyOfficeServiceTest" test` (from `backend/`)
Validation result: BUILD SUCCESS.

## 2026-04-26: WS-01-T08 Verified

Task ID: WS-01-T08  
Summary: Confirmed webhook delivery already validates URLs via `OutboundUrlPolicy.validatePublicEgressHttpUrl` in `WebhookService.sendWithRetry` before `RestTemplate.exchange`; disallowed URLs are logged with reason code only and `WebhookServiceTest` covers private URL and HTTP-when-HTTPS-required cases. No code changes required for T08 in this pass.

## 2026-04-26: WS-01-T04 Completed

Task ID: WS-01-T04  
Summary: Segment OnlyOffice callback now returns `{"error":1}` when URL policy rejects the download URL, JWT validation fails (when required), assembly lookup fails, segment index or file path is invalid, download is empty, or `ContentIsolationValidator` rejects the payload; infrastructure failures still map to `BusinessException` with HTTP 500. Benign statuses and missing download URL remain `{"error":0}`.  
Files changed:
- `backend/src/main/java/com/docgen/controller/CompositeTemplateController.java`
- `backend/src/test/java/com/docgen/controller/CompositeTemplateControllerSegmentCallbackTest.java`
Validation commands:
- `mvn "-Dtest=CompositeTemplateControllerSegmentCallbackTest" test` (from `backend/`)
Validation result: BUILD SUCCESS.  
Notes: full `mvn test` not re-run; focused test class only.

## 2026-04-26: WS-01-T10 Completed

Task ID: WS-01-T10  
Summary: Added `ProductionSecretGuard` plus `ProductionSecretStartupValidator` (`ApplicationRunner`) to fail startup in `prod` / `production` profiles when `jwt.secret`, `encryption.key`, `minio.access-key`, `minio.secret-key`, or `onlyoffice.jwt-secret` is missing, blank, or matches documented defaults from `application.yml`. Errors list property keys and issue type only (no secret values). Documented behavior in `application.yml` comments. Repaired `SecurityConfig` Javadoc that accidentally terminated the block comment (`*/` inside path patterns). Test profile fixes: added `test`-scoped H2 dependency for `application-test.yml`; set `onlyoffice.jwt-secret` to a JJWT-safe length; set `encryption.key` to a valid Base64 32-byte test key.  
Files changed:
- `backend/src/main/java/com/docgen/config/ProductionSecretGuard.java`
- `backend/src/main/java/com/docgen/config/ProductionSecretStartupValidator.java`
- `backend/src/main/java/com/docgen/config/SecurityConfig.java`
- `backend/src/main/resources/application.yml`
- `backend/pom.xml`
- `backend/src/test/resources/application-test.yml`
- `backend/src/test/java/com/docgen/config/ProductionSecretGuardTest.java`
Validation commands:
- `mvn "-Dtest=ProductionSecretGuardTest,DocgenApplicationTests" test` (from `backend/`)
Validation result: BUILD SUCCESS.  
Notes: full `mvn test` not run in this pass.

## 2026-04-26: WS-02-T05 Completed

Task ID: WS-02-T05  
Summary: Chose dedicated **`POST /watermark`** on the Docxtemplater service (reuses `utils/watermark.js`, same as optional watermark on `/render`). Implemented `src/routes/watermark.js`, mounted in `server.js`. Node rejects remote image URLs; strips `data:image/...;base64,` prefix for `applyImageWatermark`. Java `WatermarkService` documents contract and rejects `http(s)` image sources before outbound call. Added integration tests and audit doc `15-watermark-api-contract.md`.  
Files changed:
- `docxtemplater-service/src/routes/watermark.js`
- `docxtemplater-service/src/routes/render.js`
- `docxtemplater-service/server.js`
- `docxtemplater-service/src/__tests__/integration.test.js`
- `backend/src/main/java/com/docgen/service/WatermarkService.java`
- `backend/src/main/java/com/docgen/dto/ImageWatermarkConfig.java`
- `backend/src/test/java/com/docgen/service/WatermarkServiceTest.java`
- `docs/audits/full-project-review-2026-04-26/15-watermark-api-contract.md`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`
Validation commands:
- `npm test` (from `docxtemplater-service/`)
- `mvn "-Dtest=WatermarkServiceTest" test` (from `backend/`)
Validation result: BUILD SUCCESS (80 Jest tests; WatermarkServiceTest).

## 2026-04-26: WS-02-T04 Completed

Task ID: WS-02-T04  
Summary: `DocumentMergeService` now calls `POST /merge-segments` with a `segments` array (`buffer` per doc, `pageBreakBefore` on each segment after the first when `insertPageBreaks` is true) instead of the non-existent `/merge` contract. Added `buildMergeSegmentsPayload` helper; `generateToc` / `outputFormat` remain Java-side metadata only (merge route always returns DOCX bytes). Updated unit and property tests; noted route in `server.js`.  
Files changed:
- `backend/src/main/java/com/docgen/service/DocumentMergeService.java`
- `backend/src/test/java/com/docgen/service/DocumentMergeServiceTest.java`
- `backend/src/test/java/com/docgen/property/DocumentMergeOrderPropertyTest.java`
- `docxtemplater-service/server.js`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`
Validation commands:
- `mvn "-Dtest=DocumentMergeServiceTest,DocumentMergeOrderPropertyTest" test` (from `backend/`)
Validation result: BUILD SUCCESS.  
Notes: `npm test` not re-run (no Node route logic change).

## 2026-04-26: WS-02-T02 Completed

Task ID: WS-02-T02  
Summary: `ExpressionType` now exposes `toEvaluateApiType()` mapping `JAVASCRIPT` → `javascript` and `EXCEL_FORMULA` → `excel` for Docxtemplater `POST /evaluate`. `ExpressionEngineImpl` sends these literals and normalizes structured `{ code, message }` error payloads from Node. Added `ExpressionTypeTest` and extended `ExpressionEngineImplTest` (request body assertions, structured error case); reset `RestTemplate` mock between tests.  
Files changed:
- `backend/src/main/java/com/docgen/entity/ExpressionType.java`
- `backend/src/main/java/com/docgen/service/ExpressionEngineImpl.java`
- `backend/src/test/java/com/docgen/entity/ExpressionTypeTest.java`
- `backend/src/test/java/com/docgen/service/ExpressionEngineImplTest.java`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`
Validation commands:
- `mvn "-Dtest=ExpressionEngineImplTest,ExpressionTypeTest" test` (from `backend/`)
Validation result: BUILD SUCCESS.  
Notes: `npm test` in `docxtemplater-service/` not re-run for this card (WS-02-T03 already covered `/evaluate` type rejection).

## 2026-04-26: WS-02-T03 Completed

Task ID: WS-02-T03  
Summary: `/evaluate` now rejects unknown or non-string `type` with HTTP 400 (`UNKNOWN_EXPRESSION_TYPE` / `INVALID_EXPRESSION_TYPE`) before any sandbox or formula evaluation; omitted or blank-after-trim `type` still defaults to `javascript`. Integration tests cover unknown type, invalid type shape, default path, and explicit `javascript`.  
Files changed:
- `docxtemplater-service/src/routes/evaluate.js`
- `docxtemplater-service/src/__tests__/integration.test.js`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`
Validation commands:
- `npm test` (from `docxtemplater-service/`)
Validation result: PASS (74 tests).

## 2026-04-26: WS-03-T04 Completed

Task ID: WS-03-T04  
Summary: Documented a single product decision: **defer** `render-config.json` in composite template ZIP export/import until a reviewed JSON schema, SSRF-safe validation, ZIP allowlist extension under existing `composite-import.zip` limits, and a persistence model on `Template` (or a related entity) exist. Recorded impact (no round-trip of watermark/barcode via ZIP), backlog target, and follow-up criteria. Updated Kiro `requirements.md` (English R7 status block), `tasks.md` (Task 6 deferred), traceability **REQ-R7-001** to **Deferred**, and audit `README.md` link.  
Files changed:
- `docs/audits/full-project-review-2026-04-26/17-composite-r7-render-config-scope.md`
- `docs/audits/full-project-review-2026-04-26/README.md`
- `docs/audits/full-project-review-2026-04-26/05-traceability-matrix.md`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`
- `.kiro/specs/composite-template-full-import/requirements.md`
- `.kiro/specs/composite-template-full-import/tasks.md`
Validation commands:
- `rg "\\p{Han}" docs/audits/full-project-review-2026-04-26/17-composite-r7-render-config-scope.md` (expect no matches on new file)
Validation result: no Chinese text in new English decision document.  
Notes: **WS-03-T05** and **WS-03-T06** remain blocked by this deferral until a new decision supersedes WS-03-T04.

## 2026-04-26: WS-04-T01 Completed

Task ID: WS-04-T01  
Summary: Added `ContentDiffServiceTest` with Mockito-backed `DocxTextExtractor` covering `computeLineDiff`: both-empty list, null-as-empty vs non-empty, identical single-line (one `EQUAL` row), added line, removed line, `MODIFIED` line; `computeContentDiff` short-circuit for identical bodies (empty `lines`, `contentChanged=false`), truncated-only flag when extractors report truncation, and differing bodies; `hasContentChanged` true/false. Documents that **identical multi-line text** still short-circuits at `computeContentDiff` (no diff lines), while **identical single-line via `computeLineDiff` alone** yields one `EQUAL` line (algorithm path).  
Files changed:
- `backend/src/test/java/com/docgen/service/ContentDiffServiceTest.java`
- `docs/audits/full-project-review-2026-04-26/05-traceability-matrix.md`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`
Validation commands:
- `mvn -Dtest=ContentDiffServiceTest test` (from `backend/`)
Validation result: BUILD SUCCESS.  
Notes: full `mvn test` not run. Followed by **WS-04-T02** (`DocxTextExtractorTest`).

## 2026-04-26: WS-04-T02 Completed

Task ID: WS-04-T02  
Summary: Added `DocxTextExtractorTest` using in-memory ZIP docx payloads: single-paragraph extraction, multi-paragraph text joined with newlines, `w:tab` inside a run, missing `word/document.xml` (`BusinessException` / `CONTENT_DIFF_EXTRACTION_FAILED`), invalid ZIP bytes, UTF-8 truncation with `ExtractedText.truncated` and marker suffix, and `extractTextFromMinio` via mocked `MinioClient.getObject`. Namespace for OOXML is declared on `w:document` only; multi-paragraph case must call the static `body(...)` wrapper (local variable must not shadow it).  
Files changed:
- `backend/src/test/java/com/docgen/service/DocxTextExtractorTest.java`
- `docs/audits/full-project-review-2026-04-26/05-traceability-matrix.md`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`
Validation commands:
- `mvn -Dtest=DocxTextExtractorTest test` (from `backend/`)
Validation result: BUILD SUCCESS.  
Notes: full `mvn test` (task card default) not run. Next: **WS-04-T03** (identical diff contract documentation).

## 2026-04-26: WS-04-T03 Completed

Task ID: WS-04-T03  
Summary: Recorded the **canonical identical-text contract** for DOCX segment content diff: when extracted plain text is equal, **`contentChanged=false`** and **`contentDiffs` is empty** (no synthetic `EQUAL` rows); distinguished this from the package-private **`computeLineDiff`** primitive (both-empty → empty list; identical non-empty → `EQUAL` rows only). Documented client compatibility (do not infer equality from `EQUAL` rows). Added audit note [19-docx-identical-diff-contract.md](19-docx-identical-diff-contract.md), English amendment in `.kiro/specs/docx-content-diff/requirements.md`, `includeContentDiff` and content-diff field table in `docs/segment-version-api.md`, and README link.  
Files changed:
- `docs/audits/full-project-review-2026-04-26/19-docx-identical-diff-contract.md`
- `docs/audits/full-project-review-2026-04-26/README.md`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`
- `docs/audits/full-project-review-2026-04-26/05-traceability-matrix.md`
- `docs/segment-version-api.md`
- `.kiro/specs/docx-content-diff/requirements.md`
Validation commands:
- `rg "[\\p{Han}]" docs/audits/full-project-review-2026-04-26/19-docx-identical-diff-contract.md docs/segment-version-api.md` (scoped to new/edited English sections; full path per task card still matches legacy Chinese under `.kiro/specs/docx-content-diff/requirements.md`)
Validation result: no Han characters in `19-docx-identical-diff-contract.md` or in the newly added `segment-version-api.md` paragraphs (verified via search).  
Notes: Implementation alignment for **WS-04-T04** recorded in a follow-up entry below.

## 2026-04-26: WS-04-T04 Completed

Task ID: WS-04-T04  
Summary: Confirmed `ContentDiffService.computeContentDiff` already matches WS-04-T03 (short-circuit on `String.equals`, empty `lines`, `contentChanged=false`, truncation preserved). Documented the contract on the public method with Javadoc; added `computeContentDiff_identicalEmptyExtractedText_returnsEmptyLinesAndNoContentChange` so identical **empty** extracted bodies are explicitly covered (contrasts with `computeLineDiff` both-empty, which is never invoked on that path).  
Files changed:
- `backend/src/main/java/com/docgen/service/ContentDiffService.java`
- `backend/src/test/java/com/docgen/service/ContentDiffServiceTest.java`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`
Validation commands:
- `mvn -Dtest=ContentDiffServiceTest test` (from `backend/`)
Validation result: BUILD SUCCESS (12 tests).  
Notes: full `mvn test` not run (task card default). Next: **WS-04-T05** (docx XML parse failure semantics).

## 2026-04-26: WS-03-T01 Completed

Task ID: WS-03-T01  
Summary: Added ZIP import boundary characterization tests for `CompositeImportExportService.importFromZip`: non-ZIP payload, empty archive, malformed `config.json`, many ignored entry paths, zip-slip-style `segments/../segments/...` names (segment key retains `..`), 512 KiB segment buffered entirely in memory, and 200 junk entries before core files (no entry-count limit yet). Documents current lack of path/size/count enforcement ahead of `WS-03-T02`.  
Files changed:
- `backend/src/test/java/com/docgen/service/CompositeImportExportServiceTest.java`
- `docs/audits/full-project-review-2026-04-26/05-traceability-matrix.md`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`
Validation commands:
- `mvn -Dtest=CompositeImportExportServiceTest test` (from `backend/`)
Validation result: BUILD SUCCESS.  
Notes: full `mvn test` not run (task card default; environment may still hit unrelated failures).

## 2026-04-26: WS-03-T02 Completed

Task ID: WS-03-T02  
Summary: Added configurable ZIP import limits (`composite-import.zip` in `application.yml` via `CompositeZipImportProperties`): max compressed archive bytes (multipart size + stream cap), max non-directory entry count, max per-entry uncompressed bytes (bounded read instead of `readAllBytes()` when limits are on), max total uncompressed bytes across entries, and optional compression expansion ratio when central directory reports compressed and uncompressed sizes. Enforced strict allowlist for entry paths (`config.json`, `test-data.json`, `parameters.json`, `coverage-report.json`, and single-segment `segments|headers|footers/<name>.docx` without `..`, `/`, or `\\` in `<name>`). Rejects unsupported paths instead of silently ignoring. Wired `getInputStream()` IOException to `IMPORT_INVALID_FILE`. Updated export property tests: DRAFT is exportable alongside ACTIVE per current service rules.  
Files changed:
- `backend/src/main/java/com/docgen/config/CompositeZipImportProperties.java`
- `backend/src/main/java/com/docgen/service/CompositeImportExportService.java`
- `backend/src/main/resources/application.yml`
- `backend/src/test/java/com/docgen/service/CompositeImportExportServiceTest.java`
- `backend/src/test/java/com/docgen/service/CompositeImportExportServiceExportTest.java`
- `backend/src/test/java/com/docgen/property/ExportConstraintPropertyTest.java`
- `docs/audits/full-project-review-2026-04-26/05-traceability-matrix.md`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`
Validation commands:
- `mvn "-Dtest=CompositeImportExportServiceTest,CompositeImportExportServiceExportTest,ExportConstraintPropertyTest" test` (from `backend/`)
- `mvn -Dtest=DocgenApplicationTests test` (from `backend/`)
Validation result: BUILD SUCCESS (full `mvn test` not run).  
Remaining risks: compression ratio check is skipped when ZIP headers omit sizes (`-1`). MinIO object name sanitization completed in `WS-03-T03`.

## 2026-04-26: WS-03-T03 Completed

Task ID: WS-03-T03  
Summary: MinIO object keys for composite ZIP import no longer embed raw segment/header/footer logical names: `sanitizeMinioObjectNameComponent` normalizes to the same allowed character class as export-side `uniqueFileName` (letters, digits, CJK BMP, `_`, `-`), replaces `..` defensively, collapses underscores, trims, caps length at 120, and falls back to `unnamed`. Assembly segment display names remain from `config.json` / export entries; only the stored object path suffix changes. Upload failure logs use `tenantId` and sanitized component instead of full object paths.  
Files changed:
- `backend/src/main/java/com/docgen/service/CompositeImportExportService.java`
- `backend/src/test/java/com/docgen/service/CompositeImportExportServiceTest.java`
- `docs/audits/full-project-review-2026-04-26/05-traceability-matrix.md`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`
Validation commands:
- `mvn -Dtest=CompositeImportExportServiceTest test` (from `backend/`)
Validation result: BUILD SUCCESS.  
Notes: full `mvn test` not run.

## 2026-04-26: Template testing hardening (ad-hoc)

Summary: Aligned frontend test-case types with backend JSON (`testDataJson`, `comparisonType`, `TestReportDTO` counters). `TemplateTestService` now runs real `DocumentGeneratorService` rendering plus `DocxTextExtractor` for text assertions and SHA-256 of DOCX bytes for snapshot mode. Added `TemplateTestRenderOutcome`, composite in-memory render helper, backward-compatible Jackson aliases on `CreateTestCaseRequest`, and `testCaseName` on `TestResultDTO`. Improved template detail test UI (hints, JSON validation, result drawer).  
Validation: `mvn "-Dtest=TemplateTestServiceTest" test` and `mvn -DskipTests compile` from `backend/` — BUILD SUCCESS. Frontend Vitest for `TestCaseFormDialog` hit a worker timeout in this environment (no test assertion failures observed).

## 2026-04-26: Template test list pagination and workspace embedding (ad-hoc)

Summary: `GET /api/templates/{id}/test-cases` now returns a Spring `Page<TestCaseDTO>` with optional `q` (name contains, case-insensitive) and standard `page`/`size`. Latest `lastRun` per case is loaded in one PostgreSQL `DISTINCT ON` query. Frontend `getTestCases` consumes `PageResult`; `TestCaseManagement` adds search, pagination, last-run column, empty-page rewind after deletes, and refresh after run. `TestStage` embeds `TestCaseManagement` under a collapse panel; Pinia `refreshTestCases` loads up to 500 rows for export summaries. Ran `git gc --prune=now`; `git push` to GitHub failed in this environment (connection timeout to github.com:443). Commits: `d5d94e4` (this batch) on top of prior template-test commits.  
Validation: `mvn "-Dtest=TemplateTestServiceTest" test` (from `backend/`) — BUILD SUCCESS.

## 2026-04-26: Template test mocks and workspace UX (ad-hoc)

Summary: Vitest mocks for `getTestCases` now return a `PageResult` shape (fixes `refreshTestCases` using `.content`). Added `getTestCases` contract test in `market-extensions.test.ts`. `TestCaseManagement` supports `hideIntro` to suppress the long info alert when embedded under workspace `TestStage`.  
Validation: `npx vitest run src/__tests__/api/market-extensions.test.ts src/__tests__/views/TemplateWorkspaceIndex.test.ts` (from `frontend/`) — passed.

## 2026-04-26: Test case list page cap and UX (ad-hoc)

Summary: `TemplateTestService.listTestCases` caps `page`/`size` via `capPageable` (max page size 500, minimum effective size 20 when requested size is below 1). `TestCaseManagement` resets to page 1 after creating a test case and after Run All so the refreshed list shows the newest ordering. Added unit tests for oversized requests, zero-size `Pageable`, and unchanged pass-through.  
Validation: `mvn "-Dtest=TemplateTestServiceTest" test` (from `backend/`) — BUILD SUCCESS.

## 2026-04-26: WS-05-T07 Template test service semantics

Summary: Completed task card **WS-05-T07**. Added characterization tests in `TemplateTestServiceTest` (`characterization_*`) proving `runTestCase` calls `renderForTemplateTest` with parsed `testDataJson`, `TEXT_CONTENT` invokes `DocxTextExtractor` on rendered DOCX bytes, `runAllTests` renders once per loaded case, and `listTestCases` does not touch the generator. Added audit note [16-template-test-execution-semantics.md](16-template-test-execution-semantics.md) and README link; traceability row `REQ-TEST-PIPELINE-001` set to Verified.  
Validation: `mvn "-Dtest=TemplateTestServiceTest" test` (from `backend/`) — BUILD SUCCESS.

## 2026-04-26: WS-05-T02 Centralized generation ACTIVE eligibility

Summary: Implemented **WS-05-T02**. Added `TemplateGenerationEligibilityService.requireActiveForDocumentGeneration` and applied it in `DocumentGeneratorService.generateDocument` (covers sync API via `DynamicApiService`, async worker, batch items, scheduled tasks), `DynamicApiService.generateViaApi` (replacing inline check), `AsyncDocumentService.submitAsyncGeneration`, and `BatchDocumentService` (submit + fail-fast in `processBatchGeneration`). `DocumentGeneratorService.renderForTemplateTest` intentionally skips the check so template tests still run on non-`ACTIVE` templates. Added unit tests; fixed `BatchGenerationCompletenessPropertyTest` constructor and set template status `ACTIVE` for batch processing.  
Validation: `mvn -q compile test "-Dtest=TemplateGenerationEligibilityServiceTest,DocumentGeneratorServiceGenerationEligibilityTest,AsyncDocumentServiceSubmitEligibilityTest,BatchDocumentServiceSubmitEligibilityTest,TemplateTestServiceTest,BatchGenerationCompletenessPropertyTest"` and `mvn -q test-compile` (from `backend/`) — BUILD SUCCESS. Full `mvn test` not re-run (known Docker / unrelated failures in this environment).

## 2026-04-26: WS-05-T03 Generate API version parameter characterization

Summary: Completed **WS-05-T03**. Added `DynamicApiServiceVersionParameterTest` covering null `version` (no `TemplateVersionRepository` calls), history disallowed + non-latest (`GENERATE_VERSION_NOT_ALLOWED`), latest-only success, history-allowed success, missing version (`TEMPLATE_VERSION_NOT_FOUND`), and proof that `GenerateDocumentRequest` is passed through unchanged (no version field). Documented observed behavior: `?version=n` does not alter the render file path — `DocumentGeneratorService` always uses `Template.templateFilePath`; version rows are validation-only. Audit note: [18-generate-api-version-parameter-behavior.md](18-generate-api-version-parameter-behavior.md); traceability `REQ-GEN-VERSION-CHAR-001`.  
Validation: `mvn -q test "-Dtest=DynamicApiServiceVersionParameterTest"` (from `backend/`) — BUILD SUCCESS.

## 2026-04-26: WS-05-T04 Versioned rendering contract (documentation)

Summary: Completed **WS-05-T04** (no production code). Decided and documented normative contract: synchronous `POST /api/generate/{templateId}?version=n` — `version` is **supported for validation and policy only**; **historical `template_versions` file paths are not used** for render output; generation **always** uses current `templates.template_file_path` unless WS-05-T05 supersedes. Async/batch have no `version` parameter. Added canonical English spec `docs/versioned-template-generation-contract.md`; linked from audit `18-generate-api-version-parameter-behavior.md` and `README.md`; traceability `REQ-GEN-VERSION-CONTRACT-001`.  
Validation: `rg "[\\p{Han}]" docs/versioned-template-generation-contract.md` — no matches (ASCII / English only in new file).

## Entry Template

```text
Date:
Workstream:
Summary:
Files changed:
Validation commands:
Validation result:
Remaining risks:
Related tracking items:
Next step:
```

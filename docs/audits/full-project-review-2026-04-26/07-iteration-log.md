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

## 2026-04-26: WS-04-T05 Completed

Task ID: WS-04-T05  
Summary: `DocxTextExtractor` no longer treats **malformed `word/document.xml`** as empty text: SAX parse failures on the main document throw **`BusinessException`** with **`CONTENT_DIFF_EXTRACTION_FAILED`**. **`word/header*.xml`** and **`word/footer*.xml`** remain **best-effort** (warn and skip part) so a corrupt header does not mask body extraction. Class Javadoc documents the policy; XXE-related features unchanged (`disallow-doctype-decl`, external entities off). Tests: corrupt body XML, body with `<!DOCTYPE` (rejected), corrupt `word/header1.xml` alongside valid body.  
Files changed:
- `backend/src/main/java/com/docgen/service/DocxTextExtractor.java`
- `backend/src/test/java/com/docgen/service/DocxTextExtractorTest.java`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`
Validation commands:
- `mvn -Dtest=DocxTextExtractorTest test` (from `backend/`)
Validation result: BUILD SUCCESS (10 tests).  
Notes: full `mvn test` not run. Next: **WS-04-T06** (concurrent segment publish).

## 2026-04-26: WS-04-T06 Completed

Task ID: WS-04-T06  
Summary: **`publishSegment`** now runs each allocate+copy+**persist** attempt in **`PROPAGATION_REQUIRES_NEW`** via `TransactionTemplate`, with up to **5 retries** on **`DataIntegrityViolationException`** when the failure matches **`uq_segment_versions_template_name_version`** (or PostgreSQL **23505** on `segment_versions`). Exhausted retries yield **`BusinessException`** **`SEGMENT_VERSION_PUBLISH_CONFLICT`** (**409 CONFLICT**). Non-version unique violations propagate unchanged. Added **`SegmentVersionServiceTest`** (constraint detection, first publish, retry success, unrelated DIV, exhaustion). Empty segment path publish message translated to English.  
Files changed:
- `backend/src/main/java/com/docgen/service/SegmentVersionService.java`
- `backend/src/main/java/com/docgen/exception/ErrorCode.java`
- `backend/src/test/java/com/docgen/service/SegmentVersionServiceTest.java`
- `docs/audits/full-project-review-2026-04-26/05-traceability-matrix.md`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`
Validation commands:
- `mvn -Dtest=SegmentVersionServiceTest test` (from `backend/`)
Validation result: BUILD SUCCESS (7 tests).  
Notes: full `mvn test` not run. Next: **WS-04-T07** (segment version API documentation).

## 2026-04-26: WS-04-T07 Completed

Task ID: WS-04-T07  
Summary: Expanded **[segment-version-api.md](../../segment-version-api.md)** with English API reference: `SegmentVersionDTO` table; compare query params including **`includeContentDiff`** (default `false`); full **`SegmentVersionDiffResult`**, **`SegmentDiffEntry`**, and **`ContentDiffLine`** field tables; **identical-text** behavior (WS-04-T03); same-version shortcut; **truncation** and **graceful degradation** semantics for content diff; **publish** **`409`** / **`SEGMENT_VERSION_PUBLISH_CONFLICT`** (WS-04-T06). Updated traceability **`DIFF-TEST-001`** documentation column.  
Files changed:
- `docs/segment-version-api.md`
- `docs/audits/full-project-review-2026-04-26/05-traceability-matrix.md`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`
Validation commands:
- PowerShell ASCII-only check on `docs/segment-version-api.md` (replaced typographic punctuation); task-card `rg "[\\p{Han}]"` not run on full audit tree.
Validation result: documentation-only; no `mvn test`.  
Notes: Next: **WS-04-T08** (SegmentVersionDialog tests).

## 2026-04-26: WS-04-T08 and WS-04-T09 Completed

Task IDs: WS-04-T08, WS-04-T09  
Summary: Added **`frontend/src/__tests__/views/SegmentVersionDialog.test.ts`** (Vitest + Element Plus global setup): compare calls **`compareSegmentVersions(..., true)`**; **`contentChanged=false`** shows no-difference / no-content-change UI; **`truncated=true`** shows warning alert; ADDED/REMOVED/MODIFIED lines render; collapsed EQUAL group expands and reveals more rows. Fixed **`expandGroup`** to replace the **`Set`** immutably so computed **`processedDiffLines`** refreshes (WS-04-T09). Added **`{ immediate: true }`** on the dialog-open watch so **`getSegmentVersions`** runs when the dialog is initially open.  
Files changed:
- `frontend/src/views/template-workspace/components/SegmentVersionDialog.vue`
- `frontend/src/__tests__/views/SegmentVersionDialog.test.ts`
- `docs/audits/full-project-review-2026-04-26/05-traceability-matrix.md`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`
Validation commands:
- `npx vitest run src/__tests__/views/SegmentVersionDialog.test.ts` (from `frontend/`)
Validation result: passed (5 tests).  
Notes: full `npm test` not run. Next workstreams follow **`11-execution-sequence.md`** (e.g. remaining WS-05/06/07/08 task cards).

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

Summary: Completed **WS-05-T04** (no production code). Decided and documented normative contract: synchronous `POST /api/generate/{templateId}?version=n` — `version` is **supported for validation and policy only**; **historical `template_versions` file paths are not used** for render output; generation **always** uses current `templates.template_file_path`. Async/batch have no `version` parameter. Added canonical English spec `docs/versioned-template-generation-contract.md`; linked from audit `18-generate-api-version-parameter-behavior.md` and `README.md`; traceability `REQ-GEN-VERSION-CONTRACT-001`.  
Validation: `rg "[\\p{Han}]" docs/versioned-template-generation-contract.md` — no matches (ASCII / English only in new file).

## 2026-04-26: WS-05-T05 Version parameter plumbing (contract-preserving)

Summary: Implemented **WS-05-T05** per WS-05-T04 (historical **file** rendering still **not** supported). Added `DocumentGeneratorService.generateDocument(templateId, request, syncValidatedTemplateVersion)` with two-arg delegating to `null` third parameter; `DynamicApiService` passes validated `version` (or `null`); async, batch, and scheduled paths call `generateDocument(..., null)` explicitly so mocks match runtime. Logging when third argument non-null. Tests: extended `DynamicApiServiceVersionParameterTest`, new `DocumentGeneratorServiceSyncVersionRenderPathTest`, updated `ScheduledTaskServiceTest` and `BatchGenerationCompletenessPropertyTest`. Updated `docs/versioned-template-generation-contract.md` implementation section.  
Validation: `mvn -q test "-Dtest=DynamicApiServiceVersionParameterTest,DocumentGeneratorServiceSyncVersionRenderPathTest,DocumentGeneratorServiceGenerationEligibilityTest,ScheduledTaskServiceTest,BatchGenerationCompletenessPropertyTest,TemplateTestServiceTest"` (from `backend/`) — BUILD SUCCESS.

## 2026-04-26: WS-05-T06 Composite activation via state machine

Summary: Completed **WS-05-T06**. `CompositeTemplateService.activateCompositeTemplate` no longer sets `ACTIVE` directly; after existing assembly validation it calls `TemplateStateMachineService.transition(templateId, ACTIVE)` (with idempotent return when already `ACTIVE`). Injected `TemplateStateMachineService` into `CompositeTemplateService`. Added `CompositeTemplateServiceActivateTest` (draft/reviewed paths, already-active skip, review-required error propagation, empty segments). `TemplateStateMachineService` Javadoc cross-link.  
Validation: `mvn -q test "-Dtest=CompositeTemplateServiceActivateTest"` and `mvn -q test-compile` (from `backend/`) — BUILD SUCCESS.

## 2026-04-26: WS-06-T01 Workspace route reuse characterization

Summary: Completed **WS-06-T01**. `TemplateWorkspaceIndex.test.ts` uses a hoisted `mockRouteState` for `useRoute()` (later made `reactive` in WS-06-T02 so the route watcher fires under Vitest). T01 originally characterized mount-only init; **WS-06-T02** implemented reload on `params.id` change and replaced the assertion with a re-init expectation.  
Validation (after T02): `npx vitest run src/__tests__/views/TemplateWorkspaceIndex.test.ts` (from `frontend/`) — 5 tests passed.

## 2026-04-26: WS-06-T02 Workspace reload on route change

Summary: Completed **WS-06-T02**. `Index.vue` watches `route.params.id`; on change (finite positive id) resets stage UI to design and calls `store.initWorkspace(id)`. Extracted `workspaceIdFromRoute()` for mount, retry, and consistent parsing. Tests: WS-06-T01 characterization block replaced with WS-06-T02 assertion that `initWorkspace` is invoked with the new id without remount.  
Validation: `npx vitest run src/__tests__/views/TemplateWorkspaceIndex.test.ts` (from `frontend/`) — 5 tests passed.

## 2026-04-26: WS-06-T03 OnlyOfficeEditor document prop characterization

Summary: Completed **WS-06-T03**. Added `OnlyOfficeEditor.test.ts`: pre-seeds a script tag + mocked `window.DocsAPI.DocEditor` (constructable spy) so `loadScript()` resolves without network; mocks `signOnlyOfficeConfig`. Characterization proves that after mount, changing `documentUrl` and `documentKey` does **not** call `DocEditor` again (no prop watchers; contrast with `watch(locale)`). Traceability `FRONT-OO-001` updated to In Progress with test pointer.  
Validation: `npx vitest run src/__tests__/components/OnlyOfficeEditor.test.ts` (from `frontend/`) — 1 test passed. Full `npm test` not re-run (known unrelated failures in this environment).

## 2026-04-26: WS-06-T04 OnlyOfficeEditor reinitialize on document change

Summary: Completed **WS-06-T04**. `OnlyOfficeEditor.vue` watches `[documentUrl, documentKey]` and calls `createEditor()` when `scriptLoaded` (same path as locale reload; `createEditor` still calls `destroyEditor()` first). `onMounted` now `await createEditor()` so the first construction finishes before later prop updates are handled, reducing overlap risk. Tests cover mount, combined url/key change, url-only, and key-only; assert prior instance `destroyEditor` before second `DocEditor` construction. Signing path unchanged (`signOnlyOfficeConfig` per recreation). Traceability `FRONT-OO-001` → Verified.  
Validation: `npx vitest run src/__tests__/components/OnlyOfficeEditor.test.ts` (from `frontend/`) — 4 tests passed.

## 2026-04-26: WS-06-T05 DesignStage debug logging removal

Summary: Completed **WS-06-T05**. Removed two `console.log` calls and one `console.warn` from `insertToEditor` in `DesignStage.vue` (they logged tab id, type, payload, and ref map keys — avoid leaking editor/template-related data). Missing editor ref still returns early with no user-visible change. No debug-flag convention referenced those logs.  
Removed: `[DesignStage] insertToEditor called: …`, `[DesignStage] No editor ref found for tab: …`, `[DesignStage] Editor ref found, type: …`.  
Validation: `npx vitest run src/__tests__/views/TemplateWorkspaceIndex.test.ts` (from `frontend/`) — 5 tests passed. Full `npm test` not re-run.

## 2026-04-26: WS-06-T06 VersionDiffPanel non-text diff display

Summary: Completed **WS-06-T06**. The details `el-table` used `v-if="diffResult.textDiffs.length > 0"` while `:data="allDiffs"` already merged text, variable, data-source, and expression entries — so variable-only (or other non-text-only) responses hid the table. Condition changed to **`v-if="allDiffs.length > 0"`**. Added Vitest case with empty `textDiffs` and non-empty `variableDiffs`. Traceability `FRONT-DIFF-001` → Verified.  
Validation: `npx vitest run src/__tests__/VersionDiffPanel.test.ts` (from `frontend/`) — 5 tests passed.

## 2026-04-26: WS-06-T07 Frontend i18n for reviewed components

Summary: Completed **WS-06-T07**. Replaced user-facing hardcoded strings with `t()` / `i18n.global.t()`: `OnlyOfficeEditor.vue` (clipboard/prompt, API/load/init errors); `VersionDiffPanel.vue` (table column labels, empty cells); `SegmentVersionDialog.vue` (fixed invalid `t(key, default)` for same-version warning; empty placeholders; content-diff “more lines” line). `request.ts` uses `i18n.global.t('message.forbidden'|'message.tooManyRequests')` for 403/429. Added keys in `en-US.json`, `zh-CN.json`, `zh-TW.json` (`common.emptyValue`, `message.tooManyRequests`, `workspace.settings.versionDiff*`, `workspace.editor.insertText*`, `apiNotLoaded`, `initFailed`, `loadScriptFailed`, `workspace.segment.sameVersionWarning`, `workspace.segment.contentDiff.moreLinesRemaining`).  
Validation: `npx vitest run src/__tests__/components/OnlyOfficeEditor.test.ts src/__tests__/VersionDiffPanel.test.ts src/__tests__/views/TemplateWorkspaceIndex.test.ts` (from `frontend/`) — 14 tests passed. Full `npm test` not re-run.

## 2026-04-26: WS-07-T03 Frontend CI (GitHub Actions)

Summary: Completed **WS-07-T03**. Added `.github/workflows/frontend-ci.yml`: **Node 20**, `npm ci` in `frontend/` (uses `package-lock.json`), then **`npm run type-check`**, **`npm test`**, **`npm run build`**. Path filters on `frontend/**` and workflow file; concurrency + cancel-in-progress. Fixed **`vue-tsc`** failure from `require('vue')` in `TemplateWorkspaceIndex.test.ts` by moving route mock state to **`src/__tests__/helpers/templateWorkspaceRouteMock.ts`** and using an **async** `vi.mock('vue-router', …)` factory.  
Validation (from `frontend/`): `npm run type-check` — pass; `npx vitest run src/__tests__/views/TemplateWorkspaceIndex.test.ts` — pass; `npm run build` — pass. Full `npm ci` not re-run (existing `node_modules`); full `npm test` may still surface unrelated flaky tests in CI.

## 2026-04-26: WS-07-T02 Backend CI (GitHub Actions)

Summary: Completed **WS-07-T02**. Added `.github/workflows/backend-ci.yml`: **GitHub Actions** on `push` / `pull_request` / `workflow_dispatch` when `backend/**` or the workflow file changes; **Temurin JDK 17** (matches `pom.xml`); **Maven cache** via `actions/setup-java`; **Redis 7.2** service for `application-test` profile; `mvn -B -ntp test` in `backend/`. Concurrency cancels redundant runs. Traceability `INFRA-CI-001` → In Progress (frontend/docxtemplater stages still TBD).  
Validation: `mvn -B -ntp test -Dtest=DocgenApplicationTests` (from `backend/`) — BUILD SUCCESS in this environment. Full `mvn test` not re-run here (long / environment-specific). Workflow YAML syntax reviewed locally.

## 2026-04-26: WS-05-T01 Generation eligibility characterization (closure)

Task ID: WS-05-T01  
Summary: Closed the task card gap for **synchronous dynamic API** eligibility. `DocumentGeneratorServiceGenerationEligibilityTest`, `AsyncDocumentServiceSubmitEligibilityTest`, and `BatchDocumentServiceSubmitEligibilityTest` already characterized non-`ACTIVE` rejection for core sync/async/batch submit paths (from WS-05-T02 work). Added `DynamicApiServiceGenerationEligibilityTest` so `DynamicApiService.generateViaApi` is explicitly covered: non-`ACTIVE` fails before `TemplateVersionRepository` or `DocumentGeneratorService`; `ACTIVE` with `version == null` delegates with a null sync version. No production behavior changes.  
Files changed:
- `backend/src/test/java/com/docgen/service/DynamicApiServiceGenerationEligibilityTest.java`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`
Validation commands:
- `mvn -q test "-Dtest=DynamicApiServiceGenerationEligibilityTest,DocumentGeneratorServiceGenerationEligibilityTest,AsyncDocumentServiceSubmitEligibilityTest,BatchDocumentServiceSubmitEligibilityTest"` (from `backend/`)
Validation result: BUILD SUCCESS.

## 2026-04-26: WS-02-T06 Composite coverage variable scan contract

Task ID: WS-02-T06  
Summary: Documented the **current mismatch** (`CompositeCoverageService` posts `{ templatePath }` to `/evaluate` expecting `variables`, while Node `/evaluate` is expression-only) and adopted a **normative target**: dedicated **`POST /scan-variables`** returning `{ variables: string[] }` (lexicographically sorted unique tag roots), same Docxtemplater stack as `/render`, read-only, no client-supplied expressions. Added test requirements for **WS-02-T07**. Updated **C-004** in `14-java-node-contract-mismatch-inventory.md` to point at the new contract.  
Files changed:
- `docs/audits/full-project-review-2026-04-26/20-composite-coverage-variable-scan-contract.md`
- `docs/audits/full-project-review-2026-04-26/14-java-node-contract-mismatch-inventory.md`
- `docs/audits/full-project-review-2026-04-26/README.md`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`
Validation commands:
- `rg "[\\p{Han}]" docs/audits/full-project-review-2026-04-26/20-composite-coverage-variable-scan-contract.md docs/audits/full-project-review-2026-04-26/14-java-node-contract-mismatch-inventory.md docs/audits/full-project-review-2026-04-26/README.md`
Validation result: No Han/CJK matches in `20-composite-coverage-variable-scan-contract.md` (workspace search); README link line is ASCII-only.

## 2026-04-26: WS-02-T07 Implement composite coverage variable scan

Task ID: WS-02-T07  
Summary: Implemented **`POST /scan-variables`** on the Docxtemplater service: loads `.docx` from MinIO with the **same** `parser` + `createImageModule()` stack as `/render` (extracted to `src/docx-templater-config.js`), runs Docxtemplater compile + internal `getTags` postparse walk, returns **lexicographically sorted unique** placeholder paths as `{ variables: string[] }`. Errors: `400` missing path, `404` object not found, `422` template parse errors, `500` otherwise. **`CompositeCoverageService`** now calls `/scan-variables` instead of `/evaluate`. Added Jest integration tests and **`CompositeCoverageServiceScanVariablesTest`** (Mockito). Updated contract doc status and **C-004** inventory narrative.  
Files changed:
- `docxtemplater-service/src/docx-templater-config.js`
- `docxtemplater-service/src/routes/render.js`
- `docxtemplater-service/src/routes/scan-variables.js`
- `docxtemplater-service/server.js`
- `docxtemplater-service/src/__tests__/integration.test.js`
- `backend/src/main/java/com/docgen/service/CompositeCoverageService.java`
- `backend/src/test/java/com/docgen/service/CompositeCoverageServiceScanVariablesTest.java`
- `docs/audits/full-project-review-2026-04-26/20-composite-coverage-variable-scan-contract.md`
- `docs/audits/full-project-review-2026-04-26/14-java-node-contract-mismatch-inventory.md`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`
Validation commands:
- `npm test -- --testPathPattern=integration.test` (from `docxtemplater-service/`)
- `mvn -q test "-Dtest=CompositeCoverageServiceScanVariablesTest"` (from `backend/`)
Validation result: BUILD SUCCESS / Jest PASS for scoped commands. Full `mvn test` not run (task card lists full suite; time/environment).

## 2026-04-26: WS-07-T04 Docxtemplater CI (GitHub Actions)

Task ID: WS-07-T04  
Summary: **`.github/workflows/docxtemplater-ci.yml`** already present: **Node 20**, `npm ci`, **`npm test`** (Jest) under `docxtemplater-service/`; path filters + concurrency. LibreOffice not required (PDF tests skip when absent). **Follow-up fix:** `sandbox.js` `evaluateWithIsolatedVm` now injects **`typeof === 'function'`** builtins with **`ivm.Reference`** (not `ExternalCopy`) and **`Formula`** as **`Reference`**, fixing `TypeError: … could not be cloned` for `parseInt` / Formula.js when `isolated-vm` is installed — **Jest integration tests pass** with full optional deps.  
Files changed:
- `docxtemplater-service/src/sandbox.js`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`
Validation commands:
- `npm test` (from `docxtemplater-service/`) — **84 tests passed** (after sandbox fix).
Validation result: Local Jest green; GitHub Actions to confirm on push.

## 2026-04-26: WS-07-T05 Database migration runbook (V30 / V36 / V39)

Task ID: WS-07-T05  
Summary: Added **[21-database-migration-runbook-v30-v36-v39.md](21-database-migration-runbook-v30-v36-v39.md)** covering **greenfield** Flyway order, **upgrade** risks (V36 drops segment-library tables including V30 `segment_versions`; V39 creates a new template-scoped `segment_versions`), **mandatory backups before V36** when historical segment data matters, **rollback limits** (restore / forward-fix only), and **open decisions** (no automatic retention of old segment version rows across V36). Updated **`README.md`** index and **`05-traceability-matrix.md`** `MIGRATION-SEG-001` documentation column to point at the runbook (`Open` → `In Progress` while migration tests remain TBD). No Flyway SQL changes.  
Files changed:
- `docs/audits/full-project-review-2026-04-26/21-database-migration-runbook-v30-v36-v39.md`
- `docs/audits/full-project-review-2026-04-26/README.md`
- `docs/audits/full-project-review-2026-04-26/05-traceability-matrix.md`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`
Validation commands:
- Workspace search for CJK in `21-database-migration-runbook-v30-v36-v39.md` only — no matches.
Validation result: Documentation-only; English-only in new runbook.

## 2026-04-26: WS-07-T06 Release runbook

Task ID: WS-07-T06  
Summary: Added **[22-release-runbook.md](22-release-runbook.md)** describing **pre-release** checks (CI, migrations, secrets), **build/test parity** with `.github/workflows/*` and `06-validation-commands.md`, **backup** expectations (PostgreSQL, object storage, Redis, artifacts), **generic deploy ordering**, **post-release smoke** (actuator, Docxtemplater `/health`, frontend, critical path), and **rollback limits** (app revert vs **DB restore or forward migration** — cross-links [21-database-migration-runbook-v30-v36-v39.md](21-database-migration-runbook-v30-v36-v39.md)). No Dockerfiles, CI YAML, or application code changed. **`README.md`** index, **`05-traceability-matrix.md`** new row **`REL-RELEASE-001`**, **`07-iteration-log.md`**.  
Files changed:
- `docs/audits/full-project-review-2026-04-26/22-release-runbook.md`
- `docs/audits/full-project-review-2026-04-26/README.md`
- `docs/audits/full-project-review-2026-04-26/05-traceability-matrix.md`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`
Validation commands:
- CJK scan on `22-release-runbook.md` — no matches.
Validation result: Documentation-only.

## 2026-04-26: WS-08-T03 Frontend Docker reproducibility

Task ID: WS-08-T03  
Summary: **`frontend/Dockerfile`**: build stage uses **`node:20-alpine`** (aligned with frontend CI), copies **`package.json` + `package-lock.json`**, runs **`npm ci`**, then **`npm run build`** (`vue-tsc` + `vite build` per `package.json` — type check no longer skipped in the image). Preserved **`VITE_ONLYOFFICE_URL`** / **`VITE_BACKEND_INTERNAL_URL`** args and nginx stage. Comments translated to **English**. **`Dockerfile.local`**: header comment English-only (still host `dist/` only).  
Files changed:
- `frontend/Dockerfile`
- `frontend/Dockerfile.local`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`
Validation commands:
- `docker build -f frontend/Dockerfile -t docgen-frontend:test frontend` (from repo root)
Validation result: **Not completed** — Docker Hub pull timed out in this environment (network). Dockerfile reviewed for lockfile presence and `npm ci` / `npm run build` consistency with `package-lock.json`.

## 2026-04-26: WS-08-T03 validation follow-up (Compose build frontend)

Task ID: WS-08-T03 (validation only)  
Summary: **`docker compose build frontend`** succeeded on 2026-04-26 after WS-08-T04 nginx stage fixes (same build path as Compose: **`Dockerfile.local`**). Confirms **`dist/`** context and Nginx stage build for local workflow.  
Files changed: none (log only).  
Validation commands: `docker compose build frontend` (repo root).  
Validation result: **Exit code 0**.

## 2026-04-26: DIFF-TEST-001 closure (content diff test evidence)

Task ID: DIFF-TEST-001 (traceability; no single WS task card)  
Summary: Ran the **scoped backend** suite for docx content diff (`ContentDiffServiceTest`, `DocxTextExtractorTest`, `SegmentVersionServiceTest`) and the **frontend** `SegmentVersionDialog` Vitest file. Marked **`05-traceability-matrix.md`** row **`DIFF-TEST-001`** as **Verified** with concrete command evidence.  
Files changed:
- `docs/audits/full-project-review-2026-04-26/05-traceability-matrix.md`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`  
Validation commands:
- `mvn "-Dtest=ContentDiffServiceTest,DocxTextExtractorTest,SegmentVersionServiceTest" test` (from `backend/`) — **BUILD SUCCESS**.
- `npx vitest run src/__tests__/views/SegmentVersionDialog.test.ts` (from `frontend/`) — **5 tests passed**.  
Not run: full `mvn test` / full frontend suite (out of scope for this closure pass).  
Remaining risks: integration tests with real MinIO + multi-tenant flows not re-run here.

## 2026-04-26: WS-08-T04 Non-root Docker runtime users

Task ID: WS-08-T04  
Summary: **Backend** images run as **`app` (UID 10001)** with owned `app.jar`. **Frontend** images run as **`nginx`** with **`cap_net_bind_service`** on the nginx binary so **listen 80** works without changing Compose `ports`; build-time **`libcap`** removed after `setcap`; **`/etc/nginx/templates`** created when missing on base image. **Docxtemplater** runs as **`node`** with **`chown -R node:node /app`** and **`HOME=/app`**. Audit **[25-docker-non-root-runtime-ws-08-t04.md](25-docker-non-root-runtime-ws-08-t04.md)**; **`README.md`** index; **`05-traceability-matrix.md`** `INFRA-DOCKER-NONROOT-001` evidence column.  
Files changed:
- `backend/Dockerfile`
- `backend/Dockerfile.local`
- `frontend/Dockerfile`
- `frontend/Dockerfile.local`
- `docxtemplater-service/Dockerfile`
- `docs/audits/full-project-review-2026-04-26/25-docker-non-root-runtime-ws-08-t04.md`
- `docs/audits/full-project-review-2026-04-26/README.md`
- `docs/audits/full-project-review-2026-04-26/05-traceability-matrix.md`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`  
Validation commands:
- `docker compose build` (repo root) — **exit code 0**.
- `docker run --rm --entrypoint id docgen-frontend:latest` → **uid=101(nginx)**.
- `docker run --rm --entrypoint id docgen-app:latest` → **uid=10001(app)**.
- `docker run --rm --entrypoint id docgen-docxtemplater:latest` → **uid=1000(node)**.  
Stop conditions: none (LibreOffice and Nginx paths validated at image build; full document conversion smoke not in task scope).  
Remaining risks: policies that forbid **file capabilities** on nginx; rare LibreOffice edge cases under `node` may need extra writable paths.

## 2026-04-26: WS-08-T01 Pin floating Docker image tags

Task ID: WS-08-T01  
Summary: **`docker-compose.yml`** already used pinned **MinIO** (`RELEASE.2025-04-08T15-41-24Z`), **Redis** (`7.2.7-alpine`), **PostgreSQL** (`16.5`), and **Euro-Office Document Server** (image **digest** on GHCR). Completed the audit deliverable **[23-docker-image-pinning-ws-08-t01.md](23-docker-image-pinning-ws-08-t01.md)** (registry vs local images, upgrade notes, verification expectations). Translated **Compose service comments** to **English**; aligned inline doc pointers with the new file. **`README.md`** index, **`05-traceability-matrix.md`** row **`INFRA-COMPOSE-PIN-001`**.  
Files changed:
- `docker-compose.yml`
- `docs/audits/full-project-review-2026-04-26/23-docker-image-pinning-ws-08-t01.md`
- `docs/audits/full-project-review-2026-04-26/README.md`
- `docs/audits/full-project-review-2026-04-26/05-traceability-matrix.md`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`
Validation commands:
- `docker compose config` (from repo root)
Validation result: **Exit code 0** — merged Compose config valid.  
Not run: `docker compose pull` / image digest re-verification via pull (optional; previously unreliable on this network).  
Known limitations: locally built images still use the `:latest` **label** for compose-built services (documented as non-upstream); Kubernetes manifests under `k8s/` were out of scope for this task card.

## 2026-04-26: WS-08-T02 Compose resource limits

Task ID: WS-08-T02  
Summary: Added **`deploy.resources`** memory **limits** and **reservations** to all services in root **`docker-compose.yml`** (conservative values: frontend 512M, app 2G, docxtemplater 3G, onlyoffice 4G, postgres 1G, redis 512M, minio 1G). **CPU caps omitted** for laptop-friendly JVM/LO behavior. Documented in **[24-compose-resource-limits-ws-08-t02.md](24-compose-resource-limits-ws-08-t02.md)**; **`README.md`** index; **`05-traceability-matrix.md`** row **`INFRA-COMPOSE-LIMITS-001`**. Volumes unchanged.  
Files changed:
- `docker-compose.yml`
- `docs/audits/full-project-review-2026-04-26/24-compose-resource-limits-ws-08-t02.md`
- `docs/audits/full-project-review-2026-04-26/README.md`
- `docs/audits/full-project-review-2026-04-26/05-traceability-matrix.md`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`
Validation commands:
- `docker compose config` (from repo root) — exit code 0.
Validation result: Compose merge valid.

## 2026-04-26: WS-08-T04 Non-root runtime users in Dockerfiles

Task ID: WS-08-T04  
Summary: **Backend** production and local images: Alpine user **`app`** (uid **10001**), `chown` on the fat JAR, **`USER app`**. **Frontend** production image: **`nginx:1.25-alpine`** with **`cap_net_bind_service`** on `/usr/sbin/nginx`, writable cache/log/template paths **`chown`’d to `nginx`**, **`USER nginx`** (port **80** unchanged for Compose/K8s). **Docxtemplater**: **`chown -R node:node /app`**, **`USER node`** (official `node:18-slim` user); removed redundant **`HOME=/app`** so the default **`/home/node`** applies. **`backend/Dockerfile.local`** header comments converted to **English**.  
Files changed:
- `backend/Dockerfile` (non-root `app` uid 10001; already aligned)
- `backend/Dockerfile.local` (English header comments; non-root `app`)
- `frontend/Dockerfile` (`mkdir -p /etc/nginx/templates` before `chown`; `apk del libcap` after `setcap`; non-root `nginx`)
- `frontend/Dockerfile.local` (same Nginx hardening for Compose `Dockerfile.local`; `mkdir` + `apk del libcap`)
- `docxtemplater-service/Dockerfile` (non-root `node`; drop redundant `HOME=/app`)
- `docs/audits/full-project-review-2026-04-26/05-traceability-matrix.md`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`
Validation commands:
- `docker compose build` (from repo root)
Validation result: **Exit code 0** — `docgen-app`, `docgen-frontend`, and `docgen-docxtemplater` images built successfully.  
Stop conditions: none (Nginx non-root uses `cap_net_bind_service` on `/usr/sbin/nginx`; Docxtemplater runs as `node` with `/app` owned by `node`).

## 2026-04-26: Batch 9 traceability refresh (post WS-08)

Workstream: governance (execution sequence Batch 9)  
Summary: After **Batch 8** (`WS-08-T01`–`T04`) completion, aligned **`05-traceability-matrix.md`** high-priority rows with implemented work already recorded in **`07-iteration-log.md`**: **`SEC-OO-001`** and **`SEC-SSRF-001`** → **Verified** (WS-01 OnlyOffice JWT + `OutboundUrlPolicy` + `CallbackDocumentDownloadHelper` + tests); **`CONTRACT-NODE-001`**, **`CONTRACT-NODE-002`**, **`CONTRACT-EXPR-001`** → **Verified** (WS-02-T02–T05 + listed Java/Jest tests and contract inventory link). No production code changes.  
Files changed:
- `docs/audits/full-project-review-2026-04-26/05-traceability-matrix.md`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`
Validation commands: none required (documentation-only).  
Remaining risks: **`SEC-OO-001`** optional CIDR allowlist still depends on accurate **`RemoteAddr`** / forwarded-header configuration at the edge; full **`mvn test`** may fail in environments without Docker/H2 per prior logs — scoped tests cited in the matrix remain the evidence baseline.

## 2026-04-26: MIGRATION-SEG-001 greenfield schema integration test

Task ID: MIGRATION-SEG-001 (partial — greenfield only)  
Summary: **`SegmentVersionsFlywaySchemaIT`** on a **fresh** PostgreSQL (Testcontainers + Spring `integration` profile + Flyway) asserts **`segment_versions`** column set, **`uq_segment_versions_template_name_version`**, and expected **indexes** match **`V39__create_segment_versions_inline.sql`** / **`SegmentVersion`** entity. **`segmentLibraryTablesDroppedByV36AreAbsent`** asserts all **V36** `DROP TABLE` segment-library targets are gone on a greenfield chain (**`segments`**, **`segment_tag_mappings`**, **`segment_reviews`**, **`segment_favorites`**, **`segment_test_data`**). **`BaseIntegrationTest`**: **`@Testcontainers(disabledWithoutDocker = true)`** so environments without a working Docker API **skip** integration tests instead of erroring; **MinIO** test image tag aligned to **`minio/minio:RELEASE.2025-04-08T15-41-24Z`** (same pin as root Compose). **[21-database-migration-runbook-v30-v36-v39.md](21-database-migration-runbook-v30-v36-v39.md)** §3 documents **`SegmentVersionsFlywaySchemaIT`** + **`MigrationPropertyTest`** and remaining **upgrade-path TBD**. **`05-traceability-matrix.md`** `MIGRATION-SEG-001` test column updated; status stays **In Progress** until an automated **upgrade-from-V35-representative** path exists.  
Files changed:
- `backend/src/test/java/com/docgen/integration/SegmentVersionsFlywaySchemaIT.java`
- `backend/src/test/java/com/docgen/integration/BaseIntegrationTest.java`
- `docs/audits/full-project-review-2026-04-26/21-database-migration-runbook-v30-v36-v39.md`
- `docs/audits/full-project-review-2026-04-26/05-traceability-matrix.md`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`
Validation commands:
- `mvn "-Dtest=SegmentVersionsFlywaySchemaIT" test` (from `backend/`)
Validation result: **BUILD SUCCESS** with Docker unavailable — Testcontainers **skipped** the class (`disabledWithoutDocker = true`). With Docker: run the same command to execute assertions.  
Remaining risks: **V36 → V39** upgrades with existing data are still **manual / runbook-driven**; this test does not simulate pre-V39 schemas.

## 2026-04-26: MIGRATION-SEG-001 greenfield IT — V36 dropped tables completeness

Task ID: MIGRATION-SEG-001 (partial — test + runbook alignment)  
Summary: Extended **`SegmentVersionsFlywaySchemaIT.segmentLibraryTablesDroppedByV36AreAbsent`** to assert **`segment_favorites`** and **`segment_test_data`** are absent after Flyway on a greenfield DB (both are **`DROP TABLE`** targets in **`V36__migrate_assembly_config_and_drop_segments.sql`** alongside `segments`, `segment_tag_mappings`, `segment_reviews`). Updated **[21-database-migration-runbook-v30-v36-v39.md](21-database-migration-runbook-v30-v36-v39.md)** follow-up item #3 and **`05-traceability-matrix.md`** `MIGRATION-SEG-001` test column for consistency.  
Files changed:
- `backend/src/test/java/com/docgen/integration/SegmentVersionsFlywaySchemaIT.java`
- `docs/audits/full-project-review-2026-04-26/21-database-migration-runbook-v30-v36-v39.md`
- `docs/audits/full-project-review-2026-04-26/05-traceability-matrix.md`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`
Validation commands:
- `mvn "-Dtest=SegmentVersionsFlywaySchemaIT" test` (from `backend/`)
Validation result: **BUILD SUCCESS** (Testcontainers **skipped** when Docker unavailable; with Docker, both IT methods execute against PostgreSQL).

## 2026-04-26: IMPORT-ZIP-001 traceability closure

Task ID: IMPORT-ZIP-001 (documentation / matrix only)  
Summary: **`05-traceability-matrix.md`**: **`IMPORT-ZIP-001`** documentation column was **TBD**; filled with **`07-iteration-log.md` (WS-03-T01–T03)** and **`application.yml`** `composite-import.zip` property keys. Test column now includes explicit **`mvn "-Dtest=CompositeImportExportServiceTest" test`**. Design column aligned to **WS-03-T01–T03**. Status **Implemented** → **Verified** (limits, ratio, entry count, path allowlist, MinIO object name sanitization — evidence in `CompositeImportExportServiceTest`).  
Files changed:
- `docs/audits/full-project-review-2026-04-26/05-traceability-matrix.md`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`
Validation commands:
- `mvn "-Dtest=CompositeImportExportServiceTest" test` (from `backend/`)
Validation result: **BUILD SUCCESS** (2026-04-26).

## 2026-04-26: MIGRATION-SEG-001 upgrade-path IT + SEC-OO-001 callback CIDR filter

Task IDs: **MIGRATION-SEG-001** (Verified); **SEC-OO-001** (network allowlist evidence)  
Summary: Added **`SegmentVersionsFlywayUpgradeIT`**: `DROP SCHEMA public` reset per test; Flyway **target V35**; minimal **`tenants` / `teams` / `users` / `segments` / `templates` / `segment_versions`** seed; Flyway to **latest**; asserts **V36** `assembly_config` inline rewrite (valid + missing `segmentId`) and absence of segment-library tables; asserts **V39** `segment_versions` empty after upgrade. Added **`OnlyOfficeCallbackProperties`** + **`OnlyOfficeCallbackIpFilter`** (optional **`onlyoffice.callback.allowed-source-cidrs`** vs `getRemoteAddr()`, POST-only, main + composite callback paths) registered **before** **`RateLimitFilter`** in **`SecurityConfig`**. **`OnlyOfficeCallbackIpFilterTest`**, **`SecurityConfigTest`** constructor update, **`application.yml`** default empty list. Amended **[21-database-migration-runbook-v30-v36-v39.md](21-database-migration-runbook-v30-v36-v39.md)** (supersedes prior “defer all upgrade IT” note for this **bounded** fixture). **`05-traceability-matrix.md`**: **`MIGRATION-SEG-001`** → **Verified**; **`SEC-OO-001`** implementation/tests columns mention CIDR filter.  
Files changed:
- `backend/src/test/java/com/docgen/integration/SegmentVersionsFlywayUpgradeIT.java`
- `backend/src/main/java/com/docgen/config/OnlyOfficeCallbackProperties.java`
- `backend/src/main/java/com/docgen/filter/OnlyOfficeCallbackIpFilter.java`
- `backend/src/main/java/com/docgen/config/SecurityConfig.java`
- `backend/src/test/java/com/docgen/filter/OnlyOfficeCallbackIpFilterTest.java`
- `backend/src/test/java/com/docgen/config/SecurityConfigTest.java`
- `backend/src/main/resources/application.yml`
- `docs/audits/full-project-review-2026-04-26/21-database-migration-runbook-v30-v36-v39.md`
- `docs/audits/full-project-review-2026-04-26/05-traceability-matrix.md`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`  
Validation commands:
- `mvn "-Dtest=OnlyOfficeCallbackIpFilterTest,SecurityConfigTest,SegmentVersionsFlywayUpgradeIT,DocgenApplicationTests" test` (from `backend/`) — **BUILD SUCCESS** (Testcontainers classes **skipped** when Docker unavailable).  
Remaining risks: CIDR filter uses **`RemoteAddr`** only — operators must align reverse proxies / **`server.forward-headers-strategy`** when enabling allowlists; upgrade IT does not cover full production data shapes; historical V30 **`segment_versions`** retention remains a **product / DBA** topic (runbook §1).

## 2026-04-26: Dependency audit snapshot (npm audit)

Task ID: governance / supply-chain visibility (no WS task card)  
Summary: Ran read-only **`npm audit`** in **`docxtemplater-service/`** and **`frontend/`**; captured advisories (Docxtemplater: **critical/high/moderate** including transitive **`xmldom`** / **`docxtemplater-image-module-free`** with no fix in chain; Frontend: **6 moderate** via **`monaco-editor`/`dompurify`**, **`vite`/`esbuild`**, **`follow-redirects`**, **`postcss`**). Added audit note **[26-dependency-audit-snapshot-2026-04-26.md](26-dependency-audit-snapshot-2026-04-26.md)** with reproduction commands and **task-card-sized** follow-ups (no **`npm audit fix --force`** applied). **`README.md`** index. **No** `package.json` / `pom.xml` changes.  
Files changed:
- `docs/audits/full-project-review-2026-04-26/26-dependency-audit-snapshot-2026-04-26.md`
- `docs/audits/full-project-review-2026-04-26/README.md`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`  
Validation commands: `npm audit` (from `docxtemplater-service/` and `frontend/`).  
Validation result: both commands completed with **non-zero exit** (vulnerabilities listed).  
Remaining risks: upgrades require separate review (Docxtemplater image stack; Vue/Vite/Monaco majors).

## 2026-04-26: Validation docs + env example (OnlyOffice CIDR + Flyway IT commands)

Task ID: governance / Batch 9 documentation (no WS task card)  
Summary: Expanded **[06-validation-commands.md](06-validation-commands.md)** Backend section with **scoped Maven** commands for **Flyway migration ITs** and **OnlyOffice / URL-policy** tests, plus Testcontainers skip notes. Added **OnlyOffice callback CIDR** smoke row and **`RemoteAddr` / forwarded-header** reminder to **[22-release-runbook.md](22-release-runbook.md)** §5. Documented optional **`ONLYOFFICE_CALLBACK_ALLOWED_SOURCE_CIDRS`** in **`.env.example`** (English-only block; existing Chinese headings unchanged).  
Files changed:
- `docs/audits/full-project-review-2026-04-26/06-validation-commands.md`
- `docs/audits/full-project-review-2026-04-26/22-release-runbook.md`
- `.env.example`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`  
Validation commands: not required (documentation-only).  
Remaining risks: list-style binding from a single comma env var depends on Spring Boot relaxed binding for `List<String>` — verify in target deployment profile if used.

## 2026-04-26: Local Docker Compose — host builds, frontend non-root pid fix

Task ID: local-deployment-operations (user-requested test stack)  
Summary: Documented operational notes from a full local path: **`mvn clean package -DskipTests`** in **`backend/`**, **`npm ci` + `npm run build`** in **`frontend/`**, then **`docker compose build`** and **`docker compose up -d`**. Docker Hub pulls for **`redis:7.2.7-alpine`** and **`minio/minio:RELEASE.2025-04-08T15-41-24Z`** failed on this host (registry timeout); stack was started after **tagging existing local images** to those names (acceptable only for isolated local smoke — re-pull pinned tags when network allows). **`frontend/Dockerfile.local`**: non-root **`nginx`** could not write **`/var/run/nginx.pid`** — **`sed`** now points **`pid`** to **`/var/cache/nginx/nginx.pid`** (directory already **`chown nginx`**).  
Files changed:
- `frontend/Dockerfile.local`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`  
Validation: **`docker compose config`** OK; **`docker compose up -d`** OK; **`http://localhost:8080/actuator/health/ping`** → 200; **`http://localhost:3000/health`** → 200; **`http://localhost/`** → 200 after pid fix.  
Remaining risks: retagged MinIO/Redis may differ from pinned releases; restore official pulls for reproducible tests.

## 2026-04-27 — reset-all-templates-and-import script (international-bank-fol)

Date: 2026-04-27  
Workstream: Demo tooling  
Summary: Added `templates/international-bank-fol/scripts/reset-all-templates-and-import.ps1` to export a golden composite ZIP (optional), delete all templates via API, import one ZIP, and write `out/last-template-id.txt`. Documented in README and DEMO-RUNBOOK.  
Files changed: `templates/international-bank-fol/scripts/reset-all-templates-and-import.ps1`, `templates/international-bank-fol/README.md`, `templates/international-bank-fol/docs/DEMO-RUNBOOK.md`  
Validation commands: Ran script locally with `-ExportGoldFromTemplateId 31 -Force` (export → delete 4 templates → import → new id=32).  
Validation result: OK.  
Remaining risks: Destructive for entire tenant template list; requires explicit `-Force` or `DELETE-ALL` confirmation.  
Related tracking items: —  
Next step: —  

## 2026-04-27 — international-bank-fol demo docs (segment vs merged DOCX)

Date: 2026-04-27  
Workstream: Demo / documentation (international-bank-fol)  
Summary: Updated `templates/international-bank-fol/docs/DEMO-RUNBOOK.md`, `README.md`, and `DEMO-SHOWCASE-SCRIPT.md` to document that authors edit per-segment DOCX while template tests and composite generation assemble one merged DOCX; clarified Word TOC field behavior and optional `fill-all-segments.ps1` bulk authoring.  
Files changed: `templates/international-bank-fol/docs/DEMO-RUNBOOK.md`, `templates/international-bank-fol/README.md`, `templates/international-bank-fol/docs/DEMO-SHOWCASE-SCRIPT.md`, `templates/international-bank-fol/scripts/fill-all-segments.ps1` (script header only).  
Validation commands: N/A (documentation only).  
Validation result: N/A.  
Remaining risks: Word field refresh (TOC/PAGE) still requires client-side Update Fields when reviewing merged output.  
Related tracking items: —  
Next step: —  

## 2026-04-28 — WP-01: Parameter sidebar no longer submits whole template for review

Date: 2026-04-28  
Workstream: Template workflow (WP-01)  
Summary: Removed the misleading **Publish** control from `ParameterSidebar` that called `submitReview` (whole-template review) while users work in the segment editor context. The sidebar now only saves the assembly draft and shows a short hint that review is started from Test/Approval and segment versions are published from the segment canvas.  
Files changed:
- `frontend/src/views/template-workspace/components/ParameterSidebar.vue`
- `frontend/src/i18n/en-US.json`, `zh-CN.json`, `zh-TW.json`
- `frontend/src/__tests__/components/ParameterSidebar.workflow.test.ts`  
Validation commands: `npx vitest --run src/__tests__/components/ParameterSidebar.workflow.test.ts`; `npm run type-check` (from `frontend/`).  
Validation result: Vitest 4 passed; type-check OK.  
Remaining risks: Users must use Test/Approval (and future submit-to-test flow) for template review; no backend `IN_TEST` state yet.  
Related tracking items: —  
Next step: WP-02+ (team, state machine, unified review API) per workflow plan.  

## 2026-04-28 — WP-02: Team-scoped template ownership and reviewer candidates

Date: 2026-04-28  
Workstream: Template workflow (WP-02)  
Summary: Default `templates.team_id` from creator when omitted; validate `teamId` on create/update against the current tenant. Added `GET /api/templates/{id}/reviewers/candidates` (same-tenant, same-team users, excluding the template author). `TemplateReviewService.submitForReview` now requires a template team and validates each reviewer (same tenant and team, not the author).  
Files changed: backend `UserRepository`, `ReviewerCandidateDTO`, `TemplateService`, `TemplateController`, `TemplateReviewService`; tests and `TemplatePersistencePropertyTest` mocks; frontend `templates.ts` (`getReviewerCandidates`).  
Validation: `mvn -Dtest=TemplateServiceTest,TemplateReviewServiceTest test`; `mvn -Dtest=TemplatePersistencePropertyTest test`; `npm run type-check` (frontend).  
Note: Full `mvn test` may still report unrelated pre-existing failures (e.g. `OnlyOfficeServiceTest`, `AggregationSchemaPropertyTest`).  
Next step: WP-03 (IN_TEST and workflow transitions) and WP-04 (single review submit path, frontend `SubmitReviewDialog` → candidates API).  

## 2026-04-28 — WP-03: Frontend IN_TEST workflow (submit to test, return to design, review from testing)

Date: 2026-04-28  
Workstream: Template workflow (WP-03)  
Summary: Aligned the Vue workspace with the backend `IN_TEST` lifecycle: `TemplateStatus` and API helpers `submitTemplateToTest` / `returnTemplateToDesign`; `useStageAvailability` treats `IN_TEST` like an editable phase (with test as default active stage). Design toolbar adds **Submit to testing**; Test stage adds **Return to design** and requires `IN_TEST` + 100% coverage before **Submit for review**; `submitForReview` no longer chains deprecated `submitReview`. Template list/detail show `IN_TEST` and the detail header action uses **Submit to testing** when `IN_TEST` is an available transition. Added `store.isInTest`, workspace `currentStage` sync from `activeStage`, and extended property tests for `IN_TEST`.  
Files changed: `frontend/src/api/templates.ts`, `composables/useStageAvailability.ts`, `stores/templateWorkspace.ts`, `views/template-workspace/Index.vue`, `components/DesignStage.vue`, `TestStage.vue`, `ApprovalStage.vue`, `views/templates/Index.vue`, `Detail.vue`, `i18n/en-US.json`, `zh-CN.json`, `zh-TW.json`, `__tests__/stageAvailability.property.test.ts`, `__tests__/api/templates-extensions.test.ts`.  
Validation commands: `npm run type-check`; `npx vitest run src/__tests__/stageAvailability.property.test.ts src/__tests__/api/templates-extensions.test.ts` (from `frontend/`).  
Validation result: type-check OK; Vitest 24 passed (files 2).  
Remaining risks: `SubmitReviewDialog` may still use manual reviewer IDs until WP-04 wires `getReviewerCandidates`; legacy `submitReview` API remains for compatibility but is deprecated.  
Next step: WP-04 (unified review entry + candidate picker) per workflow plan.  

## 2026-04-28 — WP-04: SubmitReviewDialog uses team-scoped reviewer candidates API

Date: 2026-04-28  
Workstream: Template workflow (WP-04)  
Summary: Replaced tenant-wide `getUsers` paging in `SubmitReviewDialog` with `GET /api/templates/{id}/reviewers/candidates` (`getReviewerCandidates`): same-tenant, same-team reviewers excluding the template author, aligned with `TemplateReviewService.submitForReview`. Dialog now requires `templateId`; `TestStage` and `ApprovalStage` pass `store.templateId`. Candidates refetch whenever the dialog opens; load failures surface via i18n-backed `ElMessage.error`.  
Files changed: `frontend/src/views/template-workspace/components/SubmitReviewDialog.vue`, `TestStage.vue`, `ApprovalStage.vue`, `frontend/src/__tests__/SubmitReviewDialog.test.ts`, `frontend/src/i18n/en-US.json`, `zh-CN.json`, `zh-TW.json`.  
Validation commands: `npm run type-check`; `npx vitest run src/__tests__/SubmitReviewDialog.test.ts` (from `frontend/`).  
Validation result: type-check OK; Vitest 7 passed.  
Remaining risks: Template detail page still uses manual comma-separated reviewer IDs for `submitForReview`; workspace flow is now candidate-driven. Empty candidate list means no eligible reviewers in team (backend contract).  
Next step: Optional — align template `Detail.vue` admin submit UX with candidates or deep-link to workspace.  

## 2026-04-28 — Template Detail review dialog + PublishStage IN_TEST tag

Date: 2026-04-28  
Workstream: Template workflow (WP-04 follow-up)  
Summary: `Detail.vue` Reviews tab uses shared `SubmitReviewDialog` with `getReviewerCandidates`. **Submit for review** is enabled only when `template.status === 'IN_TEST'` (tooltip `review.submitRequiresInTest`). After submit, `fetchTransitions()` runs. `SubmitReviewDialog` adds optional `isSubmitting` (Detail, Test, Approval). `PublishStage` status tag map includes `IN_TEST`.  
Files changed: `frontend/src/views/templates/Detail.vue`, `SubmitReviewDialog.vue`, `TestStage.vue`, `ApprovalStage.vue`, `PublishStage.vue`, `SubmitReviewDialog.test.ts`, `i18n/en-US.json`, `zh-CN.json`, `zh-TW.json`.  
Validation: `npm run type-check`; `npx vitest run src/__tests__/SubmitReviewDialog.test.ts` — 8 passed.  
Next step: Publish-stage capabilities as a separate scope if required.  

## 2026-04-28 — Publish stage integration links (API + document history)

Date: 2026-04-28  
Workstream: Template workflow (publish capabilities, no new dependencies)  
Summary: When the template is **ACTIVE**, **PublishStage** shows an **Integration** card with navigation to existing routes: **Template API Management** (`TemplateApiManagement`) and **Documents** with `?templateId=` filter. **documents/Index.vue** reads `route.query.templateId` on mount and seeds the filter. Removed rocket emoji from Activate button for consistency.  
Files changed: `frontend/src/views/template-workspace/components/PublishStage.vue`, `frontend/src/views/documents/Index.vue`, `frontend/src/i18n/en-US.json`, `zh-CN.json`, `zh-TW.json`.  
Validation: `npm run type-check` (OK).  
Next step: —  

## 2026-04-28 — Template integrations route (webhooks, schedules, watermark)

Date: 2026-04-28  
Workstream: Template workflow (publish follow-up)  
Summary: Added authenticated route **`/templates/:id/integrations`** (`TemplateIntegrations`) rendering **WebhookPanel**, **ScheduledTaskManagement**, and **WatermarkSecurityConfig** with tabs; **`tab`** query selects the pane and stays in sync when switching tabs. **PublishStage** (ACTIVE) gained a third capability button opening this route with `tab=webhooks`.  
Files changed: `frontend/src/router/index.ts`, `frontend/src/views/templates/Integrations.vue`, `frontend/src/views/template-workspace/components/PublishStage.vue`, `frontend/src/i18n/en-US.json`, `zh-CN.json`, `zh-TW.json`.  
Validation: `npm run type-check` (OK).  

## 2026-04-28 — List + publish: integrations link and generate dialog

Date: 2026-04-28  
Workstream: Template workflow (UX follow-up)  
Summary: **Templates** table: for **ACTIVE** rows, added a link to **`TemplateIntegrations`** (same label as `workspace.publish.openIntegrations`); widened actions column. **Publish** stage: for **ACTIVE** templates, added **Generate document** using existing `GenerateDialog` (sync/async/batch) with `v-if="store.isActive"`.  
Files changed: `frontend/src/views/templates/Index.vue`, `frontend/src/views/template-workspace/components/PublishStage.vue`, `07-iteration-log.md`.  
Validation: `npm run type-check` (OK).  

## 2026-04-28 — Ad-hoc: code comment cleanup / slimming (no task card ID)

Date: 2026-04-28  
Workstream: Ad-hoc hygiene  
Summary: Repository-wide comment slimming: removed decorative `//` / `/* */` / HTML section markers and redundant inline comments in `docxtemplater-service` (including `generate-fol-template.mjs`), Java `backend/src` (decorative section lines, partial controller cleanup), and `frontend/src` (API partition comments, `<!-- ... -->` template labels, `request.ts` interceptor notes). Preserved configuration comments per `docs/development/comment-cleanup-config-allowlist.md`.  
Files changed: new allowlist doc; touched JS/TS/Vue/Java across services (no `application.yml`, k8s, or build config edits).  
Validation commands: `npm test` (`docxtemplater-service/`, exit 0); `mvn test` (`backend/`, 3 pre-existing/ENV-related failures: `OnlyOfficeServiceTest` x2, `AggregationSchemaPropertyTest` x1; `mvn -DskipTests compile` OK); `npm run type-check`, `npx vitest run`, `npm run build` (`frontend/`, all OK).  
Validation result: Docxtemplater tests pass; frontend type-check, 345 tests, and production build pass; full backend `mvn test` not clean in this environment.  
Remaining risks: `mvn test` noise from DB/Docker/encryption fixtures; re-run in CI or with `application-test` profile as documented.  
Next step: None required for this hygiene pass.  

## 2026-04-28 — AI-CODE-T01–T10 series closure (documentation)

Date: 2026-04-28  
Workstream: AI-readable code governance  
Summary: Formal completion report for **`AI-CODE-T01`–`AI-CODE-T10`** per `docs/development/ai-readable-code-task-cards.md`. Mechanical passes (T01–T05), persisted inventories for read-only cards (T06–T08), and implemented approved extractions/inline steps (T09–T10) are recorded in **`docs/development/ai-readable-code-completion-report.md`**. **`docs/development/ai-governance-index.md`** updated so future agents open **new** narrow cards instead of assuming T01–T10 are still in flight.  
Files changed:
- `docs/development/ai-readable-code-completion-report.md` (new)
- `docs/development/ai-governance-index.md`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`  
Validation commands: documentation consistency review only.  
Validation result: N/A.  
Remaining risks: New comment noise may accumulate; schedule periodic T01-style passes via new task cards.  
Related tracking items: —  
Next step: Use **`ai-readable-code-completion-report.md`** as baseline; spin follow-up cards only for approved extractions or inventories.

## 2026-04-28 — REQ-R7 follow-up: REST API for `render_config`

Workstream: WS-03 / template API  
Summary: Added **`PUT /api/templates/{id}/render-config`** and **`DELETE /api/templates/{id}/render-config`** with **`RenderConfigDocument`** body validation via **`RenderConfigValidator`** (same rules as ZIP import). **`TemplateService`** gains **`updateRenderConfig`** / **`clearRenderConfig`** with tenant check and version snapshot. Frontend **`templates.ts`**: **`RenderConfigDocument`** type, **`updateTemplateRenderConfig`**, **`clearTemplateRenderConfig`**, **`TemplateDTO.renderConfig`**. Documentation: **`render-config-json-schema.md`** REST section.  
Files changed: `TemplateController.java`, `TemplateService.java`, `TemplateServiceTest.java`, property/unit tests constructing **`TemplateService`**, `frontend/src/api/templates.ts`, `docs/development/render-config-json-schema.md`, `07-iteration-log.md`.  
Validation: `mvn -q -DskipTests compile`; `mvn "-Dtest=TemplateServiceTest,TemplateVersionServiceTest,TemplateClonePropertyTest,TemplatePersistencePropertyTest,TemplateVersionPropertyTest" test`; `npm run type-check` (frontend).  
Validation result: BUILD SUCCESS / type-check OK.

## 2026-04-28 — REQ-R7-001: Composite ZIP `render-config.json` (render_config column)

Task ID: REQ-R7-001 / WS-03-T05–T06  
Workstream: WS-03  
Summary: Implemented optional **`render-config.json`** in composite template ZIP export and import with **`templates.render_config`** JSONB persistence (**V41**). **`RenderConfigDocument`** schema version 1 supports text/image watermarks; remote image URLs rejected; non-empty **`barcodes`** rejected until supported. **`RenderConfigValidator`** delegates watermark rules to **`WatermarkService`**. **`CompositeGeneratorService`** applies configured watermarks after segment merge. **`CompositeMarketService`** copies **`render_config`** when installing from market; **`TemplateService.cloneTemplate`** copies **`templateType`**, **`assembly_config`**, and **`render_config`**. Documentation: **`docs/development/render-config-json-schema.md`**; traceability **`REQ-R7-001`** → Verified.  
Files changed: `V41__templates_add_render_config.sql`, `Template.java`, `TemplateDTO.java`, `RenderConfigDocument.java`, `RenderConfigValidator.java`, `CompositeImportExportService.java`, `CompositeGeneratorService.java`, `WatermarkService.java`, `CompositeMarketService.java`, `TemplateService.java`, tests, **`05-traceability-matrix.md`**, **`.kiro/specs/.../requirements.md`**.  
Validation commands: `mvn -q -DskipTests compile`; `mvn "-Dtest=CompositeImportExportServiceTest,RenderConfigValidatorTest,CompositeImportExportServiceExportTest,ExportConstraintPropertyTest" test` (from `backend/`).  
Validation result: BUILD SUCCESS.  
Remaining risks: Barcode rendering not implemented; **`cloneTemplate`** behaviour extended—verify downstream assumptions for composite clones.  
Related tracking items: REQ-R7-001  
Next step: Optional API/UI to edit **`render_config`** beyond ZIP import.

## 2026-04-28 — Integrations watermark UI: single-template read-only

Workstream: WS-03 / frontend  
Summary: **`WatermarkSecurityConfig`** wires **`render_config`** to **`PUT/DELETE .../render-config`**; i18n keys added for watermark and security hints. **Single (`SINGLE`) templates** now show an extended info alert and use **`el-form` `disabled`** so watermark controls and save/clear cannot mutate **`render_config`** (composite-only semantics). Handlers guard against **`watermarkEditable === false`**.  
Files changed: `frontend/src/views/templates/components/WatermarkSecurityConfig.vue`, `frontend/src/i18n/en-US.json`, `frontend/src/i18n/zh-CN.json`, `frontend/src/i18n/zh-TW.json`, `07-iteration-log.md`.  
Validation commands: `npm run type-check` (from `frontend/`).  
Validation result: `npm run type-check` OK (exit 0).  
Remaining risks: Operators migrating a row from composite to single retain stored **`render_config`** until cleared elsewhere; UI no longer edits it from Integrations for single type.  
Related tracking items: REQ-R7 / Integrations UX  
Next step: Backend `PUT` guard for single templates — see following entry.

## 2026-04-28 — `PUT /render-config` composite-only (API alignment)

Workstream: WS-03 / backend  
Summary: **`TemplateService.updateRenderConfig`** rejects templates whose **`templateType`** is null, blank, or **`SINGLE`** with **`400`** and message **`render-config updates apply to composite templates only`**. **`clearRenderConfig`** (DELETE) unchanged so stale **`render_config`** on single templates can still be cleared. **`TemplateServiceTest`**: composite success path; new **`updateRenderConfig_singleTemplate_throws`**. **`render-config-json-schema.md`** REST table updated. Frontend: **`WatermarkSecurityConfig`** moves action buttons outside disabled **`el-form`** so **Clear** works on single templates; **Save** stays disabled; i18n description mentions clear.  
Files changed: `TemplateService.java`, `TemplateServiceTest.java`, `TemplateController.java` (javadoc), `docs/development/render-config-json-schema.md`, `WatermarkSecurityConfig.vue`, `frontend/src/i18n/en-US.json`, `frontend/src/i18n/zh-CN.json`, `frontend/src/i18n/zh-TW.json`, `07-iteration-log.md`.  
Validation commands: `mvn -q "-Dtest=TemplateServiceTest" test` (from `backend/`); `npm run type-check` (from `frontend/`).  
Validation result: TemplateServiceTest OK (exit 0); type-check OK (exit 0).  
Related tracking items: REQ-R7 / Integrations UX  

## 2026-04-28 — Integration tests: `PUT /render-config` composite rule

Workstream: WS-03 / backend tests  
Summary: **`TemplateCrudIntegrationTest`** adds **`putRenderConfig_singleTemplate_returnsBadRequest`** (expects **`400`** and **`error.message`** mentioning composite) and **`putRenderConfig_compositeTemplate_returnsOk`** (template with **`templateType` = `COMPOSITE`**, **`200`**, **`renderConfig`** contains watermark text). **`createTestTemplate`** overload accepts **`templateType`**.  
Files changed: `TemplateCrudIntegrationTest.java`, `07-iteration-log.md`.  
Validation commands: `mvn -q "-Dtest=TemplateCrudIntegrationTest#putRenderConfig_singleTemplate_returnsBadRequest,TemplateCrudIntegrationTest#putRenderConfig_compositeTemplate_returnsOk" test` (requires Docker for Testcontainers).  
Validation result: Skipped locally when Docker/Testcontainers is unavailable; **`TemplateServiceTest`** remains the primary CI signal without containers.  
Related tracking items: REQ-R7  

## 2026-04-28 — REQ-R7 tests: DELETE clear + frontend render-config API

Workstream: WS-03 / tests  
Summary: **`TemplateCrudIntegrationTest`** adds **`deleteRenderConfig_singleTemplate_withStoredConfig_returnsOk`** (Order 10): **SINGLE** template with persisted **`render_config`**, **`DELETE /api/templates/{id}/render-config`**, **`200`**, DB **`render_config`** null. **`templates-extensions.test.ts`**: **`mockPut`** / **`mockDelete`** on **`request`** mock; **`updateTemplateRenderConfig`** and **`clearTemplateRenderConfig`** contract tests.  
Files changed: `TemplateCrudIntegrationTest.java`, `frontend/src/__tests__/api/templates-extensions.test.ts`, `07-iteration-log.md`.  
Validation commands: `npx vitest run src/__tests__/api/templates-extensions.test.ts` (from `frontend/`); integration method same Testcontainers constraints as prior entry.  
Validation result: Vitest file OK (16 tests, exit 0).  
Related tracking items: REQ-R7  

## 2026-04-28 — REQ-R7: Vitest for WatermarkSecurityConfig

Workstream: WS-03 / frontend tests  
Summary: Added **`WatermarkSecurityConfig.test.ts`**: mocks **`@/api/templates`** and **`ElMessage`**; asserts **Save** disabled for **`SINGLE`**, not disabled for **`COMPOSITE`**, and **Clear** invokes **`clearTemplateRenderConfig`** for single templates.  
Files changed: `frontend/src/__tests__/views/templates/WatermarkSecurityConfig.test.ts`, `07-iteration-log.md`.  
Validation commands: `npx vitest run src/__tests__/views/templates/WatermarkSecurityConfig.test.ts` (from `frontend/`).  
Validation result: 3 tests, exit 0.  
Related tracking items: REQ-R7  

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

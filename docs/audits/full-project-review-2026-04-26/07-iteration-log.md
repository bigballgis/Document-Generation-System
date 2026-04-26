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

## 2026-04-26: Template testing hardening (ad-hoc)

Summary: Aligned frontend test-case types with backend JSON (`testDataJson`, `comparisonType`, `TestReportDTO` counters). `TemplateTestService` now runs real `DocumentGeneratorService` rendering plus `DocxTextExtractor` for text assertions and SHA-256 of DOCX bytes for snapshot mode. Added `TemplateTestRenderOutcome`, composite in-memory render helper, backward-compatible Jackson aliases on `CreateTestCaseRequest`, and `testCaseName` on `TestResultDTO`. Improved template detail test UI (hints, JSON validation, result drawer).  
Validation: `mvn "-Dtest=TemplateTestServiceTest" test` and `mvn -DskipTests compile` from `backend/` — BUILD SUCCESS. Frontend Vitest for `TestCaseFormDialog` hit a worker timeout in this environment (no test assertion failures observed).

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

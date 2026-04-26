# Traceability Matrix

This file tracks requirements, implementation, tests, and documentation. Every future remediation workstream should update the related rows.

## Status Values

- `Open`: gap identified, not yet remediated.
- `In Progress`: remediation has started.
- `Implemented`: code or documentation has been completed.
- `Verified`: test or manual acceptance evidence exists.
- `Deferred`: explicitly postponed with a reason and target version.

## High-Priority Tracking Items

| ID | Requirement or Issue | Design or Spec | Implementation Files | Test Files | Documentation | Status |
| --- | --- | --- | --- | --- | --- | --- |
| SEC-OO-001 | OnlyOffice callbacks must verify source and must not anonymously overwrite files | TBD security design | `OnlyOfficeService`, `CompositeTemplateController`, `SecurityConfig` | TBD | TBD | Open |
| SEC-SSRF-001 | Callback URLs and webhook URLs need SSRF restrictions | TBD security design | `OnlyOfficeService`, `CompositeTemplateController`, `WebhookService` | TBD | TBD | Open |
| CONTRACT-NODE-001 | Java `/merge` and Node `/merge-segments` must use one contract | TBD API contract | `DocumentMergeService`, `server.js`, `merge-segments.js` | TBD contract tests | TBD | Open |
| CONTRACT-NODE-002 | Java `/watermark` and Node watermark behavior must align | TBD API contract | `WatermarkService`, `render.js` | TBD | TBD | Open |
| CONTRACT-EXPR-001 | `ExpressionType` and Node evaluate `type` mapping must align | TBD API contract | `ExpressionEngineImpl`, `evaluate.js` | TBD | `docs/template-expression-filters.md` to verify | Open |
| CONTRACT-COV-001 | Coverage scanning must not misuse `/evaluate` | [20-composite-coverage-variable-scan-contract.md](20-composite-coverage-variable-scan-contract.md) (WS-02-T06/T07) | `CompositeCoverageService`, `docxtemplater-service/src/routes/scan-variables.js` | Jest `integration.test.js` (`POST /scan-variables`); `CompositeCoverageServiceScanVariablesTest` | `07-iteration-log.md` (WS-02-T06, WS-02-T07) | Verified |
| IMPORT-ZIP-001 | ZIP imports need size, count, ratio, and path limits | Composite import/export spec to update | `CompositeImportExportService`, `CompositeZipImportProperties`, `application.yml` (`composite-import.zip`) | `CompositeImportExportServiceTest` (WS-03-T01/T02/T03 limits, allowlist, MinIO name sanitization) | TBD | Implemented (limits + allowlist + storage component sanitization) |
| REQ-R7-001 | Composite import/export R7: `render-config.json` | `.kiro/specs/composite-template-full-import/requirements.md` (R7 English status); `17-composite-r7-render-config-scope.md` | `CompositeImportExportService` (not implemented) | Decision doc only (WS-03-T04) | `17-composite-r7-render-config-scope.md` | Deferred |
| DIFF-TEST-001 | docx content diff checked tasks need real tests | `.kiro/specs/docx-content-diff/tasks.md`; identical-text contract [19-docx-identical-diff-contract.md](19-docx-identical-diff-contract.md) (WS-04-T03) | `ContentDiffService`, `DocxTextExtractor`, `SegmentVersionService` | Backend: `ContentDiffServiceTest`, `DocxTextExtractorTest`, `SegmentVersionServiceTest`; frontend: `SegmentVersionDialog.test.ts` (WS-04-T08/09) | [segment-version-api.md](../../segment-version-api.md) (WS-04-T03/07) | In Progress |
| MIGRATION-SEG-001 | V30/V36/V39 `segment_versions` upgrade strategy | `.kiro/specs/template-segmentation` to revise | `V30`, `V36`, `V39` | TBD migration tests | [21-database-migration-runbook-v30-v36-v39.md](21-database-migration-runbook-v30-v36-v39.md) (WS-07-T05) | In Progress |
| FRONT-OO-001 | OnlyOfficeEditor prop changes and multi-instance behavior | WS-06-T03 characterization; WS-06-T04 fix | `OnlyOfficeEditor.vue`, `DesignStage.vue` | `OnlyOfficeEditor.test.ts` (WS-06-T04) | `07-iteration-log.md` (WS-06-T03, WS-06-T04) | Verified |
| FRONT-DIFF-001 | VersionDiffPanel must show non-text-only diffs | WS-06-T06 | `VersionDiffPanel.vue` | `VersionDiffPanel.test.ts` | `07-iteration-log.md` (WS-06-T06) | Verified |
| INFRA-CI-001 | Add CI/CD and quality gates | WS-07-T02+ | `.github/workflows/backend-ci.yml`, `.github/workflows/frontend-ci.yml`, `.github/workflows/docxtemplater-ci.yml` | GitHub Actions on push/PR | `07-iteration-log.md` (WS-07-T02, WS-07-T03, WS-07-T04) | In Progress |
| REQ-TEST-PIPELINE-001 | Template test execution uses `DocumentGeneratorService.renderForTemplateTest` (not JSON-only compare) | WS-05-T07 | `TemplateTestService` | `TemplateTestServiceTest` (`characterization_*`) | [16-template-test-execution-semantics.md](16-template-test-execution-semantics.md) | Verified |
| REQ-GEN-ELIGIBILITY-001 | Persisted document generation requires `Template.status == ACTIVE` via `TemplateGenerationEligibilityService` | WS-05-T02 | `TemplateGenerationEligibilityService`, `DocumentGeneratorService`, `DynamicApiService`, `AsyncDocumentService`, `BatchDocumentService` | `TemplateGenerationEligibilityServiceTest`, `DocumentGeneratorServiceGenerationEligibilityTest`, `AsyncDocumentServiceSubmitEligibilityTest`, `BatchDocumentServiceSubmitEligibilityTest` | `07-iteration-log.md` (WS-05-T02) | Verified |
| REQ-GEN-VERSION-CHAR-001 | `?version=` on sync generate API: validated in `DynamicApiService` but does not change `DocumentGeneratorService` render path | WS-05-T03 | `DynamicApiService`, `DocumentGeneratorService` | `DynamicApiServiceVersionParameterTest` | [18-generate-api-version-parameter-behavior.md](18-generate-api-version-parameter-behavior.md) | Verified |
| REQ-GEN-VERSION-CONTRACT-001 | Normative contract: `?version=` validation-only; historical file rendering not supported until WS-05-T05 | WS-05-T04 | N/A (documentation) | N/A | [`docs/versioned-template-generation-contract.md`](../../versioned-template-generation-contract.md) | Implemented |
| REQ-GEN-VERSION-IMPL-001 | WS-05-T05: pass validated sync `version` into `DocumentGeneratorService`; async/batch/scheduled pass `null`; render path unchanged | WS-05-T05 | `DynamicApiService`, `DocumentGeneratorService`, `AsyncDocumentService`, `BatchDocumentService`, `ScheduledTaskService` | `DynamicApiServiceVersionParameterTest`, `DocumentGeneratorServiceSyncVersionRenderPathTest` | `docs/versioned-template-generation-contract.md` | Verified |
| REQ-COMPOSITE-ACTIVATE-001 | Composite activation uses `TemplateStateMachineService.transition(..., ACTIVE)` after assembly checks; same DRAFT/REVIEWED rules as single templates | WS-05-T06 | `CompositeTemplateService`, `TemplateStateMachineService` | `CompositeTemplateServiceActivateTest` | `07-iteration-log.md` (WS-05-T06) | Verified |
| FRONT-WS-06-T02 | Template workspace reloads when route template `id` changes without remount | WS-06-T02 | `Index.vue` | `TemplateWorkspaceIndex.test.ts` (WS-06-T02 describe) | `07-iteration-log.md` (WS-06-T02) | Verified |

## Update Rules

1. Add or update matrix rows before implementation starts.
2. Every `Verified` row must point to a test command, test file, or manual acceptance record.
3. Every `Deferred` row must explain reason, impact, and target version.
4. Do not mark a task complete unless implementation and test evidence exist.
5. Keep all descriptions in English for handoff to lower-tier implementation models.

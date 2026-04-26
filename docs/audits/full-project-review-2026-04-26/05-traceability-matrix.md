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
| CONTRACT-COV-001 | Coverage scanning must not misuse `/evaluate` | TBD API contract | `CompositeCoverageService`, Node route TBD | TBD | TBD | Open |
| IMPORT-ZIP-001 | ZIP imports need size, count, ratio, and path limits | Composite import/export spec to update | `CompositeImportExportService`, `CompositeZipImportProperties`, `application.yml` (`composite-import.zip`) | `CompositeImportExportServiceTest` (WS-03-T01/T02/T03 limits, allowlist, MinIO name sanitization) | TBD | Implemented (limits + allowlist + storage component sanitization) |
| REQ-R7-001 | Composite import/export R7: `render-config.json` | `.kiro/specs/composite-template-full-import/requirements.md` (R7 English status); `17-composite-r7-render-config-scope.md` | `CompositeImportExportService` (not implemented) | Decision doc only (WS-03-T04) | `17-composite-r7-render-config-scope.md` | Deferred |
| DIFF-TEST-001 | docx content diff checked tasks need real tests | `.kiro/specs/docx-content-diff/tasks.md` | `ContentDiffService`, `DocxTextExtractor`, `SegmentVersionService` | TBD | `docs/segment-version-api.md` to update | Open |
| MIGRATION-SEG-001 | V30/V36/V39 `segment_versions` upgrade strategy | `.kiro/specs/template-segmentation` to revise | `V30`, `V36`, `V39` | TBD migration tests | TBD runbook | Open |
| FRONT-OO-001 | OnlyOfficeEditor prop changes and multi-instance behavior | TBD frontend design | `OnlyOfficeEditor.vue`, `DesignStage.vue` | TBD | TBD | Open |
| FRONT-DIFF-001 | VersionDiffPanel must show non-text-only diffs | TBD | `VersionDiffPanel.vue` | TBD | TBD | Open |
| INFRA-CI-001 | Add CI/CD and quality gates | TBD | `.github/workflows/*` or equivalent CI | CI self-validation | TBD | Open |
| REQ-TEST-PIPELINE-001 | Template test execution uses `DocumentGeneratorService.renderForTemplateTest` (not JSON-only compare) | WS-05-T07 | `TemplateTestService` | `TemplateTestServiceTest` (`characterization_*`) | [16-template-test-execution-semantics.md](16-template-test-execution-semantics.md) | Verified |

## Update Rules

1. Add or update matrix rows before implementation starts.
2. Every `Verified` row must point to a test command, test file, or manual acceptance record.
3. Every `Deferred` row must explain reason, impact, and target version.
4. Do not mark a task complete unless implementation and test evidence exist.
5. Keep all descriptions in English for handoff to lower-tier implementation models.

# Workstreams

## WS-01: OnlyOffice and Outbound Call Security

Goal: eliminate forged callbacks, SSRF, unbounded downloads, and cross-tenant write risks.

Scope:

- `SecurityConfig`
- `OnlyOfficeController`
- `OnlyOfficeService`
- `CompositeTemplateController`
- `RestTemplateConfig`
- `WebhookService`
- OnlyOffice Docker configuration

Deliverables:

- Authenticated and source-validated OnlyOffice callbacks.
- URL allowlists and private network rejection.
- RestTemplate timeouts and response size limits.
- Security regression tests.

Lower-tier task shape:

- Change only one callback path at a time.
- Add tests before moving to the second path.
- Do not redesign tenant isolation without escalation.

## WS-02: Java and Node Service Contracts

Goal: align backend calls with the real Docxtemplater service API.

Scope:

- `DocumentMergeService`
- `WatermarkService`
- `ExpressionEngineImpl`
- `CompositeCoverageService`
- `docxtemplater-service/server.js`
- `docxtemplater-service/src/routes/*`

Deliverables:

- Route and payload contract documentation.
- Working merge, watermark, expression, and variable scanning contracts.
- Contract tests.

Lower-tier task shape:

- Fix one endpoint contract per task.
- Include a minimal integration or contract test.
- Do not change both Java and Node semantics without an explicit contract section.

## WS-03: Composite Template Import and Export

Goal: complete deployment package behavior and harden import safety.

Scope:

- `CompositeImportExportService`
- `CompositeTemplateController`
- `AssemblyEngineService`
- `.kiro/specs/composite-template-full-import`
- `docs`

Deliverables:

- Safe ZIP import.
- Implemented or explicitly deferred `render-config.json`.
- Round-trip tests for parameters, header/footer files, test data, coverage, and render config.

Lower-tier task shape:

- Separate security hardening from feature completion.
- Never modify migration scripts as part of ZIP import hardening.
- Add negative tests for malicious archives.

## WS-04: Segment Versions and docx Content Diff

Goal: make segment versioning and content diff behavior verifiable and maintainable.

Scope:

- `SegmentVersionService`
- `SegmentVersionRepository`
- `ContentDiffService`
- `DocxTextExtractor`
- `SegmentVersionDialog.vue`
- `docs/segment-version-api.md`

Deliverables:

- Concurrent publish handling.
- Unified diff contract.
- Correct XML parse failure semantics.
- Backend unit and property tests.
- Frontend component tests.

Lower-tier task shape:

- Split backend diff, backend versioning, and frontend UI into separate tasks.
- Include explicit expected behavior for identical text and extraction failure.
- Do not silently change API response fields without updating docs.

## WS-05: Template Generation, State Machine, and Versioning

Goal: align generation entry points and template lifecycle behavior.

Scope:

- `DynamicApiService`
- `DocumentGeneratorService`
- `AsyncDocumentService`
- `BatchDocumentService`
- `TemplateStateMachineService`
- `CompositeTemplateService`
- `TemplateMarketService`
- `TemplateTestService`

Deliverables:

- Centralized generation eligibility rule.
- Real `version` behavior or explicit rejection.
- Unified composite activation and review rule.
- Composite marketplace copy behavior.
- Correct test case semantics.

Lower-tier task shape:

- Start with read-only characterization tests.
- Change one entry point at a time after central rule is defined.
- Escalate if product semantics are ambiguous.

## WS-06: Frontend Workspace Experience

Goal: fix workspace state drift and OnlyOffice multi-instance issues.

Scope:

- `templateWorkspace` store
- `template-workspace/Index.vue`
- `DesignStage.vue`
- `SegmentCanvas.vue`
- `SegmentVersionDialog.vue`
- `OnlyOfficeEditor.vue`
- `VersionDiffPanel.vue`
- i18n JSON files

Deliverables:

- Route-change reload.
- OnlyOffice prop-change remount or reinitialization.
- Correct multi-tab insertion target.
- Complete diff display.
- Complete i18n.
- Frontend tests.

Lower-tier task shape:

- Prefer component-level tests before UI refactors.
- Keep each task to one component unless the store contract is the actual problem.
- Preserve existing user-facing behavior unless a defect is documented.

## WS-07: Testing, CI/CD, and Release Governance

Goal: establish sustainable quality gates.

Scope:

- Backend tests.
- Frontend tests.
- Docxtemplater service tests.
- Dockerfiles.
- Compose configuration.
- CI configuration.
- `.kiro/specs`
- `docs`

Deliverables:

- CI pipeline.
- Traceability matrix.
- Release runbook.
- Migration runbook.
- Untracked artifact review.
- Dependency audit checklist.

Lower-tier task shape:

- Keep CI changes incremental.
- Add one pipeline stage per task.
- Do not introduce mandatory tools that are not available in the project environment.

## WS-08: Performance, Reliability, and Observability

Goal: handle large files, concurrency, LibreOffice, MinIO, logs, and metrics safely.

Scope:

- Docxtemplater service.
- PDF conversion.
- Segment merging.
- MinIO read/write paths.
- Actuator, Micrometer, and logging.

Deliverables:

- Request size and concurrency limits.
- LibreOffice resource isolation.
- Large-file streaming where practical.
- Metrics and alerts.
- Backup and restore strategy.

Lower-tier task shape:

- Measure before optimizing when possible.
- Add limits before broad refactors.
- Keep operational documentation updated with every runtime change.

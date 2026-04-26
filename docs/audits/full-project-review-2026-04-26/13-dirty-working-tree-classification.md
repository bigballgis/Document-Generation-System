# Dirty Working Tree Classification

Task: `WS-07-T01`  
Date: 2026-04-26  
Repository: `D:\working\Document Generation System`

## Purpose

This document classifies current uncommitted and untracked items into categories so implementation tasks can avoid scope drift and accidental commits of generated artifacts, binaries, or secrets.

This is a classification only:

- No files are deleted.
- No files are committed.
- No scope decisions are enforced here.

## Evidence

The repository currently has both modified tracked files and untracked files. Evidence source: `git status` output from this session.

## Classification Buckets

- Feature code (intended): source code that implements product behavior.
- Tests (intended): tests that validate behavior.
- Documentation (intended): specs, docs, audit package, runbooks.
- Tooling and agent constraints (intended): `.cursor/`, `AGENTS.md`, `CLAUDE.md`.
- Sample assets (intended): template examples and sample data shipped with the repository.
- Local overrides (review required): environment-specific patches or vendor overrides.
- Scripts (review required): operational scripts that may embed credentials or environment assumptions.
- Binaries / archives (avoid committing unless explicitly intended): zip files and other binary artifacts.
- Generated artifacts (avoid committing): build outputs and other generated files.
- Potential secrets (must review): files that may include credentials or tokens.

## Modified Tracked Files

### Documentation and specs (likely intended)

- `.kiro/specs/template-parameter-redesign/backend-cleanup/tasks.md`
- `.kiro/specs/template-parameter-redesign/backend-core/tasks.md`
- `.kiro/specs/template-parameter-redesign/backend-validation-coverage/tasks.md`
- `.kiro/specs/template-segmentation/tasks.md`

### Tooling / editor settings (review required)

- `.vscode/settings.json`

### Backend feature code and configuration (likely intended)

- `backend/pom.xml`
- `backend/src/main/java/com/docgen/controller/CompositeTemplateController.java`
- `backend/src/main/java/com/docgen/controller/OnlyOfficeController.java`
- `backend/src/main/java/com/docgen/exception/ErrorCode.java`
- `backend/src/main/java/com/docgen/service/AggregationResolver.java`
- `backend/src/main/java/com/docgen/service/CompositeImportExportService.java`
- `backend/src/main/java/com/docgen/service/OnlyOfficeService.java`
- `backend/src/main/resources/application.yml`

### Backend tests (likely intended)

- `backend/src/test/java/com/docgen/property/ExportConstraintPropertyTest.java`
- `backend/src/test/java/com/docgen/service/CompositeImportExportServiceExportTest.java`
- `backend/src/test/java/com/docgen/service/CompositeImportExportServiceTest.java`

### Infrastructure (review required)

- `docker-compose.yml`

### Docxtemplater service code and dependencies (likely intended)

- `docxtemplater-service/package.json`
- `docxtemplater-service/package-lock.json`
- `docxtemplater-service/src/routes/render.js`

### Frontend feature code and i18n (likely intended)

- `frontend/src/api/composite-templates.ts`
- `frontend/src/components.d.ts` (generated; review whether it should be committed)
- `frontend/src/components/OnlyOfficeEditor.vue`
- `frontend/src/i18n/en-US.json`
- `frontend/src/i18n/zh-CN.json`
- `frontend/src/i18n/zh-TW.json`
- `frontend/src/views/template-workspace/components/CanvasArea.vue`
- `frontend/src/views/template-workspace/components/ControlNodeEditor.vue`
- `frontend/src/views/template-workspace/components/DesignStage.vue`
- `frontend/src/views/template-workspace/components/ParameterSidebar.vue`
- `frontend/src/views/template-workspace/components/SegmentCanvas.vue`
- `frontend/src/views/template-workspace/components/SegmentDetailDesign.vue`
- `frontend/src/views/template-workspace/components/TestStage.vue`
- `frontend/src/views/template-workspace/components/VersionDiffPanel.vue`
- `frontend/src/views/templates/Editor.vue`
- `frontend/src/views/templates/Index.vue`

### Frontend deletion (review required)

- `frontend/src/components/TemplateTagToolbar.vue` (deleted)

## Untracked Files and Directories

### Agent constraints and project skills (intended)

- `.cursor/`
- `AGENTS.md`
- `CLAUDE.md`

### Audit and documentation (intended)

- `docs/` (includes `docs/audits/full-project-review-2026-04-26/`)
- `.kiro/specs/composite-template-full-import/`
- `.kiro/specs/docx-content-diff/`

### Backend feature code (likely intended)

- `backend/src/main/java/com/docgen/dto/ContentDiffLine.java`
- `backend/src/main/java/com/docgen/dto/ContentDiffResult.java`
- `backend/src/main/java/com/docgen/dto/ExtractedText.java`
- `backend/src/main/java/com/docgen/dto/PublishSegmentRequest.java`
- `backend/src/main/java/com/docgen/dto/SegmentVersionDTO.java`
- `backend/src/main/java/com/docgen/dto/SegmentVersionDiffResult.java`
- `backend/src/main/java/com/docgen/entity/SegmentVersion.java`
- `backend/src/main/java/com/docgen/repository/SegmentVersionRepository.java`
- `backend/src/main/java/com/docgen/service/ContentDiffService.java`
- `backend/src/main/java/com/docgen/service/DocxTextExtractor.java`
- `backend/src/main/java/com/docgen/service/SegmentVersionService.java`
- `backend/src/main/resources/db/migration/V39__create_segment_versions_inline.sql`

### Frontend feature code (likely intended)

- `frontend/src/views/template-workspace/components/SegmentVersionDialog.vue`

### Local overrides and patches (review required)

- `onlyoffice-fixes/`

### Scripts (review required)

- `scripts/`

### Sample template assets (likely intended)

- `templates/`

### Docxtemplater helper scripts (review required)

- `docxtemplater-service/generate-fol-template.mjs`

### Binary artifacts (avoid committing unless explicitly intended)

- `fol-template-import.zip`

## Notes and Risks

- `frontend/src/components.d.ts` is typically generated by `unplugin-vue-components`. Decide whether this repository treats it as committed source of truth or a generated artifact.
- `onlyoffice-fixes/` may contain vendor overrides. Treat as security-sensitive and review before committing to a release branch.
- `scripts/` may include environment assumptions or embedded credentials. Review before use or commit.
- `fol-template-import.zip` is a binary artifact. Avoid committing unless it is explicitly intended as a sample asset with a documented purpose.
- There may be additional untracked generated artifacts not shown here if the working tree changes after this snapshot.

## Next Step

Proceed to `WS-02-T01` (document Java-to-Node contract mismatches) and `WS-01-T01` (characterize OnlyOffice callback behavior) using the execution order in `11-execution-sequence.md`.

# Evidence Index

## Backend Security

- `backend/src/main/java/com/docgen/config/SecurityConfig.java`
  - OnlyOffice callback endpoints are anonymously allowed.
  - Actuator, Swagger, and API docs are anonymously allowed.
  - CORS uses wildcard origin patterns with credentials enabled.
- `backend/src/main/java/com/docgen/service/OnlyOfficeService.java`
  - The callback body `url` is downloaded directly and used to overwrite template files.
  - No callback JWT or body token validation was identified.
- `backend/src/main/java/com/docgen/controller/CompositeTemplateController.java`
  - Segment OnlyOffice callbacks also download and overwrite segment files.
  - Segment version API endpoints are implemented here.
- `backend/src/main/java/com/docgen/config/RestTemplateConfig.java`
  - Uses a raw `new RestTemplate()` without central timeout configuration.
- `backend/src/main/java/com/docgen/controller/AuthController.java`
  - Registration defaults to `tenantId = 1L` when no tenant is supplied.
- `backend/src/main/resources/application.yml`
  - Contains default JWT, OnlyOffice, MinIO, and encryption secrets.
  - Actuator health details and components are always shown.

## Backend Business Consistency

- `backend/src/main/java/com/docgen/service/DynamicApiService.java`
  - Synchronous generation checks `ACTIVE`.
  - The `version` parameter is validated but not carried through to rendering.
- `backend/src/main/java/com/docgen/service/DocumentGeneratorService.java`
  - The internal generation entry point does not centralize the `ACTIVE` gate.
- `backend/src/main/java/com/docgen/service/AsyncDocumentService.java`
  - Asynchronous generation does not show the same state gate.
- `backend/src/main/java/com/docgen/service/BatchDocumentService.java`
  - Batch generation does not show the same state gate.
- `backend/src/main/java/com/docgen/service/CompositeTemplateService.java`
  - Composite activation may set `ACTIVE` directly instead of using the shared state machine.
- `backend/src/main/java/com/docgen/service/TemplateMarketService.java`
  - Marketplace copying may not fully support composite templates, assembly config, and MinIO segment objects.
- `backend/src/main/java/com/docgen/service/TemplateTestService.java`
  - Test case execution may not match the real document generation pipeline.

## Composite Templates and ZIP Import/Export

- `backend/src/main/java/com/docgen/service/CompositeImportExportService.java`
  - ZIP import uses `readAllBytes()`.
  - Export does not appear to include `render-config.json`.
  - Object paths include imported segment names.
- `.kiro/specs/composite-template-full-import/requirements.md`
  - Requirement R7 requires watermark and barcode config in `render-config.json`.
- `.kiro/specs/composite-template-full-import/tasks.md`
  - No corresponding R7 task was identified.

## Segment Versions and Content Diff

- `backend/src/main/java/com/docgen/service/SegmentVersionService.java`
  - Version numbers are assigned using `MAX(versionNumber)+1`, which is vulnerable to concurrent publish conflicts.
  - Diff failures are downgraded to `contentChanged=false`.
  - Rollback copies a versioned file back over the current file.
- `backend/src/main/java/com/docgen/service/ContentDiffService.java`
  - Identical content returns an empty diff, while the requirement describes `EQUAL` lines.
- `backend/src/main/java/com/docgen/service/DocxTextExtractor.java`
  - XML parse failures are logged and converted to empty text.
- `backend/src/main/resources/db/migration/V39__create_segment_versions_inline.sql`
  - Creates the inline `segment_versions` table.
- `backend/src/main/resources/db/migration/V36__migrate_assembly_config_and_drop_segments.sql`
  - Drops the old segment library and old `segment_versions`.
- `.kiro/specs/docx-content-diff/tasks.md`
  - Claimed completed tests do not match the observed test assets.
- `docs/segment-version-api.md`
  - Does not document `includeContentDiff` or the newer response fields.

## Docxtemplater Service

- `docxtemplater-service/server.js`
  - Registers `/render`, `/evaluate`, `/convert-pdf`, and `/merge-segments`.
  - Does not register `/merge` or `/watermark`.
  - Global JSON limit is 50 MB.
- `docxtemplater-service/src/routes/evaluate.js`
  - Only `type === 'excel'` selects Excel evaluation.
- `docxtemplater-service/src/sandbox.js`
  - `isolated-vm` is optional; missing it falls back to Node `vm`.
- `docxtemplater-service/src/routes/merge-segments.js`
  - Merges `word/document.xml` by string manipulation and does not merge media or relationships.
- `docxtemplater-service/src/routes/convert-pdf.js`
  - Uses LibreOffice and UNO conversion paths that need resource isolation.
- `docxtemplater-service/src/minio-client.js`
  - Contains default MinIO credentials.

## Frontend

- `frontend/src/views/template-workspace/Index.vue`
  - Workspace initialization is mounted-based and needs route parameter reuse review.
- `frontend/src/components/OnlyOfficeEditor.vue`
  - Prop changes do not reinitialize the editor.
  - Signing failure continues without a token.
  - Text insertion may target the wrong iframe when multiple instances exist.
- `frontend/src/views/template-workspace/components/DesignStage.vue`
  - Contains debug `console.log` usage.
- `frontend/src/views/template-workspace/components/VersionDiffPanel.vue`
  - Details may not render when only non-text diffs exist.
- `frontend/src/views/template-workspace/components/SegmentVersionDialog.vue`
  - In-place `Set.add` may not trigger recomputation.
  - `sameVersionWarning` i18n is missing.
- `frontend/src/api/request.ts`
  - Some error messages are hardcoded in English rather than routed through i18n.
- `frontend/Dockerfile`
  - Uses `npm install` and skips type checking during the image build.

## Infrastructure and Release

- `docker-compose.yml`
  - Exposes PostgreSQL, Redis, MinIO, OnlyOffice, and Docxtemplater ports.
  - Enables `ALLOW_PRIVATE_IP_ADDRESS` and `ALLOW_META_IP_ADDRESS` for OnlyOffice.
  - Uses floating `latest` tags for some images.
  - Does not set CPU or memory limits.
- `frontend/nginx/default.conf.template`
  - Proxies `/actuator/`, Swagger, and API docs.
- `.env.example`
  - Placeholder secrets must be explicitly documented as non-production values.
- `.github/workflows`
  - No repository CI configuration was identified.

## Dirty Working Tree Risk

The current working tree contains many uncommitted and untracked files. Before implementation work starts, classify each change into one of these buckets:

- Feature code intended for the branch.
- Review or design documentation.
- Example template assets.
- Local debug scripts and OnlyOffice overrides.
- Binary or generated artifacts.
- Temporary files that should be ignored.

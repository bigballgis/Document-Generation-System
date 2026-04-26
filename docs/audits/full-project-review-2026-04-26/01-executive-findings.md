# Executive Findings

## P0: Immediate Remediation Required

### Forged OnlyOffice callbacks can overwrite stored documents

`SecurityConfig` allows anonymous access to `/api/templates/*/onlyoffice-callback` and `/api/composite-templates/*/segments/*/onlyoffice-callback`. The callback handlers trust the `url` field in the request body, download its content, and overwrite template or segment files in MinIO.

Impact:

- A forged callback may bypass tenant isolation and modify another tenant's template.
- The download URL creates an SSRF vector.
- Malicious content may overwrite stored template or segment files.

Evidence:

- `backend/src/main/java/com/docgen/config/SecurityConfig.java`
- `backend/src/main/java/com/docgen/service/OnlyOfficeService.java`
- `backend/src/main/java/com/docgen/controller/CompositeTemplateController.java`
- `backend/src/main/java/com/docgen/filter/TenantIsolationFilter.java`

### Backend and Docxtemplater service contracts do not match

Several Java backend calls do not match the routes or payload semantics implemented by the Node service:

- `DocumentMergeService` calls `/merge`, while the Node service registers `/merge-segments`.
- `WatermarkService` calls `/watermark`, while the Node service only applies watermarking through `/render`.
- `ExpressionEngineImpl` sends `ExpressionType.name()` values such as `EXCEL_FORMULA`, while the Node service only treats `type === 'excel'` as Excel mode.
- `CompositeCoverageService` calls `/evaluate` with `templatePath` and expects variables, while Node `/evaluate` requires an `expression`.

Impact:

- Document merge, watermarking, Excel formulas, and coverage scanning may fail at runtime or produce incorrect behavior.
- Java-side mocks can hide real cross-service breakage.

Evidence:

- `backend/src/main/java/com/docgen/service/DocumentMergeService.java`
- `backend/src/main/java/com/docgen/service/WatermarkService.java`
- `backend/src/main/java/com/docgen/service/ExpressionEngineImpl.java`
- `backend/src/main/java/com/docgen/service/CompositeCoverageService.java`
- `docxtemplater-service/server.js`
- `docxtemplater-service/src/routes/evaluate.js`
- `docxtemplater-service/src/routes/merge-segments.js`

### Production defaults and management endpoints are too permissive

`application.yml` contains default JWT, OnlyOffice, MinIO, and encryption secrets. Actuator metrics, Prometheus, Swagger, and API docs are anonymously allowed and proxied by Nginx. Compose exposes PostgreSQL, Redis, MinIO, OnlyOffice, and Docxtemplater ports.

Impact:

- Production deployments may accidentally start with weak default secrets.
- Metrics, health details, and API metadata may leak implementation details.
- Publicly exposed infrastructure ports expand the attack surface.

Evidence:

- `backend/src/main/resources/application.yml`
- `backend/src/main/java/com/docgen/config/SecurityConfig.java`
- `frontend/nginx/default.conf.template`
- `docker-compose.yml`

### ZIP import lacks resource and path safety controls

`CompositeImportExportService.importFromZip` uses `readAllBytes()` for ZIP entries and does not enforce entry count, per-entry size, total size, compression ratio, or safe object names. Imported object paths include `segmentName` with insufficient normalization.

Impact:

- ZIP bombs or large files may exhaust memory.
- Malicious names may create unexpected object storage keys.
- Partial import failures may leave orphaned MinIO objects.

Evidence:

- `backend/src/main/java/com/docgen/service/CompositeImportExportService.java`

## P1: Short-Term Remediation

### Generation state gates are inconsistent

Synchronous generation checks `ACTIVE` in `DynamicApiService`, but `AsyncDocumentService`, `BatchDocumentService`, and `DocumentGeneratorService` do not share a single generation eligibility rule. The `version` parameter is validated but does not appear to select the actual versioned file path during rendering.

Evidence:

- `backend/src/main/java/com/docgen/service/DynamicApiService.java`
- `backend/src/main/java/com/docgen/service/DocumentGeneratorService.java`
- `backend/src/main/java/com/docgen/service/AsyncDocumentService.java`
- `backend/src/main/java/com/docgen/service/BatchDocumentService.java`

### Composite template import/export requirement R7 is not implemented

Requirement R7 requires exporting and importing `render-config.json` for watermark and barcode configuration. The task list does not include R7, and the service implementation does not contain the corresponding logic.

Evidence:

- `.kiro/specs/composite-template-full-import/requirements.md`
- `.kiro/specs/composite-template-full-import/tasks.md`
- `backend/src/main/java/com/docgen/service/CompositeImportExportService.java`

### docx content diff task status does not match test assets

The task document marks backend jqwik/unit tests and frontend Vitest/fast-check tests as complete, but no focused tests were found for `DocxTextExtractor`, `ContentDiffService`, `SegmentVersionService`, or `SegmentVersionDialog`.

Evidence:

- `.kiro/specs/docx-content-diff/tasks.md`
- `backend/src/main/java/com/docgen/service/DocxTextExtractor.java`
- `backend/src/main/java/com/docgen/service/ContentDiffService.java`
- `frontend/src/views/template-workspace/components/SegmentVersionDialog.vue`

### V30/V36/V39 migration history conflicts around `segment_versions`

The old segmentation model and migration use a segment-library-based `segment_versions` table. V36 drops it, and V39 creates a new inline `segment_versions` table with the same name but different semantics. Existing deployments need an explicit upgrade path.

Evidence:

- `.kiro/specs/template-segmentation/tasks.md`
- `backend/src/main/resources/db/migration/V30__create_segment_versions.sql`
- `backend/src/main/resources/db/migration/V36__migrate_assembly_config_and_drop_segments.sql`
- `backend/src/main/resources/db/migration/V39__create_segment_versions_inline.sql`

## P2: Systemic Optimization

- Configure timeouts, connection pooling, retries, circuit breakers, and SSRF protections for outbound HTTP calls.
- Add authentication, endpoint-specific body limits, concurrency limits, and resource isolation to the Docxtemplater service.
- Fix frontend workspace reload behavior, OnlyOffice multi-instance handling, prop-change reinitialization, and incomplete version diff rendering.
- Establish CI/CD for backend tests, frontend type checking and tests, Node Jest tests, Docker builds, dependency audits, and Flyway validation.
- Pin container versions, run services as non-root users, set CPU and memory limits, and protect Swagger, Actuator, and Prometheus in production.

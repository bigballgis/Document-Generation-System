# Requirements: Composite template full deployment package import/export

## Background

Composite template (COMPOSITE) import/export currently has these gaps:

1. **Exported ZIP lacks parameter definitions**: `CompositeImportExportService.exportAsZip()` produces `config.json`, `segments/*.docx`, `test-data.json`, `coverage-report.json`, but **not** the parameter tree (`parameters.json`).
2. **ZIP import does not restore parameters**: `importFromZip()` restores template metadata, `assembly_config`, and test data only — **not** parameter definitions.
3. **`config.json` misses key fields**: Omits `outputFormat`, `storageStrategy`, `async`, `reviewRequired`, etc.
4. **No ZIP import entry on template list**: Only `.docx` and `.json` config import — no composite ZIP flow.
5. **Export rule too strict**: ZIP export required ACTIVE; DRAFT should be allowed for dev/test.
6. **Header/footer `.docx` not exported**: Files referenced by `headerFilePath` / `footerFilePath` in `assembly_config` are omitted from the ZIP.

## Requirements

### R1: Export ZIP includes full parameter tree
- Add `parameters.json` to the export ZIP
- Full nested tree (`children`)
- Each node: name, parameterType, dataType, required, defaultValue, description, sortOrder, expressionText, expressionType, validationRules, children
- Exclude runtime-only fields: id, templateId, parentId, version, createdAt, updatedAt

### R2: Import ZIP restores parameter tree
- Read `parameters.json`; create definitions recursively
- Preserve parent/child relationships
- Preserve parameterType (REQUEST/DERIVED), expressionText, expressionType, validationRules
- If `parameters.json` is missing, skip (backward compatible)

### R3: Export `config.json` includes full template fields
- Add: outputFormat, storageStrategy, async, reviewRequired, templateType
- Import restores these fields

### R4: Export includes header/footer `.docx`
- Scan all segment headerFilePath/footerFilePath in `assembly_config`
- Download from MinIO into ZIP `headers/` and `footers/`
- On import, upload to MinIO and rewrite paths in `assembly_config`

### R5: Relax export status rule
- DRAFT and ACTIVE composite templates may export ZIP
- Record source status on export; imported template is always DRAFT

### R6: Template list — ZIP import button
- Add “Import composite package” next to existing import actions
- Accept `.zip`
- Call `POST /api/composite-templates/import`
- On success, refresh list and show success message

### R7: Export ZIP includes render config (watermark/barcode)
- If template has render config (watermark, barcodes), export `render-config.json`
- On import, store as supplementary template metadata (optional — skip if missing)

---

### R7 implementation status

**Status:** **Implemented** (2026-04-28). Composite ZIP export/import supports optional `render-config.json`; validated payload is stored in `templates.render_config` (Flyway V41). Schema and security rules: `docs/development/render-config-json-schema.md`.

Historical deferral rationale (superseded): `docs/audits/full-project-review-2026-04-26/17-composite-r7-render-config-scope.md`.

## Acceptance criteria

1. Export a COMPOSITE ZIP from the system including parameters, segments, and test data.
2. Import that ZIP in another tenant/environment.
3. Imported template has full parameter tree, `assembly_config`, and test data.
4. Imported template can render documents successfully.
5. Frontend can import the ZIP via the new button.

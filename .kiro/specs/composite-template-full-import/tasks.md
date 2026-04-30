# Task list

## Task 1: Extend `CompositeExportConfig` inner types
- [x] Add fields on `CompositeImportExportService.CompositeExportConfig`: `outputFormat`, `storageStrategy`, `async`, `reviewRequired`, `sourceStatus`
- [x] Add fields on `CompositeExportConfig.SegmentExportEntry`: `headerFileName`, `footerFileName`, `pageNumberFormat`, `pageNumberStart`
- [x] Add inner class `ParameterExportEntry` (name, parameterType, dataType, required, defaultValue, description, sortOrder, expressionText, expressionType, validationRules, children)

## Task 2: Change `exportAsZip()` — export parameters and header/footer
- [x] Drop ACTIVE-only rule; allow DRAFT or ACTIVE
- [x] Populate new template fields in `buildExportConfig()`
- [x] Populate SegmentExportEntry headerFileName/footerFileName/pageNumberFormat/pageNumberStart in `buildExportConfig()`
- [x] Inject `ParameterService`
- [x] Export `parameters.json`: call `getParameterTree()`, map to `ParameterExportEntry` list, write into ZIP
- [x] Export header/footer `.docx`: scan all segment headerFilePath/footerFilePath, dedupe download, write under ZIP `headers/` and `footers/`

## Task 3: Change `importFromZip()` — import parameters and header/footer
- [x] Parse `parameters.json` from ZIP
- [x] Parse `headers/*.docx` and `footers/*.docx` from ZIP
- [x] Upload header/footer files to MinIO; build fileName → filePath map
- [x] In `rebuildAssemblyConfig()`, restore headerFilePath/footerFilePath/pageNumberFormat/pageNumberStart
- [x] Restore template fields: outputFormat, storageStrategy, async, reviewRequired
- [x] Import parameter tree: recursively create `ParameterDefinition` rows preserving metadata

## Task 4: Frontend — ZIP import on template list
- [x] In `frontend/src/views/templates/Index.vue`, add “Import composite package” button and hidden file input (`accept=".zip"`)
- [x] Add `handleImportZip()` calling `importCompositeFromZip()`
- [x] Add `template.importCompositePackage` in all locale files

## Task 5: Build verification
- [x] Backend compiles (`mvn compile`)
- [x] Frontend type-check passes (`vue-tsc --noEmit`)

## Task 6: R7 `render-config.json` (implemented — REQ-R7-001, 2026-04-28)

**Schema:** `docs/development/render-config-json-schema.md`

- [x] Optional ZIP entry `render-config.json`; allowlisted import path; export when `templates.render_config` is non-empty.
- [x] Persistence: **`templates.render_config`** JSONB (**Flyway V41**).
- [x] SSRF-safe watermark validation via **`RenderConfigValidator`** + **`WatermarkService`** (reject remote image URLs).

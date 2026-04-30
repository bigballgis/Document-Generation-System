# Design: Composite template full deployment package import/export

## ZIP layout

```
composite-template.zip
├── config.json              # Template metadata + assembly_config (header/footer path mapping)
├── parameters.json          # Parameter tree (full metadata)
├── segments/
│   ├── Cover_Page.docx
│   ├── Part_A_Definitions.docx
│   └── ...
├── headers/
│   ├── standard-header.docx
│   └── ...
├── footers/
│   ├── standard-footer.docx
│   └── ...
├── test-data.json           # Test cases
└── render-config.json       # Render config (watermark/barcode, optional)
```

## Data formats

### `config.json` (extended)

```json
{
  "templateName": "...",
  "templateDescription": "...",
  "outputFormat": "WORD",
  "storageStrategy": "TEMP",
  "async": false,
  "reviewRequired": false,
  "sourceStatus": "ACTIVE",
  "segments": [
    {
      "segmentName": "Cover Page",
      "segmentType": "COVER",
      "position": 1,
      "enabled": true,
      "pageBreakBefore": false,
      "conditionExpression": null,
      "dataScope": null,
      "headerFileName": "standard-header",
      "footerFileName": "standard-footer",
      "pageNumberFormat": null,
      "pageNumberStart": null
    }
  ]
}
```

### `parameters.json`

```json
[
  {
    "name": "bank",
    "parameterType": "REQUEST",
    "dataType": "OBJECT",
    "required": true,
    "defaultValue": null,
    "description": "Bank details",
    "sortOrder": 0,
    "expressionText": null,
    "expressionType": null,
    "validationRules": null,
    "children": [
      {
        "name": "legal_name",
        "parameterType": "REQUEST",
        "dataType": "STRING",
        "required": true,
        "...": "...",
        "children": []
      }
    ]
  }
]
```

## Backend changes

### 1. Extend `CompositeExportConfig`

File: inner types on `CompositeImportExportService.java` — `CompositeExportConfig`

New fields:
- `outputFormat`, `storageStrategy`, `async`, `reviewRequired`, `sourceStatus`

`SegmentExportEntry` adds:
- `headerFileName`, `footerFileName`, `pageNumberFormat`, `pageNumberStart`

### 2. Parameter export DTO

New inner class `ParameterExportEntry`:
```java
static class ParameterExportEntry {
    String name;
    String parameterType;
    String dataType;
    boolean required;
    String defaultValue;
    String description;
    int sortOrder;
    String expressionText;
    String expressionType;
    Map<String, Object> validationRules;
    List<ParameterExportEntry> children;
}
```

### 3. `exportAsZip()` changes

In `CompositeImportExportService.exportAsZip()`:

1. Allow DRAFT or ACTIVE (remove ACTIVE-only restriction).
2. Write `parameters.json`: `ParameterService.getParameterTree()` → map to `ParameterExportEntry` list.
3. Export header/footer `.docx`: scan `assembly_config` paths, dedupe, download from MinIO, pack.
4. Record header/footer file names in `config.json` (`headerFileName` / `footerFileName`).
5. Include `pageNumberFormat` / `pageNumberStart` on `SegmentExportEntry`.

### 4. `importFromZip()` changes

In `CompositeImportExportService.importFromZip()`:

1. Parse `parameters.json` → recursively create definitions.
2. Parse `headers/*.docx` and `footers/*.docx` → upload to MinIO.
3. When rebuilding `assembly_config`, map header/footer file names back to MinIO paths.
4. Restore outputFormat, storageStrategy, and related template fields.
5. Parse optional `render-config.json` → persist validated render config (see R7 / `templates.render_config`).

### 5. Parameter tree import helper

Add to `ParameterService`:
```java
@Transactional
public void importParameterTree(Long templateId, List<ParameterExportEntry> entries, Long parentId)
```

Recursively create rows preserving metadata (parameterType, expressionText, validationRules, etc.).

### 6. Dependency injection

`CompositeImportExportService` injects `ParameterService` (new dependency).

## Frontend changes

### 1. Template list — ZIP import

File: `frontend/src/views/templates/Index.vue`

Next to existing import actions:
```html
<el-button @click="importZipInput?.click()">
  {{ $t('template.importCompositePackage') }}
</el-button>
<input ref="importZipInput" type="file" accept=".zip" style="display: none"
       @change="handleImportZip" />
```

### 2. Handler

```typescript
async function handleImportZip(event: Event) {
  const file = input.files?.[0]
  if (!file) return
  await importCompositeFromZip(file)
  ElMessage.success(t('message.importSuccess'))
  fetchTemplates()
}
```

### 3. i18n

Add `template.importCompositePackage` in all locale files.

## Intentionally unchanged (original baseline)

- **Note:** R7 adds `templates.render_config` (Flyway V41); see Task 6 in `tasks.md`.
- SecurityConfig: existing `/api/composite-templates/**` rules apply.
- Docker / docker-compose: no change required for this feature.

# `render-config.json` Schema (REQ-R7-001)

Composite template ZIP packages may include an optional root entry **`render-config.json`**. It describes post-merge rendering options applied after segment assembly (currently text and image watermarks).

## Rules

- **Schema version:** `schemaVersion` must be `1` when present. When omitted, version `1` is assumed.
- **Security:** Image watermark `imageSource` must **not** use `http://` or `https://` URLs. Only inline Base64 or `data:image/...;base64,...` values are allowed (enforced by `WatermarkService` and `RenderConfigValidator`).
- **Barcodes:** The `barcodes` field must be omitted or an empty array. Non-empty arrays are rejected until a future release defines barcode support.
- **ZIP limits:** The file is subject to the same `composite-import.zip.*` entry size and path allowlists as other JSON entries.

## Example

```json
{
  "schemaVersion": 1,
  "textWatermark": {
    "text": "CONFIDENTIAL",
    "fontSize": 36,
    "color": "#CCCCCC",
    "opacity": 0.3,
    "rotation": -45
  },
  "imageWatermark": {
    "imageSource": "data:image/png;base64,iVBORw0KGgo...",
    "position": "CENTER",
    "opacity": 0.3
  },
  "barcodes": []
}
```

## Persistence

Validated JSON is stored in PostgreSQL column **`templates.render_config`** (JSONB). Export includes `render-config.json` only when this column contains a non-empty effective configuration.

## REST API (same schema)

| Method | Path | Description |
| --- | --- | --- |
| `PUT` | `/api/templates/{id}/render-config` | Body: `RenderConfigDocument` JSON. Validates like ZIP import; creates a template version snapshot. |
| `DELETE` | `/api/templates/{id}/render-config` | Clears `render_config`; creates a version snapshot. |

`GET /api/templates/{id}` returns `renderConfig` as a JSON string when present (same shape as stored in DB).

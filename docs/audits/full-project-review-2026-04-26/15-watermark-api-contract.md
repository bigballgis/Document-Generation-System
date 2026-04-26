# Watermark API Contract (Java ↔ Node)

## Decision

Standalone watermarking uses **`POST /watermark`** on the Docxtemplater service. It shares implementation with render-time watermarking (`src/utils/watermark.js`). **`POST /render`** may still pass an optional `watermark` object for the same in-process utilities; the Java `WatermarkService` does not call `/render` for watermark-only work.

## `POST /watermark`

**Request (JSON)**

| Field | Required | Description |
|-------|----------|-------------|
| `document` | Yes | Base64-encoded `.docx` bytes |
| `type` | Yes | `text` or `image` |

**Text (`type: "text"`)**

| Field | Required | Description |
|-------|----------|-------------|
| `text` | Yes | Watermark string |
| `fontSize`, `color`, `opacity`, `rotation` | No | Same semantics as `utils/watermark.js` |

**Image (`type: "image"`)**

| Field | Required | Description |
|-------|----------|-------------|
| `imageSource` or `imageBase64` | Yes | Inline base64 payload, or `data:image/...;base64,...` |
| `opacity` | No | 0–1 |
| `position` | No | Accepted for API compatibility; Node util centers the image (same as render path) |

**Rejected**

- `imageSource` / `imageBase64` values that look like `http://` or `https://` URLs (HTTP 400, `WATERMARK_IMAGE_URL_REJECTED`).

**Response**

- Success: `200`, `Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document`, body = modified `.docx` bytes.
- Client errors: `400` with JSON `{ error: { code, message } }`.
- Server errors: `500` with JSON `{ error: { code, message } }`.

## Java `WatermarkService`

Calls `POST /watermark` with the body described above. Validates image sources and rejects remote URLs before calling Node.

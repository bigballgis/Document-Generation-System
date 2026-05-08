# Segment Version Management API

Endpoints for managing segment versions within **composite** templates.

**Base path:** `/api/composite-templates`

All routes require authenticated access consistent with the rest of the composite-template API. Tenant isolation applies to persisted `SegmentVersion` rows.

---

## DTOs

### `SegmentVersionDTO` (publish / list / rollback response)

| Field | Type | Description |
| --- | --- | --- |
| `id` | Long | Primary key of the published version row. |
| `templateId` | Long | Composite template ID. |
| `segmentName` | String | Segment logical name. |
| `versionNumber` | Integer | Monotonic version number for this template + segment (starts at 1). |
| `filePath` | String | MinIO object path of the snapshot `.docx` for this version. |
| `segmentType` | String | Segment type from assembly config at publish time. |
| `configSnapshot` | String | JSON snapshot of key segment flags at publish time. |
| `comment` | String | Optional publisher comment. |
| `createdBy` | Long | User ID of the publisher. |
| `createdAt` | String (ISO-8601) | Publication timestamp. |

---

## Endpoints

### Publish a Segment Version

```
POST /{id}/segments/publish
```

Creates a snapshot (version) of a segment's current state: copies the segment's current MinIO object to a versioned path and inserts a `segment_versions` row.

| Parameter | Location | Type | Required | Description |
| --- | --- | --- | --- | --- |
| `id` | path | Long | Yes | Composite template ID. |
| `segmentName` | body | String | Yes | Name of the segment to publish (must exist in assembly config with a non-empty `filePath`). |
| `comment` | body | String | No | Version comment. |

**Response:** `201 Created` - `SegmentVersionDTO`

**Concurrency (WS-04-T06):** Under heavy concurrent publishes, the server retries transparently when the database unique key `(template_id, segment_name, version_number)` conflicts. If a unique version cannot be assigned after bounded retries, the API responds with **`409 Conflict`** and error code **`SEGMENT_VERSION_PUBLISH_CONFLICT`**; clients should retry the publish request.

---

### List Segment Versions

```
GET /{id}/segments/{segmentName}/versions
```

Returns all published versions for a specific segment, **newest first** (implementation order: descending by `versionNumber`).

| Parameter | Location | Type | Required | Description |
| --- | --- | --- | --- | --- |
| `id` | path | Long | Yes | Composite template ID. |
| `segmentName` | path | String | Yes | Segment name. |

**Response:** `200 OK` - `List<SegmentVersionDTO>`

---

### Compare Segment Versions

```
GET /{id}/segments/{segmentName}/versions/diff?versionA={a}&versionB={b}&includeContentDiff={bool}
```

Returns metadata differences between two published versions, and optionally a **plain-text line diff** of the two snapshot `.docx` bodies (extracted server-side).

| Parameter | Location | Type | Required | Description |
| --- | --- | --- | --- | --- |
| `id` | path | Long | Yes | Composite template ID. |
| `segmentName` | path | String | Yes | Segment name. |
| `versionA` | query | int | Yes | First version number. |
| `versionB` | query | int | Yes | Second version number. |
| `includeContentDiff` | query | boolean | No | Default **`false`**. When **`true`**, the response includes a populated `contentDiffs` list (unless extraction fails; see **Graceful degradation**). When **`false`**, the backend still computes **`contentChanged`** using a lightweight comparison (no full diff lines). |

**Response:** `200 OK` - `SegmentVersionDiffResult`

#### `SegmentVersionDiffResult` (full payload)

| Field | Type | Description |
| --- | --- | --- |
| `templateId` | Long | Template ID echoed from the request context. |
| `segmentName` | String | Segment name echoed. |
| `versionA` | int | First version in the comparison. |
| `versionB` | int | Second version in the comparison. |
| `diffs` | `List<SegmentDiffEntry>` | Metadata field-level differences (e.g. `segmentType`, `configSnapshot`, `comment`). Empty when `versionA == versionB`. |
| `filePathChanged` | boolean | `true` if the two versions reference different MinIO paths. |
| `oldFilePath` | string | MinIO path for `versionA`. |
| `newFilePath` | string | MinIO path for `versionB`. |
| `contentChanged` | boolean | `true` if extracted plain text from the two `.docx` files differs. Still computed when `includeContentDiff=false` (cheap path). |
| `contentDiffs` | `List<ContentDiffLine>` | Line-level text diff. Meaningful rows are returned only when **`includeContentDiff=true`** and extraction succeeds. Otherwise typically an **empty array**. |
| `truncated` | boolean | `true` if either side's text extraction was truncated **or** the diff line list was capped by the server (see **Truncation**). |

#### `SegmentDiffEntry` (metadata diff row)

| Field | Type | Description |
| --- | --- | --- |
| `field` | String | Logical field name (e.g. `segmentType`). |
| `changeType` | String | `ADDED`, `REMOVED`, or `MODIFIED`. |
| `oldValue` | String | Nullable. |
| `newValue` | String | Nullable. |

#### `ContentDiffLine` (body text diff row)

Returned only when `includeContentDiff=true` and extraction succeeds. Each row uses:

| Field | Type | Description |
| --- | --- | --- |
| `type` | String enum | `EQUAL`, `ADDED`, `REMOVED`, or `MODIFIED`. |
| `oldLineNumber` | Integer | Nullable (e.g. absent for `ADDED`). |
| `newLineNumber` | Integer | Nullable (e.g. absent for `REMOVED`). |
| `oldText` | String | Nullable (e.g. absent for `ADDED`). |
| `newText` | String | Nullable (e.g. absent for `REMOVED`). |

#### Identical body text (WS-04-T03 contract)

When extracted plain text for both versions is **equal**:

- **`contentChanged` is `false`.**
- **`contentDiffs` is an empty array** (the API does **not** emit synthetic `EQUAL` rows for this endpoint).

Clients must use **`contentChanged`**, not the presence of `EQUAL` lines, to mean "no textual body change."

Canonical write-up: [19-docx-identical-diff-contract.md](audits/full-project-review-2026-04-26/19-docx-identical-diff-contract.md).

#### Same-version shortcut

When `versionA == versionB`, the server returns an empty metadata `diffs` list, `filePathChanged=false`, and `contentChanged=false` without running content extraction.

#### Truncation

`truncated` may become `true` when:

- Either `.docx` body's extracted text hit the extractor's configured size limit (plain-text truncation with a marker in stored text), **or**
- The line diff exceeded the server's maximum number of diff lines (implementation caps the list and sets truncation).

#### Graceful degradation (content diff)

If text extraction or diff computation throws while building the response, the server **logs a warning** and returns:

- `contentChanged=false`
- `contentDiffs` as an empty list
- `truncated=false`

Metadata in `diffs` and file paths remain from the loaded `SegmentVersion` rows. Clients should treat this as "content diff unavailable" rather than "identical bodies," unless `contentChanged` was computed on the lightweight path (`includeContentDiff=false`).

---

### Rollback Segment to Version

```
POST /{id}/segments/{segmentName}/rollback/{targetVersion}
```

Rolls back a segment by copying the snapshot at `targetVersion` back over the segment's **current** MinIO path referenced in assembly config.

| Parameter | Location | Type | Required | Description |
| --- | --- | --- | --- | --- |
| `id` | path | Long | Yes | Composite template ID. |
| `segmentName` | path | String | Yes | Segment name. |
| `targetVersion` | path | int | Yes | Published version to restore from. |

**Response:** `200 OK` - `SegmentVersionDTO` (descriptor of the target version row; assembly config mutation is service-side).

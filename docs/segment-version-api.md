# Segment Version Management API

Endpoints for managing segment versions within composite templates.

**Base path:** `/api/composite-templates`

## Endpoints

### Publish a Segment Version

```
POST /{id}/segments/publish
```

Creates a snapshot (version) of a segment's current state.

| Parameter | Location | Type | Required | Description |
|-----------|----------|------|----------|-------------|
| id | path | Long | ✅ | Composite template ID |
| segmentName | body | String | ✅ | Name of the segment to publish |
| comment | body | String | ❌ | Version comment |

**Response:** `201 Created` — `SegmentVersionDTO`

---

### List Segment Versions

```
GET /{id}/segments/{segmentName}/versions
```

Returns all published versions for a specific segment.

| Parameter | Location | Type | Required | Description |
|-----------|----------|------|----------|-------------|
| id | path | Long | ✅ | Composite template ID |
| segmentName | path | String | ✅ | Segment name |

**Response:** `200 OK` — `List<SegmentVersionDTO>`

---

### Compare Segment Versions

```
GET /{id}/segments/{segmentName}/versions/diff
```

Returns a diff between two versions of a segment.

| Parameter | Location | Type | Required | Description |
|-----------|----------|------|----------|-------------|
| id | path | Long | ✅ | Composite template ID |
| segmentName | path | String | ✅ | Segment name |
| versionA | query | int | ✅ | First version number |
| versionB | query | int | ✅ | Second version number |
| includeContentDiff | query | boolean | ❌ | When `true`, the response includes `contentDiffs` (plain-text line diff between the two `.docx` bodies). Default is `false`. |

**Response:** `200 OK` — `SegmentVersionDiffResult`

#### Content diff fields (`SegmentVersionDiffResult`)

| Field | Type | Description |
| --- | --- | --- |
| `diffs` | `List<SegmentDiffEntry>` | Metadata field-level diff (unchanged). |
| `filePathChanged` | boolean | Whether the stored MinIO object path changed. |
| `oldFilePath` / `newFilePath` | string | Paths for the two versions being compared. |
| `contentDiffs` | `List<ContentDiffLine>` | Line-level text diff. Populated only when `includeContentDiff=true` and extraction succeeds. Each line has a `type` (`EQUAL`, `ADDED`, `REMOVED`, `MODIFIED`) and optional line numbers / old and new text. |
| `contentChanged` | boolean | `true` if extracted plain text from the two `.docx` files differs. Computed whenever content diff logic runs successfully enough to compare text (see implementation for graceful degradation). |
| `truncated` | boolean | `true` if extraction was truncated or the diff line list was capped. |

**Identical body text (canonical contract, WS-04-T03):** When extracted plain text for both versions is **equal**, the API sets **`contentChanged` to `false`** and returns **`contentDiffs` as an empty array** (no synthetic `EQUAL` rows). Clients must rely on `contentChanged`, not on `EQUAL` rows, to detect “no textual change.” See [19-docx-identical-diff-contract.md](audits/full-project-review-2026-04-26/19-docx-identical-diff-contract.md).

---

### Rollback Segment to Version

```
POST /{id}/segments/{segmentName}/rollback/{targetVersion}
```

Rolls back a segment to a previously published version.

| Parameter | Location | Type | Required | Description |
|-----------|----------|------|----------|-------------|
| id | path | Long | ✅ | Composite template ID |
| segmentName | path | String | ✅ | Segment name |
| targetVersion | path | int | ✅ | Target version number to rollback to |

**Response:** `200 OK` — `SegmentVersionDTO`

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

**Response:** `200 OK` — `SegmentVersionDiffResult`

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

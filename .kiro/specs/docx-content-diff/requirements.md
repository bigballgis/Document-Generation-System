# Requirements Document

## Introduction

Implement real content comparison for `.docx` files. Today, segment version comparison (`SegmentVersionDialog.vue`) only compares metadata fields (`segmentType`, `configSnapshot`, `comment`) and file path changes; it cannot show actual text differences inside the `.docx`. This feature reads both version `.docx` files from MinIO on the backend, extracts plain text, runs a line-oriented diff, and renders a git-style red/green unified diff in the version comparison dialog.

## Glossary

- **Content_Diff_Service**: Backend service that reads `.docx` from MinIO, extracts plain text, and runs text diff
- **Docx_Text_Extractor**: Backend component that uses JDK `ZipInputStream` + SAX parser to extract readable plain text from `.docx` (ZIP/OpenXML) without pulling in Apache POI
- **Diff_Engine**: Backend component based on `java-diff-utils` (`io.github.java-diff-utils:java-diff-utils`) for line-by-line diff and structured `Content_Diff_Line` output
- **Content_Diff_Line**: One row in the diff result: change type (EQUAL / ADDED / REMOVED / MODIFIED), old and new line text
- **Segment_Version_Dialog**: Frontend component (`SegmentVersionDialog.vue`) for segment version history, compare, and rollback
- **Content_Diff_View**: Frontend area inside `Segment_Version_Dialog` that shows text diff with red/green styling
- **MinIO_Client**: Existing MinIO client used to read `.docx` streams

## Requirements

### Requirement 1: Extract plain text from `.docx`

**User Story:** As the backend, I need plain text from `.docx` stored in MinIO so I can diff it.

#### Acceptance Criteria

1. WHEN a valid `.docx` path in MinIO is provided, THE Docx_Text_Extractor SHALL read the file from MinIO, unzip it, and extract readable plain text from `word/document.xml` and optionally `word/header*.xml`, `word/footer*.xml` entries
2. WHEN extracting text from `.docx` XML, THE Docx_Text_Extractor SHALL use a SAX parser, concatenating all `<w:t>` text nodes within each `<w:p>` paragraph into one line (handling Word run-splitting so e.g. `{CustomerName}` may span multiple `<w:r>` elements)
3. WHEN extracting text, THE Docx_Text_Extractor SHALL keep paragraph boundaries by inserting a newline between distinct `<w:p>` elements, and SHALL strip empty paragraphs (no `<w:t>` content)
4. IF the path does not exist in MinIO, THEN THE Docx_Text_Extractor SHALL throw a BusinessException with error code SEGMENT_FILE_NOT_FOUND
5. IF the file is not a valid `.docx` (ZIP) or has no `word/document.xml`, THEN THE Docx_Text_Extractor SHALL throw a BusinessException with error code CONTENT_DIFF_EXTRACTION_FAILED
6. THE Docx_Text_Extractor SHALL NOT add Apache POI or other heavy dependencies; it SHALL use only JDK types (`ZipInputStream`, `SAXParser`)

### Requirement 2: Text diff

**User Story:** As the backend, I need a line-by-line diff of two plain-text bodies to produce structured diff rows.

#### Acceptance Criteria

1. WHEN two text strings are provided, THE Diff_Engine SHALL use `java-diff-utils` for a line diff and return a list of Content_Diff_Line entries
2. THE Diff_Engine SHALL classify each Content_Diff_Line as EQUAL (unchanged), ADDED (new only), REMOVED (old only), or MODIFIED (line changed, with both old and new text)
3. WHEN both strings are identical, THE Diff_Engine SHALL return a list where all entries have type EQUAL
4. WHEN one string is empty and the other is not, THE Diff_Engine SHALL return entries classified as all ADDED or all REMOVED as appropriate
5. WHEN both strings are empty, THE Diff_Engine SHALL return an empty list
6. EACH Content_Diff_Line SHALL include: `type` (EQUAL/ADDED/REMOVED/MODIFIED), `oldLineNumber` (nullable for ADDED), `newLineNumber` (nullable for REMOVED), `oldText` (nullable for ADDED), `newText` (nullable for REMOVED)

### Requirement 3: Segment version content-diff API

**User Story:** As a frontend developer, I need the existing segment version compare API to return `.docx` text diffs for the dialog.

#### Acceptance Criteria

1. THE Content_Diff_Service SHALL keep the existing `compareSegmentVersions` path `GET /{id}/segments/{segmentName}/versions/diff` without a new endpoint
2. THE endpoint SHALL accept optional query `includeContentDiff` (boolean, default false); WHEN true, THE response SHALL include content diff data
3. THE SegmentVersionDiffResult SHALL include `contentDiffs`: a list of Content_Diff_Line (empty when `includeContentDiff` is false or omitted)
4. THE SegmentVersionDiffResult SHALL include `contentChanged` (boolean) for whether text content differs (computed even when `includeContentDiff` is false)
5. IF either file cannot be read from MinIO during comparison, THEN THE Content_Diff_Service SHALL set `contentDiffs` to empty, `contentChanged` to false, log a warning, and SHALL NOT fail the whole compare
6. THE SegmentVersionDiffResult SHALL include `truncated` (boolean, default false) when the diff is truncated for size limits

### Requirement 4: Frontend content-diff view

**User Story:** As a template admin, I need to see real `.docx` text differences (git-style red/green) in the segment version dialog.

#### Acceptance Criteria

1. WHEN the user clicks "Compare" in the Segment_Version_Dialog, THE frontend SHALL call the API with `includeContentDiff=true`
2. WHEN `contentChanged` is true, THE Content_Diff_View SHALL render a unified diff below the metadata diff table
3. THE Content_Diff_View SHALL show REMOVED lines with red background and "-" prefix
4. THE Content_Diff_View SHALL show ADDED lines with green background and "+" prefix
5. THE Content_Diff_View SHALL show MODIFIED lines with old text on red and new text on green, side by side or stacked
6. THE Content_Diff_View SHALL show EQUAL lines with subtle gray background and space prefix for context
7. WHEN `contentChanged` is false, THE Content_Diff_View SHALL show a success state for no textual change (via i18n, e.g. "No content changes") instead of the diff view
8. WHEN `contentDiffs` is empty and `contentChanged` is false, THE Segment_Version_Dialog SHALL still show metadata diffs without error
9. WHEN `truncated` is true, THE Content_Diff_View SHALL show a warning that the diff was truncated

### Requirement 5: Diff view usability

**User Story:** As a template admin, I need the diff view to be readable so I can review changes efficiently.

#### Acceptance Criteria

1. THE Content_Diff_View SHALL use a monospace font for diff lines
2. THE Content_Diff_View SHALL show old and new line numbers per diff row
3. WHEN content exceeds the viewport, THE Content_Diff_View SHALL scroll vertically with max height 500px
4. WHEN there are more than 5 consecutive EQUAL lines, THE Content_Diff_View SHALL collapse them with a count (e.g. "... 12 unchanged lines ...") and click-to-expand
5. THE Content_Diff_View SHALL support i18n for all user-visible labels in zh-CN, zh-TW, and en-US

### Requirement 6: Performance and large files

**User Story:** As the system, I need reasonable latency and memory when processing large `.docx` files.

#### Acceptance Criteria

1. WHEN extracted text from one `.docx` exceeds 500KB, THE Docx_Text_Extractor SHALL truncate at the boundary and append a "[... content truncated ...]" marker
2. WHEN total diff lines exceed 2000, THE Content_Diff_Service SHALL truncate the diff and set `truncated` true
3. IF extraction or diff throws unexpectedly, THEN THE Content_Diff_Service SHALL log the error and degrade gracefully (empty `contentDiffs`, `contentChanged=false`) rather than failing the API

---

## WS-04-T03 amendment (English): Identical-text contract

**Requirement 2, acceptance criterion 3** above describes the internal line-diff primitive in simplified terms. The **canonical API contract** for compared DOCX bodies is:

1. **`computeContentDiff` / `contentDiffs` (segment version compare API)**  
   When extracted plain text for old and new files is **equal** (`String.equals`): return **`contentDiffs` as an empty list**, **`contentChanged=false`**. Do **not** emit `EQUAL` rows. `truncated` may still be `true` if either extractor truncated. The line diff algorithm is not run.

2. **`computeLineDiff` (package-private helper)**  
   - Both strings **empty** → **empty list**.  
   - Identical **non-empty** text (same line split) → **only `EQUAL` rows** (e.g. one line → one `EQUAL` row).

**Client rule:** Do not infer “no textual change” from `contentDiffs` alone; use **`contentChanged=false`** for identical bodies when content diff is enabled.

**Evidence:** `docs/audits/full-project-review-2026-04-26/19-docx-identical-diff-contract.md`, `ContentDiffServiceTest` (WS-04-T01).

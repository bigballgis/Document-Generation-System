# Implementation Plan: docx-content-diff

## Overview

Add real `.docx` text diff to the existing segment version compare flow. Backend: JDK `ZipInputStream` + `SAXParser` for text, `java-diff-utils` for diff. Frontend: unified diff UI in `SegmentVersionDialog`.

## Tasks

- [x] 1. Maven dependency and new DTO/record types
  - [x] 1.1 Add `io.github.java-diff-utils:java-diff-utils:4.15` to `backend/pom.xml`
    - _Requirements: 2.1_
  - [x] 1.2 Add `com.docgen.dto.ContentDiffLine` with `DiffType` enum (EQUAL/ADDED/REMOVED/MODIFIED), `oldLineNumber`, `newLineNumber`, `oldText`, `newText`
    - _Requirements: 2.6_
  - [x] 1.3 Add `com.docgen.dto.ExtractedText` record with `text` and `truncated`
    - _Requirements: 6.1_
  - [x] 1.4 Add `com.docgen.dto.ContentDiffResult` record with `lines` (List\<ContentDiffLine\>), `contentChanged`, `truncated`
    - _Requirements: 3.3, 3.4, 3.6_
  - [x] 1.5 Extend `SegmentVersionDiffResult` with `contentDiffs` (List\<ContentDiffLine\>), `contentChanged` (boolean), `truncated` (boolean) and accessors
    - _Requirements: 3.3, 3.4, 3.6_
  - [x] 1.6 Add `CONTENT_DIFF_EXTRACTION_FAILED` to `ErrorCode`
    - _Requirements: 1.5_

- [x] 2. DocxTextExtractor
  - [x] 2.1 Add `com.docgen.service.DocxTextExtractor` with `extractText(InputStream docxStream, int maxBytes)`
    - Walk ZIP with `ZipInputStream`; read `word/document.xml`, `word/header*.xml`, `word/footer*.xml`
    - Parse with `SAXParser`; listen on `<w:p>` and `<w:t>` for text
    - Concatenate `<w:t>` inside a paragraph to one line; newline between paragraphs; skip empty paragraphs
    - If over `maxBytes`, truncate and append `[... content truncated ...]`
    - If missing `word/document.xml` or invalid ZIP, throw `BusinessException(CONTENT_DIFF_EXTRACTION_FAILED)`
    - _Requirements: 1.1, 1.2, 1.3, 1.5, 1.6, 6.1_
  - [x] 2.2 Add `extractTextFromMinio(String filePath)`
    - Read stream from MinIO; call `extractText`
    - Missing file → `BusinessException(SEGMENT_FILE_NOT_FOUND)`
    - _Requirements: 1.1, 1.4_
  - [x] 2.3 Property test 1: SAX extraction preserves paragraph text
    - **Property 1: SAX extraction preserves paragraph text**
    - Use jqwik to generate random OpenXML fragments (including `<w:t>` split across `<w:r>`); verify extracted text preserves content and line breaks
    - **Validates: Requirements 1.2, 1.3**
  - [x] 2.4 Property test 6: extraction truncation boundary
    - **Property 6: Extraction truncation boundary**
    - Use jqwik to generate text over 500KB; verify `truncated=true` and length within cap plus marker
    - **Validates: Requirements 6.1**
  - [x] 2.5 DocxTextExtractor unit tests
    - Happy path, multi-paragraph multi-run doc, empty doc, non-docx error, huge file truncation
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 6.1_

- [x] 3. ContentDiffService
  - [x] 3.1 Add `com.docgen.service.ContentDiffService` with `computeContentDiff(String oldFilePath, String newFilePath)`
    - Use `DocxTextExtractor` for both sides
    - `DiffUtils.diff()` line diff
    - Map `Patch<String>` deltas to `ContentDiffLine` (INSERT→ADDED, DELETE→REMOVED, CHANGE→MODIFIED, unchanged→EQUAL)
    - If diff lines exceed 2000, truncate and set `truncated=true`
    - Propagate per-file `truncated` into the aggregate result
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 6.2_
  - [x] 3.2 Internal `computeLineDiff(String oldText, String newText)` wrapping java-diff-utils
    - Split on `\n`
    - Walk `Patch.getDeltas()` for `ContentDiffLine`; lines not in a delta → EQUAL
    - Correct `oldLineNumber` / `newLineNumber`
    - _Requirements: 2.1, 2.2, 2.6_
  - [x] 3.3 Property test 2: diff round-trip reconstruction
    - **Property 2: Diff round-trip reconstruction**
    - Random text pairs; verify old/new can be rebuilt from diff rows
    - **Validates: Requirements 2.1, 2.2, 2.4, 2.6**
  - [x] 3.4 Property test 3: self-diff identity
    - **Property 3: Self-diff identity**
    - Random text; diff against self → all EQUAL; row count matches line count
    - **Validates: Requirements 2.3**
  - [x] 3.5 Property test 4: `contentChanged` consistency
    - **Property 4: contentChanged flag consistency**
    - Random pairs; `contentChanged` is true iff texts differ
    - **Validates: Requirements 3.4**
  - [x] 3.6 Property test 7: diff line-count truncation
    - **Property 7: Diff line-count truncation**
    - Pairs producing more than 2000 diff lines → `truncated=true`, at most 2000 rows
    - **Validates: Requirements 6.2**
  - [x] 3.7 ContentDiffService unit tests
    - Normal diff, identical text, empty text, MinIO failure degradation, line cap
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 6.2, 6.3_

- [x] 4. SegmentVersionService and controller
  - [x] 4.1 Extend `SegmentVersionService.compareSegmentVersions` with `boolean includeContentDiff`
    - Always compute `contentChanged`
    - If `includeContentDiff=true`, fill `contentDiffs`
    - If false, return empty `contentDiffs`
    - try/catch around content diff; on error warn log and degrade (empty `contentDiffs`, `contentChanged=false`)
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 6.3_
  - [x] 4.2 `CompositeTemplateController.compareSegmentVersions` adds `@RequestParam(defaultValue = "false") boolean includeContentDiff`
    - _Requirements: 3.1, 3.2_
  - [x] 4.3 SegmentVersionService integration tests
    - `includeContentDiff` true/false; degradation
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 6.3_

- [x] 5. Checkpoint — backend verification
  - Ensure all tests pass; clarify open questions with the team.

- [x] 6. Frontend TypeScript and API
  - [x] 6.1 `ContentDiffLine` in `frontend/src/api/composite-templates.ts`
    - `type`, `oldLineNumber`, `newLineNumber`, `oldText`, `newText`
    - _Requirements: 2.6, 3.3_
  - [x] 6.2 Extend `SegmentVersionDiffResult` with `contentDiffs`, `contentChanged`, `truncated`
    - _Requirements: 3.3, 3.4, 3.6_
  - [x] 6.3 `compareSegmentVersions(..., includeContentDiff?)` default false; send as query param
    - _Requirements: 3.2_

- [x] 7. Frontend content-diff UI
  - [x] 7.1 `SegmentVersionDialog.vue` `handleCompare` passes `includeContentDiff: true`
    - _Requirements: 4.1_
  - [x] 7.2 Content-diff section in dialog
    - If `contentChanged`, render unified diff under metadata table
    - If not, show success state for no textual change (`el-tag type="success"` + i18n)
    - If `truncated`, `el-alert` at top
    - Show note: template text diff (not WYSIWYG), via i18n
    - _Requirements: 4.1, 4.2, 4.7, 4.8, 4.9_
  - [x] 7.3 Row rendering
    - REMOVED: red, "-"
    - ADDED: green, "+"
    - MODIFIED: old red, new green, stacked
    - EQUAL: light gray, space prefix; show old/new line numbers; monospace; max-height 500px scroll
    - _Requirements: 4.3, 4.4, 4.5, 4.6, 5.1, 5.2, 5.3_
  - [x] 7.4 Collapse more than 5 consecutive EQUAL lines; show collapsed count; click to expand
    - _Requirements: 5.4_
  - [x] 7.5 Property test 5 (fast-check): EQUAL-run collapse
    - **Property 5: Consecutive EQUAL-line collapse**
    - Random diff results; long EQUAL runs collapse; expand restores rows
    - **Validates: Requirements 5.4**
  - [x] 7.6 SegmentVersionDialog Vitest
    - `includeContentDiff=true`, `contentChanged` UI, `truncated` alert, collapse/expand
    - _Requirements: 4.1, 4.2, 4.7, 4.9, 5.4_

- [x] 8. i18n
  - [x] 8.1 Add `workspace.segment.contentDiff.*` to `zh-CN.json`, `zh-TW.json`, `en-US.json`
    - title, noChanges, truncatedWarning, textDiffNote, collapsedLines, expandLines, lineOld, lineNew
    - _Requirements: 5.5_

- [x] 9. Final checkpoint — end-to-end verification
  - Ensure all tests pass; clarify open questions with the team.

## Notes

- All tasks are required
- Each task references requirements for traceability
- Checkpoints support incremental validation
- Property tests encode design-level correctness properties
- Unit tests cover examples and edge cases
- Backend: Java 17 + Spring Boot 3.2; frontend: Vue 3 + TypeScript + Element Plus
- PBT: jqwik on backend, fast-check on frontend

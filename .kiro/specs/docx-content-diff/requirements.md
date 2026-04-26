# Requirements Document

## Introduction

实现 .docx 文件的真正内容对比功能。当前的片段版本比较（SegmentVersionDialog.vue）仅能比较元数据字段（segmentType、configSnapshot、comment）和文件路径变更，无法展示 .docx 文件的实际文本内容差异。本功能将在后端从 MinIO 读取两个版本的 .docx 文件、提取纯文本内容并执行逐段 diff 比较，前端在版本比较对话框中以类似 git diff 的红绿对比视图展示真正的内容差异。

## Glossary

- **Content_Diff_Service**: 后端服务，负责从 MinIO 读取 .docx 文件、提取纯文本内容并执行文本差异比较
- **Docx_Text_Extractor**: 后端组件，使用 JDK 内置 ZipInputStream + SAX Parser 从 .docx (ZIP/OpenXML) 文件中提取可读的纯文本内容，不引入 Apache POI 等重量级依赖
- **Diff_Engine**: 后端组件，基于 `java-diff-utils` 库（io.github.java-diff-utils:java-diff-utils）对两段纯文本执行逐行差异比较，生成结构化的 diff 结果
- **Content_Diff_Line**: 差异结果中的单行记录，包含变更类型（EQUAL / ADDED / REMOVED / MODIFIED）、旧行文本和新行文本
- **Segment_Version_Dialog**: 前端组件（SegmentVersionDialog.vue），展示片段版本历史、比较和回滚功能
- **Content_Diff_View**: 前端组件，在 Segment_Version_Dialog 中以红绿对比视图展示文本内容差异
- **MinIO_Client**: 已有的 MinIO 对象存储客户端，用于读取 .docx 文件流

## Requirements

### Requirement 1: 从 .docx 文件提取纯文本内容

**User Story:** 作为后端服务，我需要从 MinIO 存储的 .docx 文件中提取纯文本内容，以便后续进行文本差异比较。

#### Acceptance Criteria

1. WHEN a valid .docx file path in MinIO is provided, THE Docx_Text_Extractor SHALL read the file from MinIO, unzip it, and extract readable plain text content from `word/document.xml` and optionally `word/header*.xml`, `word/footer*.xml` entries
2. WHEN extracting text from .docx XML content, THE Docx_Text_Extractor SHALL use a SAX parser to traverse the XML DOM, concatenating all `<w:t>` text nodes within each `<w:p>` paragraph element into a single line (handling Word's run-splitting behavior where `{CustomerName}` may be split across multiple `<w:r>` elements)
3. WHEN extracting text, THE Docx_Text_Extractor SHALL preserve paragraph boundaries by inserting a newline character between distinct `<w:p>` elements, and SHALL strip empty paragraphs (paragraphs with no `<w:t>` content)
4. IF the specified file path does not exist in MinIO, THEN THE Docx_Text_Extractor SHALL throw a BusinessException with error code SEGMENT_FILE_NOT_FOUND
5. IF the file at the specified path is not a valid .docx (ZIP) file or contains no `word/document.xml`, THEN THE Docx_Text_Extractor SHALL throw a BusinessException with error code CONTENT_DIFF_EXTRACTION_FAILED
6. THE Docx_Text_Extractor SHALL NOT introduce Apache POI or other heavy dependencies; it SHALL use only JDK built-in classes (ZipInputStream, SAXParser)

### Requirement 2: 执行文本差异比较

**User Story:** 作为后端服务，我需要对两个版本的纯文本内容执行逐行差异比较，以便生成结构化的 diff 结果。

#### Acceptance Criteria

1. WHEN two text strings are provided, THE Diff_Engine SHALL use the `java-diff-utils` library to compute a line-by-line diff and return a list of Content_Diff_Line entries
2. THE Diff_Engine SHALL classify each Content_Diff_Line as one of four types: EQUAL (unchanged), ADDED (present only in new version), REMOVED (present only in old version), or MODIFIED (line content changed, providing both old and new text)
3. WHEN both text strings are identical, THE Diff_Engine SHALL return a list where all entries have type EQUAL
4. WHEN one text string is empty and the other is non-empty, THE Diff_Engine SHALL return entries classified as all ADDED or all REMOVED accordingly
5. WHEN both text strings are empty, THE Diff_Engine SHALL return an empty list
6. EACH Content_Diff_Line SHALL include: `type` (EQUAL/ADDED/REMOVED/MODIFIED), `oldLineNumber` (nullable for ADDED), `newLineNumber` (nullable for REMOVED), `oldText` (nullable for ADDED), `newText` (nullable for REMOVED)

### Requirement 3: 片段版本内容差异比较 API

**User Story:** 作为前端开发者，我需要通过现有的片段版本比较 API 获取 .docx 文件的文本内容差异，以便在版本对比对话框中展示。

#### Acceptance Criteria

1. THE Content_Diff_Service SHALL reuse the existing `compareSegmentVersions` endpoint path `GET /{id}/segments/{segmentName}/versions/diff` without introducing a new endpoint
2. THE endpoint SHALL accept an optional query parameter `includeContentDiff` (boolean, default false); WHEN set to true, THE response SHALL include content diff data
3. THE SegmentVersionDiffResult SHALL include a new field `contentDiffs` containing a list of Content_Diff_Line entries (empty list when `includeContentDiff` is false or not requested)
4. THE SegmentVersionDiffResult SHALL include a new field `contentChanged` as a boolean indicating whether the text content has changed between the two versions (always computed regardless of `includeContentDiff`)
5. IF either version file cannot be read from MinIO during comparison, THEN THE Content_Diff_Service SHALL set `contentDiffs` to an empty list and `contentChanged` to false, and log a warning instead of failing the entire comparison
6. THE SegmentVersionDiffResult SHALL include a new field `truncated` (boolean, default false) indicating whether the diff result was truncated due to size limits

### Requirement 4: 前端内容差异对比视图

**User Story:** 作为模板管理员，我需要在片段版本比较对话框中看到 .docx 文件的实际文本内容差异（类似 git diff 的红绿对比视图），以便了解两个版本之间的具体内容变更。

#### Acceptance Criteria

1. WHEN the user clicks "Compare" in the Segment_Version_Dialog, THE frontend SHALL call the comparison API with `includeContentDiff=true`
2. WHEN the comparison result contains `contentChanged` as true, THE Content_Diff_View SHALL render a unified diff view below the existing metadata diff table
3. THE Content_Diff_View SHALL display REMOVED lines with a red background and a "-" prefix
4. THE Content_Diff_View SHALL display ADDED lines with a green background and a "+" prefix
5. THE Content_Diff_View SHALL display MODIFIED lines with the old text in red background and new text in green background, side by side or stacked
6. THE Content_Diff_View SHALL display EQUAL lines with a subtle gray background and a space prefix for context
7. WHEN the comparison result contains `contentChanged` as false, THE Content_Diff_View SHALL display a "内容无差异" success indicator instead of the diff view
8. WHEN `contentDiffs` is empty and `contentChanged` is false, THE Segment_Version_Dialog SHALL continue to display the existing metadata diffs without error
9. WHEN `truncated` is true, THE Content_Diff_View SHALL display a warning indicator that the diff was truncated

### Requirement 5: 差异视图的可用性

**User Story:** 作为模板管理员，我需要内容差异视图具有良好的可读性和可用性，以便高效地审查版本变更。

#### Acceptance Criteria

1. THE Content_Diff_View SHALL use a monospace font for diff content to ensure alignment
2. THE Content_Diff_View SHALL display line numbers for both old and new versions alongside each diff line
3. WHEN the diff content exceeds the visible area, THE Content_Diff_View SHALL provide vertical scrolling with a maximum height of 500px
4. WHEN there are more than 5 consecutive EQUAL lines, THE Content_Diff_View SHALL collapse them showing a count indicator (e.g., "... 12 unchanged lines ...") with a click-to-expand action
5. THE Content_Diff_View SHALL include i18n support for all user-visible labels in zh-CN, zh-TW, and en-US

### Requirement 6: 性能与大文件处理

**User Story:** 作为系统，我需要在处理大型 .docx 文件时保持合理的响应时间和内存使用，以便确保系统稳定性。

#### Acceptance Criteria

1. WHEN the extracted text content from a single .docx file exceeds 500KB, THE Docx_Text_Extractor SHALL truncate the text at the 500KB boundary and append a "[... content truncated ...]" indicator
2. WHEN the total number of diff lines exceeds 2000, THE Content_Diff_Service SHALL truncate the diff result and set the `truncated` flag to true in the response
3. IF an unexpected error occurs during text extraction or diff computation, THEN THE Content_Diff_Service SHALL log the error and return a graceful degradation response with empty contentDiffs and contentChanged=false rather than failing the API call

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

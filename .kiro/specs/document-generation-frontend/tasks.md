# Implementation Plan: Document Generation Frontend

## Overview

This plan splits the document-generation frontend gap-fill into incremental tasks. Start with TypeScript types and API modules, then composables, pages, routes, and i18n. Each task builds on the last for verifiable increments. Stack: TypeScript + Vue 3 Composition API + Element Plus.

## Tasks

- [x] 1. TypeScript types
  - [x] 1.1 Add document-generation types
    - Add `frontend/src/types/document.ts`
    - Define: `GenerateDocumentRequest`, `GenerateDocumentResponse`, `SegmentRenderStat`, `BatchGenerateRequest`, `AsyncTaskDTO`, `TaskStatus`, `TaskProgress`, `TaskQuery`, `GeneratedDocumentDTO`, `DocumentQuery`, `MergeDocumentsRequest`, `ExpressionDTO`, `ExpressionType`, `CreateExpressionRequest`, `UpdateExpressionRequest`, `ValidateExpressionRequest`, `ExpressionValidationResult`
    - Field names match backend DTOs (camelCase); timestamps as `string`
    - _Requirements: 1.1, 2.1, 3.1, 4.1_

- [x] 2. API layer
  - [x] 2.1 Add generate API
    - Add `frontend/src/api/generate.ts`
    - Import `request` from `@/api/request`, types from `@/types/document`
    - `generateDocument(templateId, data?, version?)` → POST `/generate/{templateId}`
    - `generateDocumentAsync(templateId, data?)` → POST `/generate/{templateId}/async`
    - `generateDocumentBatch(templateId, data)` → POST `/generate/{templateId}/batch`
    - Mirror patterns from `templates.ts` / `segments.ts`
    - _Requirements: 1.2, 1.3, 1.4, 1.5_

  - [x] 2.2 Add tasks API
    - Add `frontend/src/api/tasks.ts`
    - `getTasks(query)` → GET `/tasks`, convert `page` to zero-based
    - `getTaskStatus(taskId)` → GET `/tasks/{taskId}`
    - `getTaskProgress(taskId)` → GET `/tasks/{taskId}/progress`
    - `downloadTaskResult(taskId)` → GET `/tasks/{taskId}/download` with `responseType: 'blob'` (browser download)
    - _Requirements: 2.3, 2.4, 2.5, 2.6_

  - [x] 2.3 Add documents API
    - Add `frontend/src/api/documents.ts`
    - `getDocuments(query)` → GET `/documents`, zero-based `page`
    - `getDocument(id)` → GET `/documents/{id}`
    - `downloadDocument(id)` → GET `/documents/{id}/download` with `responseType: 'blob'`
    - `mergeDocuments(data)` → POST `/documents/merge`
    - _Requirements: 3.2, 3.3, 3.4, 3.5_

  - [x] 2.4 Add expressions API
    - Add `frontend/src/api/expressions.ts`
    - `createExpression(templateId, data)` → POST `/templates/{templateId}/expressions`
    - `getExpressions(templateId)` → GET `/templates/{templateId}/expressions`
    - `updateExpression(id, data)` → PUT `/expressions/{id}`
    - `deleteExpression(id)` → DELETE `/expressions/{id}`
    - `validateExpression(data)` → POST `/expressions/validate`
    - _Requirements: 4.2, 4.3, 4.4, 4.5, 4.6_


- [x] 3. Backend — task list endpoint
  - [x] 3.1 Add GET /api/tasks on TaskController
    - Edit `backend/src/main/java/com/docgen/controller/TaskController.java`
    - New `@GetMapping` with optional query params: status (`String`), templateId (`Long`), `Pageable`
    - Use `AsyncDocumentService` to list current-tenant async tasks, `createdAt` DESC
    - Return `Page<AsyncTaskDTO>`
    - _Requirements: 2.2_

  - [x] 3.2 Add list method on AsyncDocumentService
    - Edit `backend/src/main/java/com/docgen/service/AsyncDocumentService.java`
    - Add `listTasks(String status, Long templateId, Pageable pageable)`
    - Query `AsyncTaskRepository` with optional status/templateId filters
    - _Requirements: 2.2_

- [x] 4. Composable — async task polling
  - [x] 4.1 Add `useTaskPolling`
    - Add `frontend/src/composables/useTaskPolling.ts`
    - Args: `taskId: Ref<string | null>`, `intervalMs = 3000`
    - When `taskId` changes, start polling `getTaskProgress`
    - Stop when status is COMPLETED or FAILED
    - Up to 3 consecutive poll failures → stop and set `error`
    - Clear `setInterval` in `onBeforeUnmount`
    - Return `{ task, progress, isPolling, error, start, stop }`
    - _Requirements: 6.3, 6.4_

- [x] 5. Checkpoint — types, API layer, backend endpoint
  - Keep tests green; ask the user if blocked.

- [x] 6. Frontend — generate dialog
  - [x] 6.1 Add GenerateDialog
    - Add `frontend/src/views/templates/components/GenerateDialog.vue`
    - Props: `visible: boolean`, `templateId: number`
    - Emits: `update:visible`, `generated`
    - Mode selector: sync / async / batch
    - JSON parameters textarea
    - Output format (Word / PDF / Both), storage strategy (Temporary / Persistent)
    - Batch: multi-line JSON data sets (max 1000), failure strategy, failure threshold
    - Sync → `generateDocument`, show result + download link
    - Async → `generateDocumentAsync`, show taskId + link to `/tasks`
    - Batch → `generateDocumentBatch`, navigate to `/tasks`
    - Errors → `ElMessage.error`
    - Copy: `document.*` and `common.*`
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7_

  - [x] 6.2 Wire GenerateDialog into template detail
    - Edit `frontend/src/views/templates/Detail.vue`
    - Add header "Generate" button
    - Use `v-model:visible` for dialog
    - _Requirements: 5.1_

- [x] 7. Frontend — expression panel
  - [x] 7.1 Add ExpressionFormDialog
    - Add `frontend/src/views/templates/components/ExpressionFormDialog.vue`
    - Props: `visible: boolean`, `templateId: number`, `data: ExpressionDTO | null`
    - Emits: `update:visible`, `saved`
    - Fields: Name (required, max 100), Type (JavaScript / Excel Formula), Expression Text (textarea), Description (optional), Execution Order (number, default 0)
    - Create → `createExpression`; edit → `updateExpression`
    - "Validate" → `validateExpression`, show success or error + position
    - Copy: `expression.*` and `common.*`
    - _Requirements: 8.3, 8.4, 8.5, 8.7, 8.8_

  - [x] 7.2 Add ExpressionPanel
    - Add `frontend/src/views/templates/components/ExpressionPanel.vue`
    - Props: `templateId: number`
    - Table: Name, Type, truncated Expression Text, Execution Order, Created At, Actions
    - Actions: Edit (dialog), Delete (`ElMessageBox.confirm` → `deleteExpression`), Validate
    - "Create Expression" opens dialog in create mode
    - Copy: `expression.*` and `common.*`
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 8.8_

  - [x] 7.3 Tab on template detail
    - Edit `frontend/src/views/templates/Detail.vue`
    - New `<el-tab-pane>` with label `$t('expression.title')`
    - Pass `templateId` into ExpressionPanel
    - _Requirements: 8.1_


- [x] 8. Frontend — task monitor page
  - [x] 8.1 Add tasks Index
    - Add `frontend/src/views/tasks/Index.vue`
    - Standard list layout: page-header + filter-card + table + pagination
    - Filters: Status (Pending / Running / Completed / Failed), Template ID
    - Columns: Task ID, Task Type, Template ID, Status (`el-tag`), Progress (`el-progress`), Created At, Completed At, Actions
    - Load with `getTasks`; refetch on filter change
    - RUNNING rows: `useTaskPolling` for live progress and counts
    - COMPLETED: "Download" → `downloadTaskResult`
    - FAILED: `errorMessage` in tooltip or expandable row
    - Copy: `task.*` and `common.*`
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7_

- [x] 9. Frontend — document history page
  - [x] 9.1 Add documents Index
    - Add `frontend/src/views/documents/Index.vue`
    - Standard list layout
    - Filters: Template ID, Status, date range
    - Columns: ID, Template ID, Format, Status, File Size, Page Count, Generated At, Expires At, Actions (Download)
    - On filter change: `getDocuments`, reset page to 1
    - Download → `downloadDocument`
    - Multi-select checkboxes → show "Merge"
    - Copy: `document.*` and `common.*`
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.7_

  - [x] 9.2 Add MergeDialog
    - Add `frontend/src/views/documents/components/MergeDialog.vue`
    - Props: `visible: boolean`, `documentIds: number[]`
    - Emits: `update:visible`, `merged`
    - Fields: Insert Page Breaks (switch), Generate TOC (switch), Output Format (DOCX / PDF)
    - Submit → `mergeDocuments`, on success emit `merged`
    - Copy: `document.*` and `common.*`
    - _Requirements: 7.6_

- [x] 10. Frontend — routes and navigation
  - [x] 10.1 Router
    - Edit `frontend/src/router/index.ts`
    - Under main layout: `/tasks` → `views/tasks/Index.vue`, meta `{ title: 'Tasks', requiresAuth: true }`
    - `/documents` → `views/documents/Index.vue`, meta `{ title: 'Documents', requiresAuth: true }`
    - _Requirements: 9.1, 9.2_

  - [x] 10.2 MainLayout menu
    - Edit `frontend/src/layouts/MainLayout.vue`
    - After Templates: Documents (`index="/documents"`, icon `Files`, title `$t('nav.documents')`)
    - Tasks (`index="/tasks"`, icon `Clock`, title `$t('nav.tasks')`)
    - Extend `navTitleMap` with `/tasks` and `/documents`
    - Import `Files`, `Clock` from `@element-plus/icons-vue`
    - _Requirements: 9.3, 9.4_

- [x] 11. Frontend — i18n
  - [x] 11.1 Locale files
    - `frontend/src/i18n/en-US.json`: add `document.*` (generateMode, modeSync, modeAsync, modeBatch, parameters, parametersHint, dataSets, dataSetsHint, failureStrategy, strategyContinue, strategyThreshold, failureThreshold, history, storageMode, storageTemporary, storagePersistent, generateSuccess, batchProgress, batchComplete, downloadZip, expiresAt, generatedAt, fileSize, merge, mergeOrder, insertPageBreak, generateToc, outputFormat, formatWord, formatPdf, formatBoth) and `task.*` (title, taskId, status, statusPending, statusRunning, statusCompleted, statusFailed, progress, createdAt, completedAt, errorMessage, downloadResult, viewProgress, pollingActive); fill `expression.executionOrder`, `expression.description`, `expression.confirmDelete`, `expression.errorPosition`; add `nav.tasks`, `nav.documents`
    - `zh-CN.json` / `zh-TW.json`: corresponding translations (keep existing `expression.*` where present)
    - Reuse `common.*` keys where possible
    - _Requirements: 10.1, 10.2, 10.3, 10.4_

- [x] 12. Checkpoint — components and routes
  - Keep tests green; ask the user if blocked.


- [x] 13. Frontend tests — PBT and unit tests
  - [x] 13.1 Property test — document query mapping
    - Add `frontend/src/__tests__/documents.property.test.ts`
    - **Property 1: Document query parameter mapping preserves all filter fields**
    - fast-check random `DocumentQuery` (optional fields present/absent)
    - Mock Axios; call `getDocuments(query)`; assert params match input; `page` zero-based
    - `fc.assert(fc.property(...), { numRuns: 100 })`
    - // Feature: document-generation-frontend, Property 1: Document query parameter mapping preserves all filter fields
    - **Validates: Requirements 3.2**

  - [x] 13.2 Property test — history filters → API
    - Add `frontend/src/__tests__/documentHistory.property.test.ts`
    - **Property 2: Document history filter-to-API mapping**
    - Random filter combos (templateId, status, date range)
    - Mount history view, trigger search, assert `getDocuments` args
    - `fc.assert(fc.property(...), { numRuns: 100 })`
    - // Feature: document-generation-frontend, Property 2: Document history filter-to-API mapping
    - **Validates: Requirements 7.4**

  - [x] 13.3 Property test — expression validation UI
    - Add `frontend/src/__tests__/expressionPanel.property.test.ts`
    - **Property 3: Expression validation result rendering**
    - Random `ExpressionValidationResult` (valid or invalid with message/position)
    - Mount ExpressionPanel; assert success vs error UI
    - `fc.assert(fc.property(...), { numRuns: 100 })`
    - // Feature: document-generation-frontend, Property 3: Expression validation result rendering
    - **Validates: Requirements 8.7**

  - [x] 13.4 API unit tests
    - `frontend/src/__tests__/api/generate.test.ts` — methods, URLs, payloads
    - `frontend/src/__tests__/api/tasks.test.ts` — list, status, progress, download
    - `frontend/src/__tests__/api/documents.test.ts` — list, get, download, merge
    - `frontend/src/__tests__/api/expressions.test.ts` — CRUD + validate
    - _Requirements: 1.2-1.5, 2.2-2.4, 3.2-3.5, 4.2-4.6_

  - [x] 13.5 useTaskPolling unit tests
    - Add `frontend/src/__tests__/composables/useTaskPolling.test.ts`
    - Start/stop; terminal states; 3 failures; unmount clears interval
    - _Requirements: 6.3, 6.4_

- [x] 14. Final checkpoint — all tests green
  - Keep tests green; ask the user if blocked.

## Notes

- Tasks trace to requirements IDs
- Checkpoints allow incremental validation
- Property tests cover universal properties from design
- Unit tests cover examples and edge cases
- Stack: TypeScript + Vue 3 Composition API + Element Plus
- API files follow `templates.ts` / `segments.ts`
- i18n keys: `{module}.{page}.{element}`

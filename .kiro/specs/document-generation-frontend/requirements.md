# Requirements Document

## Introduction

This requirements document defines the **document-generation-frontend** gap-fill: the backend already exposes full REST APIs (`GenerateController`, `TaskController`, `DocumentController`, `ExpressionController`), but the frontend lacked matching API modules, views, and components. This scope adds UI so operators can generate documents, monitor async jobs, browse download history, and manage expressions from the browser.

## Glossary

- **Generation_UI**: Generation dialog — choose template and run sync/async/batch generation
- **Task_Monitor**: Async task dashboard — running/completed jobs and progress
- **Document_History**: Generated-documents page — browse, filter, search, download
- **Expression_Panel**: Expression management tab on template detail — CRUD + validate
- **API_Layer**: Typed Axios wrappers per backend controller
- **Generate_API**: `frontend/src/api/generate.ts` — `GenerateController` endpoints
- **Task_API**: `frontend/src/api/tasks.ts` — `TaskController` endpoints
- **Document_API**: `frontend/src/api/documents.ts` — `DocumentController` endpoints
- **Expression_API**: `frontend/src/api/expressions.ts` — `ExpressionController` endpoints
- **i18n_Keys**: vue-i18n keys — all user-visible strings via `$t()`

## Requirements

### Requirement 1: Document Generation API Layer

**User Story:** As a frontend developer, I want a typed API layer for document generation endpoints, so that Vue components can call GenerateController APIs with full TypeScript type safety.

#### Acceptance Criteria

1. THE Generate_API SHALL export TypeScript interfaces matching backend DTOs: `GenerateDocumentRequest` (parameters: `Record<string, unknown>`, outputFormat: `string`, storageStrategy: `string`), `GenerateDocumentResponse` (documentId, templateId, format, storageStrategy, downloadUrl, fileSize, generatedAt, content, secondaryDocument, segmentRenderStats, totalRenderTimeMs), `BatchGenerateRequest` (dataSets: `Array<Record<string, unknown>>`, outputFormat, storageStrategy, failureStrategy, failureThreshold), and `AsyncTaskDTO` (taskId, taskType, templateId, status, progress, totalCount, completedCount, successCount, failCount, documentId, errorMessage, downloadUrl, createdAt, completedAt)
2. THE Generate_API SHALL export function `generateDocument(templateId: number, request: GenerateDocumentRequest, version?: number)` that sends POST to `/generate/{templateId}` with optional `version` query parameter and returns `GenerateDocumentResponse`
3. THE Generate_API SHALL export function `generateDocumentAsync(templateId: number, request: GenerateDocumentRequest)` that sends POST to `/generate/{templateId}/async` and returns `AsyncTaskDTO`
4. THE Generate_API SHALL export function `generateDocumentBatch(templateId: number, request: BatchGenerateRequest)` that sends POST to `/generate/{templateId}/batch` and returns `AsyncTaskDTO`
5. THE Generate_API SHALL import the shared Axios instance from `@/api/request` and follow the same coding pattern as existing API files (`frontend/src/api/templates.ts`, `frontend/src/api/segments.ts`)

### Requirement 2: Async Task API Layer

**User Story:** As a frontend developer, I want a typed API layer for async task endpoints, so that the task monitoring UI can query task status, progress, and download results.

#### Acceptance Criteria

1. THE Task_API SHALL export TypeScript interfaces: `TaskProgress` with fields: taskId (`string`), status (`string`), progress (`number`), totalCount (`number`), completedCount (`number`), successCount (`number`), failCount (`number`); and `TaskQuery` with fields: status? (`string`), templateId? (`number`), page (`number`), size (`number`)
2. THE Backend TaskController SHALL provide a new endpoint `GET /api/tasks` that accepts optional query parameters (status, templateId, page, size) and returns `Page<AsyncTaskDTO>` listing async tasks for the current tenant, ordered by createdAt descending
3. THE Task_API SHALL export function `getTasks(query: TaskQuery)` that sends GET to `/tasks` with query parameters and returns `PageResult<AsyncTaskDTO>`
4. THE Task_API SHALL export function `getTaskStatus(taskId: string)` that sends GET to `/tasks/{taskId}` and returns `AsyncTaskDTO`
5. THE Task_API SHALL export function `getTaskProgress(taskId: string)` that sends GET to `/tasks/{taskId}/progress` and returns `TaskProgress`
6. THE Task_API SHALL export function `downloadTaskResult(taskId: string)` that sends GET to `/tasks/{taskId}/download` with `responseType: 'blob'` and triggers a browser file download

### Requirement 3: Document History API Layer

**User Story:** As a frontend developer, I want a typed API layer for document history endpoints, so that the document history page can list, filter, and download generated documents.

#### Acceptance Criteria

1. THE Document_API SHALL export TypeScript interfaces: `GeneratedDocumentDTO` (id, templateId, filePath, format, status, storageStrategy, fileSize, pageCount, downloadUrl, generatedAt, expiresAt), `DocumentQuery` (templateId?: `number`, status?: `string`, startTime?: `string`, endTime?: `string`, page: `number`, size: `number`), and `MergeDocumentsRequest` (documentIds: `number[]`, insertPageBreaks: `boolean`, generateToc: `boolean`, outputFormat: `string`)
2. THE Document_API SHALL export function `getDocuments(query: DocumentQuery)` that sends GET to `/documents` with query parameters and returns `PageResult<GeneratedDocumentDTO>`
3. THE Document_API SHALL export function `getDocument(id: number)` that sends GET to `/documents/{id}` and returns `GeneratedDocumentDTO`
4. THE Document_API SHALL export function `downloadDocument(id: number)` that sends GET to `/documents/{id}/download` with `responseType: 'blob'` and triggers a browser file download
5. THE Document_API SHALL export function `mergeDocuments(request: MergeDocumentsRequest)` that sends POST to `/documents/merge` and returns `GeneratedDocumentDTO`

### Requirement 4: Expression API Layer

**User Story:** As a frontend developer, I want a typed API layer for expression endpoints, so that the expression management panel can perform CRUD and validation operations.

#### Acceptance Criteria

1. THE Expression_API SHALL export TypeScript interfaces: `ExpressionDTO` (id, templateId, name, expressionType, expressionText, description, executionOrder, createdAt), `CreateExpressionRequest` (name, expressionType, expressionText, description?, executionOrder?), `UpdateExpressionRequest` (name?, expressionType?, expressionText?, description?, executionOrder?), `ValidateExpressionRequest` (expression, expressionType), and `ExpressionValidationResult` (valid: `boolean`, errorMessage?: `string`, errorPosition?: `number`)
2. THE Expression_API SHALL export function `createExpression(templateId: number, data: CreateExpressionRequest)` that sends POST to `/templates/{templateId}/expressions` and returns `ExpressionDTO`
3. THE Expression_API SHALL export function `getExpressions(templateId: number)` that sends GET to `/templates/{templateId}/expressions` and returns `ExpressionDTO[]`
4. THE Expression_API SHALL export function `updateExpression(id: number, data: UpdateExpressionRequest)` that sends PUT to `/expressions/{id}` and returns `ExpressionDTO`
5. THE Expression_API SHALL export function `deleteExpression(id: number)` that sends DELETE to `/expressions/{id}`
6. THE Expression_API SHALL export function `validateExpression(data: ValidateExpressionRequest)` that sends POST to `/expressions/validate` and returns `ExpressionValidationResult`

### Requirement 5: Document Generation UI

**User Story:** As a document operator, I want a generation dialog accessible from the template detail page, so that I can generate documents by selecting a template, choosing generation mode (sync/async/batch), providing parameters, and selecting output format.

#### Acceptance Criteria

1. WHEN the user clicks a "Generate" button on the template detail page, THE Generation_UI SHALL open a dialog containing: a generation mode selector (sync, async, batch), a JSON parameter editor for inputting runtime parameters, an output format selector (Word, PDF, Both), and a storage strategy selector (Temporary, Persistent)
2. WHEN the user selects "sync" mode and submits the form, THE Generation_UI SHALL call `generateDocument` from Generate_API and display the generation result with a download link
3. WHEN the user selects "async" mode and submits the form, THE Generation_UI SHALL call `generateDocumentAsync` from Generate_API, display the returned task ID, and provide a link to navigate to the Task_Monitor page
4. WHEN the user selects "batch" mode, THE Generation_UI SHALL display a data sets editor allowing the user to input multiple JSON data sets (up to 1000), a failure strategy selector (Continue, Threshold), and a failure threshold input field
5. WHEN the user submits a batch generation request, THE Generation_UI SHALL call `generateDocumentBatch` from Generate_API and navigate to the Task_Monitor page showing the batch task progress
6. IF the Generate_API returns an error, THEN THE Generation_UI SHALL display the error message using Element Plus `ElMessage.error` notification
7. THE Generation_UI SHALL use i18n_Keys for all user-visible text with the `document.*` and `common.*` key prefixes

### Requirement 6: Async Task Monitoring Page

**User Story:** As a document operator, I want a task monitoring page, so that I can view all running and completed async tasks, track their progress in real-time, and download results when tasks complete.

#### Acceptance Criteria

1. THE Task_Monitor SHALL be accessible via route `/tasks` and registered in `frontend/src/router/index.ts` as a child of the main layout
2. THE Task_Monitor SHALL display a paginated table listing async tasks with columns: Task ID, Task Type, Template ID, Status, Progress, Created At, Completed At, and Actions, loaded via `getTasks` from Task_API
3. THE Task_Monitor SHALL provide filter controls: a status dropdown (Pending, Running, Completed, Failed) and a template ID input, calling `getTasks` with filter parameters on change
4. WHILE a task has status "RUNNING", THE Task_Monitor SHALL poll `getTaskProgress` from Task_API every 3 seconds and update the progress bar and counters (completed/total, success/fail) in real-time
5. WHEN a task reaches status "COMPLETED", THE Task_Monitor SHALL stop polling and display a "Download" button in the Actions column
6. WHEN the user clicks the "Download" button, THE Task_Monitor SHALL call `downloadTaskResult` from Task_API to trigger a browser file download
7. IF a task has status "FAILED", THEN THE Task_Monitor SHALL display the error message in a tooltip or expandable row detail
8. THE Task_Monitor SHALL use i18n_Keys for all user-visible text with the `task.*` and `common.*` key prefixes

### Requirement 7: Generated Document History Page

**User Story:** As a document operator, I want a document history page, so that I can browse, search, filter, and download all previously generated documents.

#### Acceptance Criteria

1. THE Document_History SHALL be accessible via route `/documents` and registered in `frontend/src/router/index.ts` as a child of the main layout
2. THE Document_History SHALL display a paginated table with columns: ID, Template ID, Format, Status, File Size, Page Count, Generated At, Expires At, and Actions (Download)
3. THE Document_History SHALL provide filter controls: a template ID input, a status dropdown (all statuses), and a date range picker for filtering by generation time
4. WHEN the user applies filters, THE Document_History SHALL call `getDocuments` from Document_API with the filter parameters and refresh the table
5. WHEN the user clicks "Download" on a document row, THE Document_History SHALL call `downloadDocument` from Document_API to trigger a browser file download
6. THE Document_History SHALL provide a "Merge Documents" feature: the user selects multiple documents via checkboxes, clicks a "Merge" button, configures merge options (page breaks, TOC, output format) in a dialog, and THE Document_History SHALL call `mergeDocuments` from Document_API
7. THE Document_History SHALL use i18n_Keys for all user-visible text with the `document.*` and `common.*` key prefixes

### Requirement 8: Expression Management Panel

**User Story:** As a template designer, I want an expression management panel integrated into the template detail page, so that I can create, edit, validate, and delete expressions (JavaScript or Excel formula) bound to a template.

#### Acceptance Criteria

1. THE Expression_Panel SHALL be rendered as a tab within the existing template detail page (`frontend/src/views/templates/Detail.vue`), labeled with i18n key `expression.title`
2. THE Expression_Panel SHALL display a list of expressions for the current template, showing columns: Name, Type (JavaScript/Excel), Expression Text (truncated), Execution Order, Created At, and Actions (Edit, Delete, Validate)
3. WHEN the user clicks "Create Expression", THE Expression_Panel SHALL open a dialog with form fields: Name (required, max 100 chars), Type (dropdown: JavaScript, Excel Formula), Expression Text (required, code editor textarea), Description (optional), and Execution Order (number, default 0)
4. WHEN the user submits the create form, THE Expression_Panel SHALL call `createExpression` from Expression_API and refresh the expression list
5. WHEN the user clicks "Edit" on an expression row, THE Expression_Panel SHALL open the same dialog pre-filled with the expression data, and on submit call `updateExpression` from Expression_API
6. WHEN the user clicks "Delete" on an expression row, THE Expression_Panel SHALL show a confirmation dialog, and on confirm call `deleteExpression` from Expression_API
7. WHEN the user clicks "Validate" on an expression row or within the create/edit dialog, THE Expression_Panel SHALL call `validateExpression` from Expression_API and display the validation result: a success indicator when valid, or the error message and error position when invalid
8. THE Expression_Panel SHALL use i18n_Keys for all user-visible text with the `expression.*` and `common.*` key prefixes

### Requirement 9: Navigation and Route Integration

**User Story:** As a user, I want navigation entries for the new pages, so that I can access task monitoring and document history from the sidebar menu.

#### Acceptance Criteria

1. THE Router SHALL register route `/tasks` pointing to `frontend/src/views/tasks/Index.vue` with meta title "Tasks" and `requiresAuth: true`
2. THE Router SHALL register route `/documents` pointing to `frontend/src/views/documents/Index.vue` with meta title "Documents" and `requiresAuth: true`
3. THE Navigation sidebar SHALL include menu items for "Tasks" and "Documents" using i18n keys `nav.tasks` and `nav.documents`
4. THE i18n_Keys SHALL be added to all three language files (en-US.json, zh-CN.json, zh-TW.json) for the `nav.tasks` and `nav.documents` entries

### Requirement 10: Internationalization Completeness

**User Story:** As a user in a multilingual environment, I want all new UI text to be available in English, Simplified Chinese, and Traditional Chinese, so that I can use the system in my preferred language.

#### Acceptance Criteria

1. THE i18n_Keys SHALL include translations for all `document.*`, `task.*`, `expression.*`, and `nav.tasks`/`nav.documents` keys in `frontend/src/i18n/en-US.json`
2. THE i18n_Keys SHALL include translations for all `document.*`, `task.*`, `expression.*`, and `nav.tasks`/`nav.documents` keys in `frontend/src/i18n/zh-CN.json`
3. THE i18n_Keys SHALL include translations for all `document.*`, `task.*`, `expression.*`, and `nav.tasks`/`nav.documents` keys in `frontend/src/i18n/zh-TW.json`
4. THE i18n_Keys SHALL reuse existing common keys (`common.confirm`, `common.cancel`, `common.delete`, `common.download`, etc.) where applicable, and only add new keys for domain-specific text

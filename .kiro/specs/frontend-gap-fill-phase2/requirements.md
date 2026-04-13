# Requirements Document

## Introduction

本需求文档定义了文档生成系统前端补全第二期（frontend-gap-fill-phase2）。第一期（document-generation-frontend）已补全了文档生成、异步任务、文档历史和表达式管理的前端功能。本期覆盖剩余所有后端 API 已存在但前端缺失对应 UI、API 调用层或视图的功能模块，包括：模板导入导出、Webhook 管理、速率限制与用量统计、模板审核补全、模板状态机补全、密码重置、测试用例导入导出、覆盖率导出、变量扫描。

## Glossary

- **ImportExport_API**: 前端 API 文件 `frontend/src/api/import-export.ts`，封装 ImportExportController 的所有端点
- **Webhook_API**: 前端 API 文件 `frontend/src/api/webhooks.ts`，封装 WebhookController 的所有端点
- **RateLimit_API**: 前端 API 文件 `frontend/src/api/rate-limits.ts`，封装 RateLimitController 的所有端点
- **WebhookPanel**: Webhook 管理面板组件，集成在模板详情页中作为标签页
- **WebhookFormDialog**: Webhook 创建/编辑对话框组件
- **WebhookLogDialog**: Webhook 日志查看对话框组件
- **RateLimitPanel**: 速率限制与用量统计面板，集成在管理页面中
- **ResetPasswordDialog**: 密码重置对话框或页面
- **VariableScanButton**: 变量扫描按钮，集成在变量管理面板中
- **i18n_Keys**: 国际化翻译键，所有用户可见文本必须使用 vue-i18n 的 `$t()` 函数引用
- **Template_Detail**: 模板详情页 `frontend/src/views/templates/Detail.vue`

## Requirements

### Requirement 1: Template Import/Export API Layer

**User Story:** As a frontend developer, I want a typed API layer for template import/export endpoints, so that Vue components can call ImportExportController APIs with full TypeScript type safety.

#### Acceptance Criteria

1. THE ImportExport_API SHALL export function `importDocx(file: File)` that sends a multipart POST to `/templates/import` and returns `TemplateDTO`
2. THE ImportExport_API SHALL export function `exportDocx(templateId: number)` that sends GET to `/templates/{templateId}/export` with `responseType: 'blob'` and triggers a browser file download
3. THE ImportExport_API SHALL export function `exportConfig(templateId: number)` that sends GET to `/templates/{templateId}/export-config` with `responseType: 'blob'` and triggers a browser file download
4. THE ImportExport_API SHALL export function `importConfig(file: File)` that sends a multipart POST to `/templates/import-config` and returns `TemplateDTO`
5. THE ImportExport_API SHALL import the shared Axios instance from `@/api/request` and follow the same coding pattern as existing API files

### Requirement 2: Template Import/Export UI

**User Story:** As a template manager, I want import and export buttons on the template list and detail pages, so that I can import .docx files as new templates, export templates as .docx, and import/export full template configurations as JSON.

#### Acceptance Criteria

1. WHEN the user clicks "Import Template" on the template list page, THE Template_List SHALL open a file upload dialog accepting `.docx` files, call `importDocx` from ImportExport_API, and refresh the template list on success
2. WHEN the user clicks "Import Configuration" on the template list page, THE Template_List SHALL open a file upload dialog accepting `.json` files, call `importConfig` from ImportExport_API, and refresh the template list on success
3. WHEN the user clicks "Export as .docx" on the template detail page, THE Template_Detail SHALL call `exportDocx` from ImportExport_API to trigger a browser file download
4. WHEN the user clicks "Export Configuration" on the template detail page, THE Template_Detail SHALL call `exportConfig` from ImportExport_API to trigger a browser file download
5. IF the ImportExport_API returns an error, THEN THE Template_List or Template_Detail SHALL display the error message using Element Plus `ElMessage.error`
6. THE import/export UI elements SHALL use i18n_Keys with the `template.*` and `common.*` key prefixes

### Requirement 3: Webhook Management API Layer

**User Story:** As a frontend developer, I want a typed API layer for webhook endpoints, so that Vue components can perform CRUD operations on webhook configurations and query webhook logs.

#### Acceptance Criteria

1. THE Webhook_API SHALL export TypeScript interfaces: `WebhookConfigDTO` (id: `number`, templateId: `number`, url: `string`, payloadTemplate: `string`, enabled: `boolean`, createdAt: `string`, updatedAt: `string`), `CreateWebhookRequest` (url: `string`, secret: `string`, payloadTemplate?: `string`), `UpdateWebhookRequest` (url?: `string`, secret?: `string`, payloadTemplate?: `string`, enabled?: `boolean`), and `WebhookLogDTO` (id: `number`, webhookConfigId: `number`, eventType: `string`, payload: `string`, responseStatus: `number`, responseBody: `string`, sentAt: `string`)
2. THE Webhook_API SHALL export function `createWebhook(templateId: number, data: CreateWebhookRequest)` that sends POST to `/templates/{templateId}/webhooks` and returns `WebhookConfigDTO`
3. THE Webhook_API SHALL export function `listWebhooks(templateId: number)` that sends GET to `/templates/{templateId}/webhooks` and returns `WebhookConfigDTO[]`
4. THE Webhook_API SHALL export function `updateWebhook(webhookId: number, data: UpdateWebhookRequest)` that sends PUT to `/webhooks/{webhookId}` and returns `WebhookConfigDTO`
5. THE Webhook_API SHALL export function `deleteWebhook(webhookId: number)` that sends DELETE to `/webhooks/{webhookId}`
6. THE Webhook_API SHALL export function `getWebhookLogs(webhookId: number, params: { page: number; size: number })` that sends GET to `/webhooks/{webhookId}/logs` and returns `PageResult<WebhookLogDTO>`

### Requirement 4: Webhook Management UI

**User Story:** As a template manager, I want a webhook management panel in the template detail page, so that I can create, edit, enable/disable, and delete webhooks, and view webhook delivery logs.

#### Acceptance Criteria

1. THE WebhookPanel SHALL be rendered as a tab within the existing template detail page, labeled with i18n key `webhook.title`
2. THE WebhookPanel SHALL display a list of webhooks for the current template, showing columns: URL, Status (enabled/disabled), Payload Template (truncated), Last Triggered, Created At, and Actions (Edit, Delete, Logs)
3. WHEN the user clicks "Create Webhook", THE WebhookPanel SHALL open WebhookFormDialog with form fields: URL (required), Secret (required), Payload Template (optional textarea), and Enabled toggle (default true)
4. WHEN the user submits the create form, THE WebhookPanel SHALL call `createWebhook` from Webhook_API and refresh the webhook list
5. WHEN the user clicks "Edit" on a webhook row, THE WebhookPanel SHALL open WebhookFormDialog pre-filled with the webhook data, and on submit call `updateWebhook` from Webhook_API
6. WHEN the user clicks "Delete" on a webhook row, THE WebhookPanel SHALL show a confirmation dialog, and on confirm call `deleteWebhook` from Webhook_API
7. WHEN the user clicks "Logs" on a webhook row, THE WebhookPanel SHALL open WebhookLogDialog displaying a paginated table with columns: Event Type, Payload (truncated), Response Status, Response Body (truncated), Sent At
8. THE WebhookPanel, WebhookFormDialog, and WebhookLogDialog SHALL use i18n_Keys with the `webhook.*` and `common.*` key prefixes

### Requirement 5: Rate Limit & Usage Stats API Layer

**User Story:** As a frontend developer, I want a typed API layer for rate limit and usage statistics endpoints, so that the admin panel can display current rate limit status and tenant usage data.

#### Acceptance Criteria

1. THE RateLimit_API SHALL export TypeScript interfaces: `RateLimitStatusDTO` (apiKeyId: `number`, apiKeyName: `string`, limitPerSecond: `number`, limitPerMinute: `number`, limitPerHour: `number`, remainingPerSecond: `number`, remainingPerMinute: `number`, remainingPerHour: `number`) and `UsageStatsDTO` (tenantId: `number`, monthlyQuota: `number`, currentMonthUsage: `number`, remainingQuota: `number`, resetAt: `string`)
2. THE RateLimit_API SHALL export function `getRateLimits()` that sends GET to `/rate-limits` and returns `RateLimitStatusDTO[]`
3. THE RateLimit_API SHALL export function `getUsageStats()` that sends GET to `/usage-stats` and returns `UsageStatsDTO`

### Requirement 6: Rate Limit & Usage Stats UI

**User Story:** As a tenant administrator, I want a rate limit and usage statistics panel in the admin page, so that I can monitor API key rate limit consumption and overall tenant usage.

#### Acceptance Criteria

1. THE RateLimitPanel SHALL be rendered as a section or tab within the existing admin page (`frontend/src/views/admin/Index.vue`), labeled with i18n key `admin.rateLimits`
2. THE RateLimitPanel SHALL display a table of rate limit statuses for all API keys, showing columns: API Key Name, Limit/Second, Remaining/Second, Limit/Minute, Remaining/Minute, Limit/Hour, Remaining/Hour, loaded via `getRateLimits` from RateLimit_API
3. THE RateLimitPanel SHALL display a usage statistics summary card showing: Monthly Quota, Current Month Usage, Remaining Quota, and Reset Date, loaded via `getUsageStats` from RateLimit_API
4. WHEN the user clicks a "Refresh" button, THE RateLimitPanel SHALL reload both rate limit statuses and usage statistics
5. THE RateLimitPanel SHALL use i18n_Keys with the `admin.rateLimit.*` and `common.*` key prefixes

### Requirement 7: Template Review Completion — API Layer

**User Story:** As a frontend developer, I want the missing review API functions added, so that the frontend can submit templates for review, list template-specific reviews, conditionally approve reviews, and open the OnlyOffice review editor.

#### Acceptance Criteria

1. THE admin.ts API file SHALL export function `submitForReview(templateId: number, data: { reviewerIds: number[]; reviewLevel?: number })` that sends POST to `/templates/{templateId}/reviews` and returns the created review records
2. THE admin.ts API file SHALL export function `getTemplateReviews(templateId: number, params: { page: number; size: number })` that sends GET to `/templates/{templateId}/reviews` and returns `PageResult<ReviewDTO>`
3. THE admin.ts API file SHALL export function `conditionalApproveReview(reviewId: number, data: { comment?: string; suggestions: string[] })` that sends PUT to `/reviews/{reviewId}/conditional-approve` and returns `ReviewDTO`
4. THE admin.ts API file SHALL export function `getReviewEditorUrl(templateId: number)` that sends GET to `/templates/{templateId}/reviews/editor-url` and returns the OnlyOffice review editor URL as a string

### Requirement 8: Template Review Completion — UI

**User Story:** As a template reviewer, I want to submit templates for review, view review history per template, conditionally approve reviews with suggestions, and open the OnlyOffice review editor, so that the full review workflow is accessible from the frontend.

#### Acceptance Criteria

1. WHEN the user clicks "Submit for Review" on the template detail page, THE Template_Detail SHALL open a dialog allowing the user to select reviewers and review level, then call `submitForReview` from admin.ts API
2. THE Template_Detail SHALL display a "Reviews" tab showing a paginated list of reviews for the current template, loaded via `getTemplateReviews` from admin.ts API, with columns: Reviewer, Status, Level, Comment, Suggestions, Created At
3. WHEN the user clicks "Conditional Approve" on a review row, THE Template_Detail SHALL open a dialog with fields for comment and suggestions list, then call `conditionalApproveReview` from admin.ts API
4. WHEN the user clicks "Review in Editor" on the template detail page, THE Template_Detail SHALL call `getReviewEditorUrl` from admin.ts API and open the returned URL in a new browser tab
5. THE review UI elements SHALL use i18n_Keys with the `review.*` and `common.*` key prefixes

### Requirement 9: Template Controller Completion — API Layer

**User Story:** As a frontend developer, I want the missing template state machine API functions added, so that the frontend can submit templates for review via the state machine and query available state transitions.

#### Acceptance Criteria

1. THE templates.ts API file SHALL export function `submitReview(templateId: number)` that sends POST to `/templates/{templateId}/submit-review` and returns `TemplateDTO`
2. THE templates.ts API file SHALL export function `getAvailableTransitions(templateId: number)` that sends GET to `/templates/{templateId}/available-transitions` and returns `string[]` representing available target states

### Requirement 10: Template State Transitions UI

**User Story:** As a template manager, I want the template detail page to show available state transitions and allow me to trigger them, so that I can manage the template lifecycle without guessing which transitions are valid.

#### Acceptance Criteria

1. WHEN the template detail page loads, THE Template_Detail SHALL call `getAvailableTransitions` from templates.ts API and display the available transitions as action buttons (Submit for Review, Activate, Archive) based on the returned states
2. WHILE no transitions are available for the current template state, THE Template_Detail SHALL disable or hide the state transition buttons
3. WHEN the user clicks a state transition button, THE Template_Detail SHALL call the corresponding API function (`submitReview`, `activateTemplate`, or `archiveTemplate`) and refresh the template data on success
4. THE state transition buttons SHALL use i18n_Keys with the `template.*` key prefix

### Requirement 11: Auth Completion — Password Reset API

**User Story:** As a frontend developer, I want a password reset API function, so that the frontend can call the reset-password endpoint.

#### Acceptance Criteria

1. THE auth.ts API file SHALL export function `resetPassword(email: string)` that sends POST to `/auth/reset-password` with body `{ email }` and returns void

### Requirement 12: Auth Completion — Password Reset UI

**User Story:** As a user who has forgotten their password, I want a password reset option on the login page, so that I can request a password reset link by entering my email address.

#### Acceptance Criteria

1. THE Login page SHALL display a "Forgot Password?" link below the login form
2. WHEN the user clicks "Forgot Password?", THE Login page SHALL open a ResetPasswordDialog with an email input field and a submit button
3. WHEN the user submits the email, THE ResetPasswordDialog SHALL call `resetPassword` from auth.ts API
4. WHEN the API call succeeds, THE ResetPasswordDialog SHALL display a success message indicating that a reset link has been sent to the provided email
5. IF the API call fails, THEN THE ResetPasswordDialog SHALL display the error message using Element Plus `ElMessage.error`
6. THE ResetPasswordDialog SHALL use i18n_Keys with the `auth.*` key prefix

### Requirement 13: Test Case Import/Export — API Layer

**User Story:** As a frontend developer, I want test case import/export API functions, so that the frontend can export and import test case data as JSON.

#### Acceptance Criteria

1. THE market.ts API file SHALL export function `exportTestCases(templateId: number)` that sends GET to `/templates/{templateId}/test-cases/export` and returns the JSON string
2. THE market.ts API file SHALL export function `importTestCases(templateId: number, json: string)` that sends POST to `/templates/{templateId}/test-cases/import` with the JSON string as request body and returns `TestCaseDTO[]`

### Requirement 14: Test Case Import/Export UI

**User Story:** As a template tester, I want import and export buttons in the test case panel, so that I can export test cases as JSON for backup or sharing, and import test cases from a JSON file.

#### Acceptance Criteria

1. WHEN the user clicks "Export Test Cases" in the test case panel on the template detail page, THE Template_Detail SHALL call `exportTestCases` from market.ts API and trigger a browser download of the JSON file
2. WHEN the user clicks "Import Test Cases" in the test case panel, THE Template_Detail SHALL open a file upload dialog accepting `.json` files, read the file content, call `importTestCases` from market.ts API, and refresh the test case list on success
3. IF the import API returns an error, THEN THE Template_Detail SHALL display the error message using Element Plus `ElMessage.error`
4. THE import/export buttons SHALL use i18n_Keys with the `test.*` and `common.*` key prefixes

### Requirement 15: Coverage Report Export — API Layer

**User Story:** As a frontend developer, I want a coverage report export API function, so that the frontend can download coverage reports as JSON files.

#### Acceptance Criteria

1. THE templates.ts API file SHALL export function `exportCoverageReport(templateId: number)` that sends GET to `/templates/{templateId}/coverage/export` with `responseType: 'blob'` and triggers a browser file download

### Requirement 16: Coverage Report Export UI

**User Story:** As a template manager, I want an "Export Coverage Report" button in the coverage section of the template detail page, so that I can download the coverage report as a JSON file.

#### Acceptance Criteria

1. WHEN the user clicks "Export Coverage Report" in the coverage section of the template detail page, THE Template_Detail SHALL call `exportCoverageReport` from templates.ts API to trigger a browser file download
2. IF the export API returns an error, THEN THE Template_Detail SHALL display the error message using Element Plus `ElMessage.error`
3. THE export button SHALL use i18n_Keys with the `template.*` key prefix

### Requirement 17: Variable Scan — API Layer

**User Story:** As a frontend developer, I want a variable scan API function, so that the frontend can trigger a manual variable scan for a template.

#### Acceptance Criteria

1. THE templates.ts API file SHALL export function `scanVariables(templateId: number)` that sends POST to `/templates/{templateId}/variables/scan` and returns `VariableDTO[]`

### Requirement 18: Variable Scan UI

**User Story:** As a template designer, I want a "Scan Variables" button in the variable management panel, so that I can manually trigger a re-scan of template variables to detect new or removed variables.

#### Acceptance Criteria

1. THE variable management panel on the template detail page SHALL display a "Scan Variables" button
2. WHEN the user clicks "Scan Variables", THE variable management panel SHALL call `scanVariables` from templates.ts API and refresh the variable list with the returned data
3. WHILE the scan is in progress, THE "Scan Variables" button SHALL display a loading state
4. WHEN the scan completes, THE variable management panel SHALL display a success message indicating the number of variables found
5. THE "Scan Variables" button SHALL use i18n_Keys with the `template.*` key prefix

### Requirement 19: Internationalization Completeness

**User Story:** As a user in a multilingual environment, I want all new UI text to be available in English, Simplified Chinese, and Traditional Chinese, so that I can use the system in my preferred language.

#### Acceptance Criteria

1. THE i18n_Keys SHALL include translations for all new keys added by this feature in `frontend/src/i18n/en-US.json`
2. THE i18n_Keys SHALL include translations for all new keys added by this feature in `frontend/src/i18n/zh-CN.json`
3. THE i18n_Keys SHALL include translations for all new keys added by this feature in `frontend/src/i18n/zh-TW.json`
4. THE i18n_Keys SHALL reuse existing common keys (`common.confirm`, `common.cancel`, `common.delete`, `common.export`, `common.import`, etc.) where applicable, and only add new keys for domain-specific text

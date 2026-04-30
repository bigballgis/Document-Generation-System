# Implementation Plan: Frontend Gap Fill Phase 2

## Overview

This plan breaks phase-2 frontend gap-fill into incremental tasks. Start with API modules (new files + extensions), add Vue components, integrate into existing pages, then i18n and unit tests. Each step builds on the previous one. Stack: TypeScript + Vue 3 Composition API + Element Plus, matching existing patterns.

## Tasks

- [x] 1. API layer — new files
  - [x] 1.1 Template import/export API
    - Add `frontend/src/api/import-export.ts`
    - Import `request` from `@/api/request`, `TemplateDTO` from `@/api/templates`
    - `importDocx(file: File)` → multipart POST `/templates/import` → `TemplateDTO`
    - `exportDocx(templateId: number)` → GET `/templates/{templateId}/export`, `responseType: 'blob'`
    - `exportConfig(templateId: number)` → GET `/templates/{templateId}/export-config`, `responseType: 'blob'`
    - `importConfig(file: File)` → multipart POST `/templates/import-config` → `TemplateDTO`
    - Match `templates.ts` style
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

  - [x] 1.2 Webhooks API
    - Add `frontend/src/api/webhooks.ts`
    - Types: `WebhookConfigDTO`, `CreateWebhookRequest`, `UpdateWebhookRequest`, `WebhookLogDTO`
    - `createWebhook(templateId, data)` → POST `/templates/{templateId}/webhooks`
    - `listWebhooks(templateId)` → GET `/templates/{templateId}/webhooks`
    - `updateWebhook(webhookId, data)` → PUT `/webhooks/{webhookId}`
    - `deleteWebhook(webhookId)` → DELETE `/webhooks/{webhookId}`
    - `getWebhookLogs(webhookId, params)` → GET `/webhooks/{webhookId}/logs` → `PageResult<WebhookLogDTO>`
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

  - [x] 1.3 Rate limits API
    - Add `frontend/src/api/rate-limits.ts`
    - Types: `RateLimitStatusDTO`, `UsageStatsDTO`
    - `getRateLimits()` → GET `/rate-limits` → `RateLimitStatusDTO[]`
    - `getUsageStats()` → GET `/usage-stats` → `UsageStatsDTO`
    - _Requirements: 5.1, 5.2, 5.3_

- [x] 2. API layer — extend existing modules
  - [x] 2.1 Extend `templates.ts` — four functions
    - Edit `frontend/src/api/templates.ts`
    - `submitReview(templateId: number)` → POST `/templates/{templateId}/submit-review` → `TemplateDTO`
    - `getAvailableTransitions(templateId: number)` → GET `/templates/{templateId}/available-transitions` → `string[]`
    - `scanVariables(templateId: number)` → POST `/templates/{templateId}/variables/scan` → `VariableDTO[]`
    - `exportCoverageReport(templateId: number)` → GET `/templates/{templateId}/coverage/export`, `responseType: 'blob'`
    - _Requirements: 9.1, 9.2, 15.1, 17.1_

  - [x] 2.2 Extend `admin.ts` — four functions
    - Edit `frontend/src/api/admin.ts`
    - `submitForReview(templateId, data: { reviewerIds: number[]; reviewLevel?: number })` → POST `/templates/{templateId}/reviews`
    - `getTemplateReviews(templateId, params: { page; size })` → GET `/templates/{templateId}/reviews` → `PageResult<ReviewDTO>`
    - `conditionalApproveReview(reviewId, data: { comment?; suggestions: string[] })` → PUT `/reviews/{reviewId}/conditional-approve` → `ReviewDTO`
    - `getReviewEditorUrl(templateId)` → GET `/templates/{templateId}/reviews/editor-url` → `string`
    - _Requirements: 7.1, 7.2, 7.3, 7.4_

  - [x] 2.3 Extend `market.ts` — two functions
    - Edit `frontend/src/api/market.ts`
    - `exportTestCases(templateId: number)` → GET `/templates/{templateId}/test-cases/export` → `string`
    - `importTestCases(templateId: number, json: string)` → POST `/templates/{templateId}/test-cases/import` → `TestCaseDTO[]`
    - _Requirements: 13.1, 13.2_

  - [x] 2.4 Extend `auth.ts` — one function
    - Edit `frontend/src/api/auth.ts`
    - `resetPassword(email: string)` → POST `/auth/reset-password`, body `{ email }`
    - _Requirements: 11.1_

- [x] 3. Checkpoint — API layer
  - Keep tests green; ask the user if blocked.

- [x] 4. Frontend — webhook components
  - [x] 4.1 WebhookFormDialog
    - Add `frontend/src/views/templates/components/WebhookFormDialog.vue`
    - Props: `visible: boolean`, `data: WebhookConfigDTO | null`, `templateId: number`
    - Emits: `update:visible`, `saved`
    - Form-dialog pattern: `v-model:visible`, computed `isEdit`
    - Fields: URL (required, URL rule), Secret (required, password), Payload Template (optional textarea), Enabled (`el-switch`, edit only)
    - Create → `createWebhook`; edit → `updateWebhook`
    - Copy: `webhook.*` and `common.*`
    - _Requirements: 4.3, 4.4, 4.5, 4.8_

  - [x] 4.2 WebhookLogDialog
    - Add `frontend/src/views/templates/components/WebhookLogDialog.vue`
    - Props: `visible: boolean`, `webhookId: number`
    - Emits: `update:visible`
    - Paginated table: Event Type, Payload (truncated + `el-popover` full text), Response Status (tag by 2xx/4xx/5xx), Response Body (truncated), Sent At
    - `getWebhookLogs` + pagination
    - Copy: `webhook.*` and `common.*`
    - _Requirements: 4.7, 4.8_

  - [x] 4.3 WebhookPanel
    - Add `frontend/src/views/templates/components/WebhookPanel.vue`
    - Props: `templateId: number`
    - Table: URL, Status (`el-tag`), Payload Template (truncated), Created At, Actions (Edit / Delete / Logs)
    - "Create Webhook" → form dialog; Edit → dialog; Delete → `ElMessageBox.confirm` → `deleteWebhook`; Logs → log dialog
    - Empty → `el-empty`
    - Copy: `webhook.*` and `common.*`
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8_

- [x] 5. Frontend — rate limit panel
  - [x] 5.1 RateLimitPanel
    - Add `frontend/src/views/admin/RateLimitPanel.vue`
    - Table: API Key Name, limit/remaining per second/minute/hour
    - Summary card: Monthly Quota, Current Usage, Remaining Quota, Reset Date
    - Refresh reloads both queries
    - `getRateLimits` + `getUsageStats`
    - Empty → "No API Keys" (i18n)
    - Copy: `admin.rateLimit.*` and `common.*`
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

- [x] 6. Frontend — reset password dialog
  - [x] 6.1 ResetPasswordDialog
    - Add `frontend/src/views/auth/ResetPasswordDialog.vue`
    - Props: `visible: boolean`
    - Emits: `update:visible`
    - Email (required, email rule)
    - Submit → `resetPassword`; success → `ElMessage.success` + close; failure → `ElMessage.error`, keep open
    - Copy: `auth.*`
    - _Requirements: 12.2, 12.3, 12.4, 12.5, 12.6_

- [x] 7. Frontend — wire webhooks on template detail
  - [x] 7.1 Webhooks tab
    - Edit `frontend/src/views/templates/Detail.vue`
    - New `<el-tab-pane>`, label `$t('webhook.title')`
    - Embed `WebhookPanel` with `templateId`
    - _Requirements: 4.1_

- [x] 8. Frontend — reviews on template detail
  - [x] 8.1 Reviews tab + actions
    - Edit `frontend/src/views/templates/Detail.vue`
    - Reviews tab: table from `getTemplateReviews` (Reviewer, Status, Level, Comment, Suggestions, Created At)
    - Submit for Review dialog → `submitForReview`
    - Conditional Approve dialog → `conditionalApproveReview`
    - Header "Review in Editor" → `getReviewEditorUrl`, open in new tab
    - Copy: `review.*` and `common.*`
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_

- [x] 9. Frontend — import/export + transitions on detail
  - [x] 9.1 Export + workflow buttons
    - Edit `frontend/src/views/templates/Detail.vue`
    - Header: "Export as .docx" → `exportDocx`; "Export Configuration" → `exportConfig`
    - On load: `getAvailableTransitions` → show transition buttons (Submit for Review / Activate / Archive) instead of hard-coded rules
    - Clicks → `submitReview` / `activateTemplate` / `archiveTemplate`; refresh template
    - Hide block when no transitions
    - _Requirements: 2.3, 2.4, 10.1, 10.2, 10.3, 10.4_

  - [x] 9.2 Scan, coverage export, test case import/export
    - Edit `frontend/src/views/templates/Detail.vue`
    - Variables tab: "Scan Variables" → `scanVariables`, loading state, then refresh + count message
    - Coverage tab: "Export Coverage Report" → `exportCoverageReport`
    - Tests tab: export JSON → `exportTestCases`; import `.json` → `importTestCases`, refresh list
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 16.1, 16.2, 16.3, 18.1, 18.2, 18.3, 18.4, 18.5_

- [x] 10. Frontend — template list imports
  - [x] 10.1 Index.vue
    - Edit `frontend/src/views/templates/Index.vue`
    - Header: "Import Template" → `.docx` → `importDocx`, refresh
    - "Import Configuration" → `.json` → `importConfig`, refresh
    - Hidden `<input type="file">` or `el-upload` `before-upload`
    - Validate extensions `.docx` / `.json`
    - Errors → `ElMessage.error`
    - _Requirements: 2.1, 2.2, 2.5, 2.6_

- [x] 11. Frontend — login forgot password
  - [x] 11.1 LoginView
    - Edit `frontend/src/views/auth/LoginView.vue`
    - `form-footer`: link `$t('auth.forgotPassword')` opens `ResetPasswordDialog`
    - _Requirements: 12.1, 12.2_

- [x] 12. Frontend — admin rate limits tab
  - [x] 12.1 admin/Index.vue
    - New tab label `$t('admin.rateLimits')`
    - Embed `RateLimitPanel`
    - _Requirements: 6.1_

- [x] 13. Checkpoint — integration
  - Keep tests green; ask the user if blocked.

- [x] 14. Frontend — i18n
  - [x] 14.1 Locale files
    - `en-US.json`: add keys listed in original plan (`auth.forgotPassword`, `template.importTemplate`, `webhook.*`, `admin.rateLimit.*`, `review.*`, etc.)
    - `zh-CN.json` / `zh-TW.json`: translations; key sets must match
    - Reuse `common.*` where possible
    - _Requirements: 19.1, 19.2, 19.3, 19.4_

- [x] 15. Checkpoint — i18n
  - Keep tests green; ask the user if blocked.

- [x] 16. Frontend tests — API unit tests
  - [x] 16.1 `import-export.test.ts` — multipart GET/blob behavior
  - [x] 16.2 `webhooks.test.ts` — CRUD + logs URL/params
  - [x] 16.3 `rate-limits.test.ts` — both GETs
  - [x] 16.4 `templates-extensions.test.ts` — submitReview, transitions, scan, coverage export
  - [x] 16.5 `admin-extensions.test.ts` — review APIs
  - [x] 16.6 `market-extensions.test.ts`, `auth-extensions.test.ts`
  - _(Requirements as in original numbering 1.x–13.x, 11.1)_

- [x] 17. Frontend tests — component unit tests
  - [x] 17.1 WebhookPanel.test.ts
  - [x] 17.2 WebhookFormDialog.test.ts
  - [x] 17.3 WebhookLogDialog.test.ts
  - [x] 17.4 RateLimitPanel.test.ts
  - [x] 17.5 ResetPasswordDialog.test.ts

- [x] 18. Final checkpoint
  - Keep tests green; ask the user if blocked.

## Notes

- Tasks map to requirement IDs
- Checkpoints gate incremental validation
- No PBT for this scope (design: thin API + UI; example tests fit better)
- Stack: TypeScript + Vue 3 Composition API + Element Plus
- API style: follow `templates.ts` / `admin.ts`
- i18n: `{module}.{element}` and shared `common.*` keys

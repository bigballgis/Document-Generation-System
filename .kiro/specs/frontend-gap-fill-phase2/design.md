# Design Document — Frontend Gap Fill Phase 2

## Overview

This design describes the technical approach for the second phase of frontend gap-fill work. The goal is to add the missing frontend API layer, Vue components, and i18n entries for backend endpoints that already exist but are not yet exposed in the UI. Scope includes: template import/export, webhook management, rate limiting and usage stats, template review flows, template state machine, password reset, test case import/export, coverage export, and variable scanning.

All changes are frontend-only; the backend is not modified in this phase. The design follows the existing Vue 3 + TypeScript + Element Plus + vue-i18n stack and coding patterns.

## Architecture

### High-level architecture

New work stays in the frontend and follows the existing layered structure:

```
┌─────────────────────────────────────────────────────┐
│                    Vue Views                         │
│  Detail.vue  Index.vue  LoginView.vue  admin/Index  │
│  (modify existing pages; integrate new panels)      │
├─────────────────────────────────────────────────────┤
│              Vue Components (new)                     │
│  WebhookPanel  WebhookFormDialog  WebhookLogDialog  │
│  RateLimitPanel  ResetPasswordDialog                │
├─────────────────────────────────────────────────────┤
│                API Layer (new + extended)           │
│  import-export.ts  webhooks.ts  rate-limits.ts      │
│  templates.ts(+4)  admin.ts(+4)  market.ts(+2)     │
│  auth.ts(+1)                                        │
├─────────────────────────────────────────────────────┤
│              Shared Axios Instance                   │
│                 request.ts                           │
├─────────────────────────────────────────────────────┤
│                 Backend API                          │
│  (already exists; no changes this phase)             │
└─────────────────────────────────────────────────────┘
```

### File change list

**New files:**
- `frontend/src/api/import-export.ts` — template import/export API
- `frontend/src/api/webhooks.ts` — webhook CRUD + logs API
- `frontend/src/api/rate-limits.ts` — rate limits + usage stats API
- `frontend/src/views/templates/components/WebhookPanel.vue` — webhook management panel
- `frontend/src/views/templates/components/WebhookFormDialog.vue` — webhook form dialog
- `frontend/src/views/templates/components/WebhookLogDialog.vue` — webhook log dialog
- `frontend/src/views/admin/RateLimitPanel.vue` — rate limit panel
- `frontend/src/views/auth/ResetPasswordDialog.vue` — password reset dialog

**Modified files:**
- `frontend/src/api/templates.ts` — add `submitReview`, `getAvailableTransitions`, `scanVariables`, `exportCoverageReport`
- `frontend/src/api/admin.ts` — add `submitForReview`, `getTemplateReviews`, `conditionalApproveReview`, `getReviewEditorUrl`
- `frontend/src/api/market.ts` — add `exportTestCases`, `importTestCases`
- `frontend/src/api/auth.ts` — add `resetPassword`
- `frontend/src/views/templates/Detail.vue` — webhooks tab, reviews tab, import/export actions, transition buttons, scan variables, export coverage
- `frontend/src/views/templates/Index.vue` — import buttons (Import Template, Import Configuration)
- `frontend/src/views/auth/LoginView.vue` — "Forgot Password?" link
- `frontend/src/views/admin/Index.vue` — Rate Limits tab
- `frontend/src/i18n/en-US.json` — new keys: `webhook.*`, `admin.rateLimit.*`, `auth.forgotPassword`, etc.
- `frontend/src/i18n/zh-CN.json` — Simplified Chinese translations
- `frontend/src/i18n/zh-TW.json` — Traditional Chinese translations

## Components and Interfaces

### 1. API layer — new files

#### `api/import-export.ts`

```typescript
import request from './request'
import type { TemplateDTO } from './templates'

/** Import a .docx file as a new template */
export function importDocx(file: File): Promise<TemplateDTO> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/templates/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** Export template as .docx — triggers browser download */
export function exportDocx(templateId: number): Promise<Blob> {
  return request.get(`/templates/${templateId}/export`, { responseType: 'blob' })
}

/** Export full template configuration as JSON — triggers browser download */
export function exportConfig(templateId: number): Promise<Blob> {
  return request.get(`/templates/${templateId}/export-config`, { responseType: 'blob' })
}

/** Import a JSON configuration file to restore a template */
export function importConfig(file: File): Promise<TemplateDTO> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/templates/import-config', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
```

#### `api/webhooks.ts`

```typescript
import request from './request'
import type { PageResult } from '@/types'

export interface WebhookConfigDTO {
  id: number
  templateId: number
  url: string
  payloadTemplate: string
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface CreateWebhookRequest {
  url: string
  secret: string
  payloadTemplate?: string
}

export interface UpdateWebhookRequest {
  url?: string
  secret?: string
  payloadTemplate?: string
  enabled?: boolean
}

export interface WebhookLogDTO {
  id: number
  webhookConfigId: number
  eventType: string
  payload: string
  responseStatus: number
  responseBody: string
  sentAt: string
}

export function createWebhook(templateId: number, data: CreateWebhookRequest) {
  return request.post<any, WebhookConfigDTO>(`/templates/${templateId}/webhooks`, data)
}

export function listWebhooks(templateId: number) {
  return request.get<any, WebhookConfigDTO[]>(`/templates/${templateId}/webhooks`)
}

export function updateWebhook(webhookId: number, data: UpdateWebhookRequest) {
  return request.put<any, WebhookConfigDTO>(`/webhooks/${webhookId}`, data)
}

export function deleteWebhook(webhookId: number) {
  return request.delete(`/webhooks/${webhookId}`)
}

export function getWebhookLogs(webhookId: number, params: { page: number; size: number }) {
  return request.get<any, PageResult<WebhookLogDTO>>(`/webhooks/${webhookId}/logs`, { params })
}
```

#### `api/rate-limits.ts`

```typescript
import request from './request'

export interface RateLimitStatusDTO {
  apiKeyId: number
  apiKeyName: string
  limitPerSecond: number
  limitPerMinute: number
  limitPerHour: number
  remainingPerSecond: number
  remainingPerMinute: number
  remainingPerHour: number
}

export interface UsageStatsDTO {
  tenantId: number
  monthlyQuota: number
  currentMonthUsage: number
  remainingQuota: number
  resetAt: string
}

export function getRateLimits() {
  return request.get<any, RateLimitStatusDTO[]>('/rate-limits')
}

export function getUsageStats() {
  return request.get<any, UsageStatsDTO>('/usage-stats')
}
```

### 2. API layer — extensions to existing files

#### `api/templates.ts` — new functions

```typescript
/** Submit template for review via state machine (DRAFT → PENDING_REVIEW) */
export function submitReview(templateId: number) {
  return request.post<any, TemplateDTO>(`/templates/${templateId}/submit-review`)
}

/** Get available state transitions for a template */
export function getAvailableTransitions(templateId: number) {
  return request.get<any, string[]>(`/templates/${templateId}/available-transitions`)
}

/** Trigger a manual variable scan */
export function scanVariables(templateId: number) {
  return request.post<any, VariableDTO[]>(`/templates/${templateId}/variables/scan`)
}

/** Export coverage report as JSON file download */
export function exportCoverageReport(templateId: number) {
  return request.get(`/templates/${templateId}/coverage/export`, { responseType: 'blob' })
}
```

#### `api/admin.ts` — new functions

```typescript
/** Submit a template for review (create review records) */
export function submitForReview(templateId: number, data: { reviewerIds: number[]; reviewLevel?: number }) {
  return request.post(`/templates/${templateId}/reviews`, data)
}

/** Get paginated reviews for a specific template */
export function getTemplateReviews(templateId: number, params: { page: number; size: number }) {
  return request.get<any, PageResult<ReviewDTO>>(`/templates/${templateId}/reviews`, { params })
}

/** Conditionally approve a review with suggestions */
export function conditionalApproveReview(reviewId: number, data: { comment?: string; suggestions: string[] }) {
  return request.put<any, ReviewDTO>(`/reviews/${reviewId}/conditional-approve`, data)
}

/** Get OnlyOffice review editor URL for a template */
export function getReviewEditorUrl(templateId: number) {
  return request.get<any, string>(`/templates/${templateId}/reviews/editor-url`)
}
```

#### `api/market.ts` — new functions

```typescript
/** Export test cases as JSON string */
export function exportTestCases(templateId: number) {
  return request.get<any, string>(`/templates/${templateId}/test-cases/export`)
}

/** Import test cases from JSON string */
export function importTestCases(templateId: number, json: string) {
  return request.post<any, TestCaseDTO[]>(`/templates/${templateId}/test-cases/import`, json, {
    headers: { 'Content-Type': 'application/json' },
  })
}
```

#### `api/auth.ts` — new functions

```typescript
/** Request password reset email */
export function resetPassword(email: string) {
  return request.post('/auth/reset-password', { email })
}
```

### 3. Vue components — new

#### `WebhookPanel.vue`

Tab on the template detail page. Lists webhooks for the template and drives CRUD.

Props: `templateId: number`

Behavior:
- Load and show webhooks in `el-table`
- Columns: URL, Status (`el-switch` or tag), Payload Template (truncated), Created At, Actions
- Actions: Edit (opens `WebhookFormDialog`), Delete (confirm then delete), Logs (opens `WebhookLogDialog`)
- Top "Create Webhook" opens `WebhookFormDialog` in create mode

#### `WebhookFormDialog.vue`

Create/edit webhook dialog; follows the project form-dialog pattern.

Props: `visible: boolean`, `data: WebhookConfigDTO | null`
Emits: `update:visible`, `saved`

Form fields:
- URL (`el-input`, required, URL validation)
- Secret (`el-input`, required, `type=password`)
- Payload Template (`el-input` textarea, optional)
- Enabled (`el-switch`, edit mode only, default true)

#### `WebhookLogDialog.vue`

Webhook log viewer with a paginated table.

Props: `visible: boolean`, `webhookId: number`
Emits: `update:visible`

Columns: Event Type, Payload (truncated, full text in `el-popover`), Response Status (tag colors for 2xx/4xx/5xx), Response Body (truncated), Sent At

#### `RateLimitPanel.vue`

Admin rate-limit dashboard panel.

Behavior:
- Table of rate-limit status per API key
- Usage card: monthly quota, current usage, remaining, reset date (`el-descriptions` or `el-statistic`)
- Refresh reloads data

#### `ResetPasswordDialog.vue`

Password reset flow.

Props: `visible: boolean`
Emits: `update:visible`

Form:
- Email (`el-input`, required, email validation)
- Submit calls `resetPassword` API
- On success: `ElMessage.success` and close

### 4. Existing view modifications

#### `templates/Detail.vue`

1. **Header actions**
   - "Export as .docx" (`exportDocx`)
   - "Export Configuration" (`exportConfig`)
   - Dynamic transition buttons from `getAvailableTransitions`: e.g. "Submit for Review" / "Activate" / "Archive"
   - "Review in Editor" (`getReviewEditorUrl`, new tab)

2. **Tabs**
   - "Webhooks" → `<WebhookPanel :template-id="template.id" />`
   - "Reviews" → review list + Submit for Review + Conditional Approve
   - Variables tab: "Scan Variables" (or inside VariableManagement)
   - Coverage tab: "Export Coverage Report" (or inside CoveragePanel)
   - Tests tab: "Export Test Cases" / "Import Test Cases"

#### `templates/Index.vue`

Header actions:
- "Import Template" (upload .docx → `importDocx`)
- "Import Configuration" (upload .json → `importConfig`)

Use `el-upload` `before-upload` or a hidden `<input type="file">`.

#### `auth/LoginView.vue`

Add "Forgot Password?" in `form-footer`; opens `ResetPasswordDialog`.

#### `admin/Index.vue`

Add "Rate Limits" tab → `<RateLimitPanel />`

## Data Models

### TypeScript interfaces (aligned with backend DTOs)

Interfaces live in their API modules; field types map to Java DTOs.

#### `WebhookConfigDTO` (backend `WebhookConfigDTO.java`)

| Field | TS type | Java type | Description |
|------|---------|-----------|-------------|
| id | `number` | `Long` | Primary key |
| templateId | `number` | `Long` | Template id |
| url | `string` | `String` | Webhook URL |
| payloadTemplate | `string` | `String` | Payload template |
| enabled | `boolean` | `boolean` | Enabled flag |
| createdAt | `string` | `Instant` | Created (ISO string) |
| updatedAt | `string` | `Instant` | Updated (ISO string) |

#### `CreateWebhookRequest` (backend `CreateWebhookRequest.java`)

| Field | TS type | Java type | Validation |
|------|---------|-----------|------------|
| url | `string` | `String` | `@NotBlank` |
| secret | `string` | `String` | `@NotBlank` |
| payloadTemplate | `string?` | `String` | Optional |

#### `UpdateWebhookRequest` (backend `UpdateWebhookRequest.java`)

| Field | TS type | Java type | Description |
|------|---------|-----------|-------------|
| url | `string?` | `String` | Optional |
| secret | `string?` | `String` | Optional |
| payloadTemplate | `string?` | `String` | Optional |
| enabled | `boolean?` | `Boolean` | Optional |

#### `WebhookLogDTO` (backend `WebhookLogDTO.java`)

| Field | TS type | Java type | Description |
|------|---------|-----------|-------------|
| id | `number` | `Long` | Primary key |
| webhookConfigId | `number` | `Long` | Webhook id |
| eventType | `string` | `String` | Event type |
| payload | `string` | `String` | Request payload |
| responseStatus | `number` | `Integer` | HTTP status |
| responseBody | `string` | `String` | Response body |
| sentAt | `string` | `Instant` | Sent at |

#### `RateLimitStatusDTO` (backend `RateLimitStatusDTO.java`)

| Field | TS type | Java type | Description |
|------|---------|-----------|-------------|
| apiKeyId | `number` | `Long` | API key id |
| apiKeyName | `string` | `String` | API key name |
| limitPerSecond | `number` | `int` | Per-second limit |
| limitPerMinute | `number` | `int` | Per-minute limit |
| limitPerHour | `number` | `int` | Per-hour limit |
| remainingPerSecond | `number` | `long` | Remaining per second |
| remainingPerMinute | `number` | `long` | Remaining per minute |
| remainingPerHour | `number` | `long` | Remaining per hour |

#### `UsageStatsDTO` (backend `UsageStatsDTO.java`)

| Field | TS type | Java type | Description |
|------|---------|-----------|-------------|
| tenantId | `number` | `Long` | Tenant id |
| monthlyQuota | `number` | `long` | Monthly quota |
| currentMonthUsage | `number` | `long` | Usage this month |
| remainingQuota | `number` | `long` | Remaining quota |
| resetAt | `string` | `String` | Reset time |

#### `TemplateReviewDTO` (backend `TemplateReviewDTO.java`; extend existing `ReviewDTO` in `admin.ts`)

Extend `ReviewDTO` to match `TemplateReviewDTO`:

| Field | TS type | Java type | Description |
|------|---------|-----------|-------------|
| id | `number` | `Long` | Primary key |
| templateId | `number` | `Long` | Template id |
| reviewerId | `number` | `Long` | Reviewer id |
| reviewLevel | `number` | `int` | Review level |
| status | `string` | `ReviewStatus` | Status |
| comment | `string?` | `String` | Comment |
| suggestions | `string[]?` | `List<String>` | Suggestions |
| createdAt | `string` | `Instant` | Created |
| completedAt | `string?` | `Instant` | Completed |

### i18n key layout

New keys are grouped by prefix:

```
webhook.*          — Webhook UI (some keys may already exist; extend as needed)
admin.rateLimit.*  — Rate limits (extend as needed)
auth.*             — Auth (reset password, forgot password, etc.)
template.*         — Template actions (export, scan, import, etc.)
test.*             — Test cases (import/export keys)
review.*           — Review flows (editor URL, conditional approve, etc.)
common.*           — Shared keys
```

Example additions (`en-US`):

```json
{
  "auth": {
    "forgotPassword": "Forgot Password?"
  },
  "template": {
    "importTemplate": "Import Template",
    "importConfiguration": "Import Configuration",
    "scanVariables": "Scan Variables",
    "scanVariablesSuccess": "Scan completed, {count} variables found",
    "exportCoverageReport": "Export Coverage Report",
    "availableTransitions": "Available Actions"
  },
  "webhook": {
    "payloadTemplate": "Payload Template",
    "confirmDelete": "Are you sure you want to delete this webhook?",
    "eventType": "Event Type",
    "responseStatus": "Response Status",
    "responseBody": "Response Body",
    "sentAt": "Sent At",
    "noLogs": "No webhook logs"
  },
  "admin": {
    "rateLimit": {
      "apiKeyName": "API Key Name",
      "limitPerSecond": "Limit/Second",
      "remainingPerSecond": "Remaining/Second",
      "limitPerMinute": "Limit/Minute",
      "remainingPerMinute": "Remaining/Minute",
      "limitPerHour": "Limit/Hour",
      "remainingPerHour": "Remaining/Hour",
      "monthlyQuota": "Monthly Quota",
      "currentMonthUsage": "Current Month Usage",
      "remainingQuota": "Remaining Quota"
    }
  },
  "review": {
    "reviewInEditor": "Review in Editor",
    "selectReviewers": "Select Reviewers",
    "conditionalApproveDialog": "Conditional Approval"
  }
}
```

## Correctness Properties

Property-based testing (PBT) does not apply here.

Rationale: changes are thin Axios wrappers, Vue UI, and i18n JSON.

1. **API layer**: Each function is a deterministic HTTP call; there is no broad property to stress with random inputs.
2. **Vue components**: DOM and interaction are better covered with example-based tests (Vitest + Vue Test Utils).
3. **i18n**: Key completeness is a static check, not runtime logic.

Acceptance criteria map to EXAMPLE, EDGE_CASE, or SMOKE — not PROPERTY.

See **Testing Strategy** below.

## Error Handling

### API errors

All functions rely on the global Axios interceptor in `request.ts`:

| HTTP status | Behavior |
|-------------|----------|
| 401 | Refresh token; on failure redirect to login |
| 403 | `ElMessage.error('Access denied')` |
| 429 | `ElMessage.warning('Too many requests...')` |
| Other 4xx/5xx | `ElMessage.error(response.message)` |

### Component errors

Wrap API calls in `try/catch`; interceptor handles most cases. Special cases:

- **Upload failure** (Import Template / Configuration): `ElMessage.error` in `catch`; do not refresh lists
- **Download failure** (.docx / config / coverage export): `ElMessage.error`
- **Webhook delete failure**: keep list; show error
- **Password reset failure**: `ElMessage.error` in dialog; keep open

### Upload validation

- Import Template: `.docx` only (`accept` + `before-upload`)
- Import Configuration: `.json` only
- Import Test Cases: `.json` only
- Size limits enforced by backend; frontend does not add extra caps

### Empty states

- No webhooks: `el-empty`
- No API keys for rate limits: "No API Keys"
- No reviews: "No reviews"
- No transitions: hide transition button area

## Testing Strategy

### Stack

- **Unit / component**: Vitest + @vue/test-utils
- **API mocking**: `vi.mock` or MSW

### Layers

#### 1. API unit tests

For each new or extended API function, verify:

- Correct HTTP method (GET/POST/PUT/DELETE)
- Correct URL (path params)
- Correct body / query params
- Correct `responseType` for blob downloads
- Correct `Content-Type` for multipart uploads

Files:

- `frontend/src/__tests__/api/import-export.test.ts`
- `frontend/src/__tests__/api/webhooks.test.ts`
- `frontend/src/__tests__/api/rate-limits.test.ts`
- `frontend/src/__tests__/api/templates-extensions.test.ts`
- `frontend/src/__tests__/api/admin-extensions.test.ts`
- `frontend/src/__tests__/api/market-extensions.test.ts`
- `frontend/src/__tests__/api/auth-extensions.test.ts`

#### 2. Component tests

For each new component:

- Renders columns, fields, buttons
- User actions invoke the right APIs
- Success/failure feedback (ElMessage, refresh, dialog close)
- i18n keys via mocked `$t()`

Files:

- `frontend/src/__tests__/views/templates/WebhookPanel.test.ts`
- `frontend/src/__tests__/views/templates/WebhookFormDialog.test.ts`
- `frontend/src/__tests__/views/templates/WebhookLogDialog.test.ts`
- `frontend/src/__tests__/views/admin/RateLimitPanel.test.ts`
- `frontend/src/__tests__/views/auth/ResetPasswordDialog.test.ts`

#### 3. Integration touchpoints

- `Detail.vue`: webhooks tab, reviews tab, export actions, transitions
- `Index.vue`: import buttons
- `LoginView.vue`: "Forgot Password?"
- `admin/Index.vue`: Rate Limits tab

#### 4. i18n completeness

Script or test that:

- New keys exist in `en-US.json`, `zh-CN.json`, and `zh-TW.json`
- Key sets are aligned across locales

### Why not PBT

Paths are:

- **Thin API wrappers**: input → Axios → output
- **UI**: example-based tests fit better
- **i18n**: static JSON

Input space is small and behavior is fixed; a few explicit examples beat 100 random iterations.

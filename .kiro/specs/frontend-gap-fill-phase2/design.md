# Design Document — Frontend Gap Fill Phase 2

## Overview

本设计文档描述前端补全第二期的技术方案。本期目标是为所有后端已存在但前端缺失的 API 端点补全前端 API 调用层、Vue 组件和 i18n 翻译。涉及模块包括：模板导入导出、Webhook 管理、速率限制与用量统计、模板审核补全、模板状态机补全、密码重置、测试用例导入导出、覆盖率导出、变量扫描。

所有新增代码均为纯前端变更，不涉及后端修改。设计遵循项目现有的 Vue 3 + TypeScript + Element Plus + vue-i18n 技术栈和编码模式。

## Architecture

### 整体架构

本期新增内容全部位于前端层，遵循现有的分层架构：

```
┌─────────────────────────────────────────────────────┐
│                    Vue Views                         │
│  Detail.vue  Index.vue  LoginView.vue  admin/Index  │
│  (修改现有页面，集成新功能面板)                         │
├─────────────────────────────────────────────────────┤
│              Vue Components (新增)                    │
│  WebhookPanel  WebhookFormDialog  WebhookLogDialog  │
│  RateLimitPanel  ResetPasswordDialog                │
├─────────────────────────────────────────────────────┤
│                API Layer (新增 + 扩展)                │
│  import-export.ts  webhooks.ts  rate-limits.ts      │
│  templates.ts(+4)  admin.ts(+4)  market.ts(+2)     │
│  auth.ts(+1)                                        │
├─────────────────────────────────────────────────────┤
│              Shared Axios Instance                   │
│                 request.ts                           │
├─────────────────────────────────────────────────────┤
│                 Backend API                          │
│  (已存在，本期不修改)                                  │
└─────────────────────────────────────────────────────┘
```

### 文件变更清单

**新增文件：**
- `frontend/src/api/import-export.ts` — 模板导入导出 API
- `frontend/src/api/webhooks.ts` — Webhook CRUD + 日志 API
- `frontend/src/api/rate-limits.ts` — 速率限制 + 用量统计 API
- `frontend/src/views/templates/components/WebhookPanel.vue` — Webhook 管理面板
- `frontend/src/views/templates/components/WebhookFormDialog.vue` — Webhook 表单对话框
- `frontend/src/views/templates/components/WebhookLogDialog.vue` — Webhook 日志对话框
- `frontend/src/views/admin/RateLimitPanel.vue` — 速率限制面板
- `frontend/src/views/auth/ResetPasswordDialog.vue` — 密码重置对话框

**修改文件：**
- `frontend/src/api/templates.ts` — 新增 `submitReview`, `getAvailableTransitions`, `scanVariables`, `exportCoverageReport`
- `frontend/src/api/admin.ts` — 新增 `submitForReview`, `getTemplateReviews`, `conditionalApproveReview`, `getReviewEditorUrl`
- `frontend/src/api/market.ts` — 新增 `exportTestCases`, `importTestCases`
- `frontend/src/api/auth.ts` — 新增 `resetPassword`
- `frontend/src/views/templates/Detail.vue` — 新增 Webhook 标签页、审核标签页、导入导出按钮、状态转换按钮、变量扫描按钮、覆盖率导出按钮
- `frontend/src/views/templates/Index.vue` — 新增导入按钮（Import Template、Import Configuration）
- `frontend/src/views/auth/LoginView.vue` — 新增 "Forgot Password?" 链接
- `frontend/src/views/admin/Index.vue` — 新增 Rate Limits 标签页
- `frontend/src/i18n/en-US.json` — 新增 webhook.*, admin.rateLimit.*, auth.forgotPassword 等 key
- `frontend/src/i18n/zh-CN.json` — 对应中文翻译
- `frontend/src/i18n/zh-TW.json` — 对应繁体翻译

## Components and Interfaces

### 1. API Layer — New Files

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

### 2. API Layer — Extensions to Existing Files

#### `api/templates.ts` — 新增函数

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

#### `api/admin.ts` — 新增函数

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

#### `api/market.ts` — 新增函数

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

#### `api/auth.ts` — 新增函数

```typescript
/** Request password reset email */
export function resetPassword(email: string) {
  return request.post('/auth/reset-password', { email })
}
```

### 3. Vue Components — New

#### `WebhookPanel.vue`

作为模板详情页的标签页组件，负责展示 Webhook 列表和触发 CRUD 操作。

Props: `templateId: number`

功能：
- 加载并展示当前模板的 Webhook 列表（el-table）
- 列：URL、Status（el-switch 或 tag）、Payload Template（truncated）、Created At、Actions
- Actions：Edit（打开 WebhookFormDialog）、Delete（确认后删除）、Logs（打开 WebhookLogDialog）
- 顶部 "Create Webhook" 按钮打开 WebhookFormDialog（创建模式）

#### `WebhookFormDialog.vue`

Webhook 创建/编辑对话框，遵循项目 Form Dialog Pattern。

Props: `visible: boolean`, `data: WebhookConfigDTO | null`
Emits: `update:visible`, `saved`

表单字段：
- URL（el-input, required, URL 校验）
- Secret（el-input, required, type=password）
- Payload Template（el-input type=textarea, optional）
- Enabled（el-switch, 仅编辑模式显示, 默认 true）

#### `WebhookLogDialog.vue`

Webhook 日志查看对话框，展示分页日志表格。

Props: `visible: boolean`, `webhookId: number`
Emits: `update:visible`

表格列：Event Type、Payload（truncated, el-popover 展示完整内容）、Response Status（tag 颜色区分 2xx/4xx/5xx）、Response Body（truncated）、Sent At

#### `RateLimitPanel.vue`

管理页面的速率限制面板组件。

功能：
- 速率限制表格：展示所有 API Key 的限流状态（el-table）
- 用量统计卡片：展示月度配额、当前用量、剩余配额、重置日期（el-descriptions 或 el-statistic）
- Refresh 按钮重新加载数据

#### `ResetPasswordDialog.vue`

密码重置对话框。

Props: `visible: boolean`
Emits: `update:visible`

表单字段：
- Email（el-input, required, email 校验）
- Submit 按钮调用 `resetPassword` API
- 成功后显示 `ElMessage.success` 提示并关闭对话框

### 4. Existing View Modifications

#### `templates/Detail.vue` 变更

1. **Header Actions 区域**：
   - 新增 "Export as .docx" 按钮（调用 `exportDocx`）
   - 新增 "Export Configuration" 按钮（调用 `exportConfig`）
   - 动态状态转换按钮：根据 `getAvailableTransitions` 返回值显示 "Submit for Review" / "Activate" / "Archive"
   - 新增 "Review in Editor" 按钮（调用 `getReviewEditorUrl`，新标签页打开）

2. **Tabs 区域**：
   - 新增 "Webhooks" 标签页 → `<WebhookPanel :template-id="template.id" />`
   - 新增 "Reviews" 标签页 → 展示模板审核列表 + "Submit for Review" + "Conditional Approve" 功能
   - 在 Variables 标签页中集成 "Scan Variables" 按钮（或在 VariableManagement 组件中添加）
   - 在 Coverage 标签页中集成 "Export Coverage Report" 按钮（或在 CoveragePanel 组件中添加）
   - 在 Tests 标签页中集成 "Export Test Cases" / "Import Test Cases" 按钮

#### `templates/Index.vue` 变更

Header Actions 区域新增：
- "Import Template" 按钮（上传 .docx，调用 `importDocx`）
- "Import Configuration" 按钮（上传 .json，调用 `importConfig`）

使用 `el-upload` 组件的 `before-upload` 或手动触发 `<input type="file">` 实现文件选择。

#### `auth/LoginView.vue` 变更

在 `form-footer` 区域新增 "Forgot Password?" 链接，点击打开 `ResetPasswordDialog`。

#### `admin/Index.vue` 变更

新增 "Rate Limits" 标签页 → `<RateLimitPanel />`

## Data Models

### TypeScript 接口定义（与后端 DTO 对应）

以下接口定义在各自的 API 文件中，字段类型严格对应后端 Java DTO。

#### WebhookConfigDTO（后端 `WebhookConfigDTO.java`）

| 字段 | TS 类型 | Java 类型 | 说明 |
|------|---------|-----------|------|
| id | `number` | `Long` | 主键 |
| templateId | `number` | `Long` | 关联模板 ID |
| url | `string` | `String` | Webhook URL |
| payloadTemplate | `string` | `String` | 负载模板 |
| enabled | `boolean` | `boolean` | 是否启用 |
| createdAt | `string` | `Instant` | 创建时间（ISO 字符串） |
| updatedAt | `string` | `Instant` | 更新时间（ISO 字符串） |

#### CreateWebhookRequest（后端 `CreateWebhookRequest.java`）

| 字段 | TS 类型 | Java 类型 | 校验 |
|------|---------|-----------|------|
| url | `string` | `String` | `@NotBlank` |
| secret | `string` | `String` | `@NotBlank` |
| payloadTemplate | `string?` | `String` | 可选 |

#### UpdateWebhookRequest（后端 `UpdateWebhookRequest.java`）

| 字段 | TS 类型 | Java 类型 | 说明 |
|------|---------|-----------|------|
| url | `string?` | `String` | 可选 |
| secret | `string?` | `String` | 可选 |
| payloadTemplate | `string?` | `String` | 可选 |
| enabled | `boolean?` | `Boolean` | 可选 |

#### WebhookLogDTO（后端 `WebhookLogDTO.java`）

| 字段 | TS 类型 | Java 类型 | 说明 |
|------|---------|-----------|------|
| id | `number` | `Long` | 主键 |
| webhookConfigId | `number` | `Long` | 关联 Webhook ID |
| eventType | `string` | `String` | 事件类型 |
| payload | `string` | `String` | 请求负载 |
| responseStatus | `number` | `Integer` | HTTP 响应码 |
| responseBody | `string` | `String` | 响应体 |
| sentAt | `string` | `Instant` | 发送时间 |

#### RateLimitStatusDTO（后端 `RateLimitStatusDTO.java`）

| 字段 | TS 类型 | Java 类型 | 说明 |
|------|---------|-----------|------|
| apiKeyId | `number` | `Long` | API Key ID |
| apiKeyName | `string` | `String` | API Key 名称 |
| limitPerSecond | `number` | `int` | 每秒限制 |
| limitPerMinute | `number` | `int` | 每分钟限制 |
| limitPerHour | `number` | `int` | 每小时限制 |
| remainingPerSecond | `number` | `long` | 每秒剩余 |
| remainingPerMinute | `number` | `long` | 每分钟剩余 |
| remainingPerHour | `number` | `long` | 每小时剩余 |

#### UsageStatsDTO（后端 `UsageStatsDTO.java`）

| 字段 | TS 类型 | Java 类型 | 说明 |
|------|---------|-----------|------|
| tenantId | `number` | `Long` | 租户 ID |
| monthlyQuota | `number` | `long` | 月度配额 |
| currentMonthUsage | `number` | `long` | 当月已用 |
| remainingQuota | `number` | `long` | 剩余配额 |
| resetAt | `string` | `String` | 重置时间 |

#### TemplateReviewDTO（后端 `TemplateReviewDTO.java`，admin.ts 中已有 ReviewDTO 需扩展）

现有 `ReviewDTO` 需补充字段以匹配后端 `TemplateReviewDTO`：

| 字段 | TS 类型 | Java 类型 | 说明 |
|------|---------|-----------|------|
| id | `number` | `Long` | 主键 |
| templateId | `number` | `Long` | 模板 ID |
| reviewerId | `number` | `Long` | 审核人 ID |
| reviewLevel | `number` | `int` | 审核级别 |
| status | `string` | `ReviewStatus` | 状态枚举 |
| comment | `string?` | `String` | 评论 |
| suggestions | `string[]?` | `List<String>` | 建议列表 |
| createdAt | `string` | `Instant` | 创建时间 |
| completedAt | `string?` | `Instant` | 完成时间 |

### i18n Key 结构

新增 key 按模块前缀组织：

```
webhook.*          — Webhook 管理相关（已有部分 key，需补充）
admin.rateLimit.*  — 速率限制相关（已有部分 key，需补充）
auth.*             — 认证相关（已有 resetPassword 等 key，需补充 forgotPassword）
template.*         — 模板相关（已有 exportDocx 等 key，需补充 scanVariables、importTemplate 等）
test.*             — 测试用例相关（已有 importCases/exportCases key）
review.*           — 审核相关（已有部分 key，需补充 reviewInEditor、conditionalApproveDialog 等）
common.*           — 通用 key（复用已有 key）
```

新增 key 示例（en-US）：

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

本功能不适用 Property-Based Testing (PBT)。

原因：本期所有变更均为前端 API 薄封装层（Axios wrapper）、Vue UI 组件和 i18n 翻译。具体来说：

1. **API 层**：每个函数都是对 Axios 实例的简单调用封装，输入输出行为完全确定，不存在需要通过大量随机输入验证的通用属性。
2. **Vue 组件**：UI 渲染和交互行为属于 DOM 操作，适合使用 example-based 组件测试（Vitest + @vue/test-utils）验证。
3. **i18n**：翻译 key 的完整性是静态检查，不涉及运行时逻辑。

所有验收标准均归类为 EXAMPLE（具体场景测试）、EDGE_CASE（错误处理边界）或 SMOKE（编译/配置检查），无 PROPERTY 类型候选。

替代测试策略见下方 Testing Strategy 章节。

## Error Handling

### API 层错误处理

所有 API 函数依赖 `request.ts` 中的全局 Axios 响应拦截器处理通用错误：

| HTTP 状态码 | 处理方式 |
|-------------|---------|
| 401 | 自动刷新 Token，失败则跳转登录页 |
| 403 | `ElMessage.error('Access denied')` |
| 429 | `ElMessage.warning('Too many requests...')` |
| 其他 4xx/5xx | `ElMessage.error(response.message)` |

### 组件层错误处理

各组件在调用 API 时使用 `try/catch` 包裹，错误由拦截器统一处理。特殊场景：

- **文件上传失败**（Import Template / Import Configuration）：在 catch 中显示 `ElMessage.error`，不刷新列表
- **文件下载失败**（Export .docx / Export Config / Export Coverage）：在 catch 中显示 `ElMessage.error`
- **Webhook 删除失败**：保持列表不变，显示错误消息
- **密码重置失败**：在 ResetPasswordDialog 中显示 `ElMessage.error`，不关闭对话框

### 文件上传校验

- Import Template：仅接受 `.docx` 文件（通过 `accept=".docx"` 和 `before-upload` 校验）
- Import Configuration：仅接受 `.json` 文件
- Import Test Cases：仅接受 `.json` 文件
- 文件大小限制由后端控制，前端不做额外限制

### 空状态处理

- Webhook 列表为空时显示 `el-empty` 占位
- Rate Limit 列表为空时显示 "No API Keys" 提示
- 审核列表为空时显示 "No reviews" 提示
- 可用状态转换为空时隐藏转换按钮区域

## Testing Strategy

### 测试框架

- **单元测试 / 组件测试**：Vitest + @vue/test-utils
- **API Mock**：vitest 的 `vi.mock` 或 MSW (Mock Service Worker)

### 测试分层

#### 1. API 层单元测试

为每个新增/扩展的 API 函数编写单元测试，验证：
- 正确的 HTTP 方法（GET/POST/PUT/DELETE）
- 正确的 URL 路径（含路径参数替换）
- 正确的请求体 / 查询参数
- 正确的 `responseType`（blob 下载场景）
- 正确的 `Content-Type`（multipart 上传场景）

测试文件：
- `frontend/src/__tests__/api/import-export.test.ts`
- `frontend/src/__tests__/api/webhooks.test.ts`
- `frontend/src/__tests__/api/rate-limits.test.ts`
- `frontend/src/__tests__/api/templates-extensions.test.ts`
- `frontend/src/__tests__/api/admin-extensions.test.ts`
- `frontend/src/__tests__/api/market-extensions.test.ts`
- `frontend/src/__tests__/api/auth-extensions.test.ts`

#### 2. 组件测试

为每个新增 Vue 组件编写组件测试，验证：
- 组件正确渲染（表格列、表单字段、按钮）
- 用户交互触发正确的 API 调用
- 成功/失败后的 UI 反馈（ElMessage、列表刷新、对话框关闭）
- i18n key 正确使用（通过 mock i18n 验证 `$t()` 调用）

测试文件：
- `frontend/src/__tests__/views/templates/WebhookPanel.test.ts`
- `frontend/src/__tests__/views/templates/WebhookFormDialog.test.ts`
- `frontend/src/__tests__/views/templates/WebhookLogDialog.test.ts`
- `frontend/src/__tests__/views/admin/RateLimitPanel.test.ts`
- `frontend/src/__tests__/views/auth/ResetPasswordDialog.test.ts`

#### 3. 集成点测试

验证现有页面正确集成新功能：
- `Detail.vue` 包含 Webhook 标签页、Reviews 标签页、导出按钮、状态转换按钮
- `Index.vue` 包含导入按钮
- `LoginView.vue` 包含 "Forgot Password?" 链接
- `admin/Index.vue` 包含 Rate Limits 标签页

#### 4. i18n 完整性检查

编写脚本或测试验证：
- 所有新增 key 在 en-US.json、zh-CN.json、zh-TW.json 中均存在
- 三个语言文件的 key 集合一致（无遗漏）

### 不使用 PBT 的理由

本功能的所有代码路径均为：
- **API 薄封装**：输入→Axios 调用→输出，无复杂转换逻辑
- **UI 渲染与交互**：DOM 操作，适合 example-based 测试
- **i18n 翻译**：静态 JSON 文件，适合 schema/completeness 检查

这些场景中，输入空间有限且行为确定，100 次随机迭代不会比 2-3 个具体示例发现更多 bug。Example-based 单元测试和组件测试是最合适的测试策略。

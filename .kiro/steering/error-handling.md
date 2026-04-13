---
description: 错误处理规范，包括 ErrorCode 命名、异常类使用、HTTP 状态码映射和日志规范
inclusion: auto
fileMatchPattern: '**/*Exception*.java,**/*exception*/**/*.java,**/*Error*.java,**/*Controller*.java'
---

# 错误处理规范

## ErrorCode 命名规范

错误码按模块前缀组织，格式: `{MODULE}_{ERROR_TYPE}`

已有模块前缀:
- `AUTH_` — 认证授权
- `TENANT_` — 租户管理
- `TEMPLATE_` — 模板管理
- `DATASOURCE_` — 数据源
- `EXPRESSION_` — 表达式引擎
- `GENERATE_` — 文档生成
- `TASK_` — 异步任务
- `DOCUMENT_` — 文档管理
- `VALIDATION_` — 数据验证
- `RATE_LIMIT_` — 限流
- `REVIEW_` — 审查
- `ENCRYPTION_` — 加密
- `PIPELINE_` — 数据管道
- `TEMPLATE_VARIABLE_` — 模板变量
- `COVERAGE_` — 覆盖率检查
- `IMPORT_` / `EXPORT_` — 导入导出
- `WEBHOOK_` — Webhook
- `SCHEDULED_TASK_` — 定时任务
- `AUDIT_` — 审计日志
- `API_KEY_` — API 密钥
- `WATERMARK_` — 水印
- `MERGE_` — 文档合并
- `TEST_CASE_` — 模板测试
- `MARKET_` — 模板市场
- `INTERNAL_` — 内部错误

新增模块时在 `ErrorCode.java` 中添加对应前缀的常量块。

## 异常类使用规范

- `ResourceNotFoundException` — 资源不存在 (HTTP 404)
- `BusinessException` — 业务逻辑错误 (可指定 HTTP 状态码)
- `ValidationException` — 参数校验失败 (HTTP 400/422)

## HTTP 状态码映射

| 场景 | 状态码 | 说明 |
|------|--------|------|
| 资源不存在 | 404 | ResourceNotFoundException |
| 参数校验失败 | 400 | @Valid 校验 / ValidationException |
| 业务规则冲突 | 409 | 如删除被引用的资源 |
| 语义错误 | 422 | 如 Assembly_Config 无效 |
| 权限不足 | 403 | PermissionService 拒绝 |
| 未认证 | 401 | JWT/API Key 无效 |
| 限流 | 429 | RateLimitService 拒绝 |
| 服务器内部错误 | 500 | 未预期的异常 |

## 错误响应格式

所有错误响应必须使用统一格式:
```json
{
  "code": "TEMPLATE_NOT_FOUND",
  "message": "模板不存在",
  "timestamp": "2025-01-01T00:00:00Z"
}
```

## 日志规范

- ERROR: 系统异常、外部服务不可用
- WARN: 业务异常、降级处理
- INFO: 关键业务操作（创建、删除、状态变更）
- DEBUG: 详细执行过程（仅开发环境）

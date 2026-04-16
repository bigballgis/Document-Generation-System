---
inclusion: auto
name: error-handling
description: 错误处理规范。在编写异常处理、Controller 或 ErrorCode 时使用。
---

# 错误处理规范

## ErrorCode

- 格式: `{MODULE}_{ERROR_TYPE}`
- 定义在: #[[file:backend/src/main/java/com/docgen/exception/ErrorCode.java]]
- 现有前缀: AUTH_, TENANT_, TEMPLATE_, EXPRESSION_, GENERATE_, TASK_, DOCUMENT_, VALIDATION_, RATE_LIMIT_, REVIEW_, ENCRYPTION_, PIPELINE_, COVERAGE_, IMPORT_/EXPORT_, ONLYOFFICE_, WEBHOOK_, SCHEDULED_TASK_, AUDIT_, API_KEY_, WATERMARK_, MERGE_, TEST_CASE_, MARKET_, COMPOSITE_, PARAMETER_, MIGRATION_, INTERNAL_

## 异常类 → HTTP 映射

| 异常 | HTTP | 场景 |
|------|------|------|
| `ResourceNotFoundException` | 404 | 资源不存在 |
| `BusinessException` | 可指定 | 业务逻辑错误 |
| `ValidationException` | 400 | 参数校验失败 |
| `AccessDeniedException` | 403 | 权限不足 |
| `RateLimitExceededException` | 429 | 限流 |

- 参考: #[[file:backend/src/main/java/com/docgen/exception/GlobalExceptionHandler.java]]

## 错误响应

```json
{ "code": "TEMPLATE_NOT_FOUND", "message": "模板不存在", "timestamp": "2025-01-01T00:00:00Z" }
```

## 日志级别

ERROR=系统异常/外部不可用, WARN=业务异常/降级, INFO=关键操作, DEBUG=详细过程(仅开发)

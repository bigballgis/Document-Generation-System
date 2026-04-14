---
inclusion: auto
name: error-handling
description: 错误处理规范。在编写异常处理、Controller 或 ErrorCode 时使用。
---

# 错误处理规范

## ErrorCode 命名

格式 `{MODULE}_{ERROR_TYPE}`，已有前缀: AUTH_, TENANT_, TEMPLATE_, DATASOURCE_, EXPRESSION_, GENERATE_, TASK_, DOCUMENT_, VALIDATION_, RATE_LIMIT_, REVIEW_, ENCRYPTION_, PIPELINE_, TEMPLATE_VARIABLE_, COVERAGE_, IMPORT_/EXPORT_, WEBHOOK_, SCHEDULED_TASK_, AUDIT_, API_KEY_, WATERMARK_, MERGE_, TEST_CASE_, MARKET_, INTERNAL_

新模块在 `ErrorCode.java` 中添加对应前缀常量块。

## 异常类

| 异常 | HTTP | 场景 |
|------|------|------|
| ResourceNotFoundException | 404 | 资源不存在 |
| BusinessException | 可指定 | 业务逻辑错误 |
| ValidationException | 400/422 | 参数校验失败 |

其他映射: 权限不足→403, 未认证→401, 限流→429, 冲突→409, 语义错误→422

## 错误响应

```json
{ "code": "TEMPLATE_NOT_FOUND", "message": "模板不存在", "timestamp": "2025-01-01T00:00:00Z" }
```

## 日志级别

ERROR=系统异常/外部不可用, WARN=业务异常/降级, INFO=关键操作, DEBUG=详细过程(仅开发)

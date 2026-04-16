---
inclusion: auto
name: api-design
description: RESTful API 设计规范。在创建或修改 Controller、API 调用层时使用。
---

# API 设计规范

## URL

- 小写 + 连字符 + 复数: `/api/composite-templates/{id}/segments`
- 嵌套最多两层，操作用动词子路径: `/api/templates/{id}/activate`

## HTTP 方法 → 响应码

| 方法 | 用途 | 幂等 | 成功码 |
|------|------|------|--------|
| GET | 查询 | 是 | 200 |
| POST | 创建 | 否 | 201 |
| PUT | 全量更新 | 是 | 200 |
| PATCH | 部分更新 | 否 | 200 |
| DELETE | 删除 | 是 | 204 |

## 响应格式

- 分页: `{ content, totalElements, totalPages, number, size }` (Spring Page 默认)
- 错误: `{ code, message, timestamp }` — code 来自 #[[file:backend/src/main/java/com/docgen/exception/ErrorCode.java]]

## 前端 API 层

- 每模块一个文件: `frontend/src/api/{module}.ts`
- 参考: #[[file:frontend/src/api/parameters.ts]]

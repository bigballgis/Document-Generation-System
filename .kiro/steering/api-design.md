---
inclusion: auto
name: api-design
description: RESTful API 设计规范。在创建或修改 Controller、API 调用层时使用。
---

# API 设计规范

## URL 命名

- 小写 + 连字符: `/api/composite-templates/{id}/segments`
- 资源名复数，嵌套最多两层
- 操作用动词子路径: `/api/templates/{id}/activate`

## HTTP 方法

| 方法 | 用途 | 幂等 | 响应码 |
|------|------|------|--------|
| GET | 查询 | 是 | 200 |
| POST | 创建 | 否 | 201 |
| PUT | 全量更新 | 是 | 200 |
| PATCH | 部分更新 | 否 | 200 |
| DELETE | 删除 | 是 | 204 |

## 响应格式

```json
// 分页
{ "content": [...], "totalElements": 100, "totalPages": 10, "number": 0, "size": 10 }

// 错误
{ "code": "RESOURCE_NOT_FOUND", "message": "模板不存在", "timestamp": "2025-01-01T00:00:00Z" }
```

## 前端 API 层

- 每模块一个文件: `src/api/segments.ts`
- 使用 TypeScript 类型定义请求/响应
- 统一通过 `src/api/request.ts` Axios 实例

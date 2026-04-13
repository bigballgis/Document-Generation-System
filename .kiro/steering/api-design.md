---
description: RESTful API 设计规范，包括 URL 命名、HTTP 方法语义、响应格式和前端 API 调用规范
inclusion: auto
fileMatchPattern: '**/*Controller*.java,**/*controller*.java,**/api/**/*.ts'
---

# API 设计规范

## RESTful API 设计原则

### URL 命名

- 使用小写字母和连字符: `/api/composite-templates/{id}/segments`
- 资源名使用复数: `/api/segments`, `/api/templates`
- 嵌套资源最多两层: `/api/templates/{id}/versions`
- 操作使用动词子路径: `/api/templates/{id}/activate`, `/api/segments/{id}/promote`

### HTTP 方法语义

| 方法 | 用途 | 幂等性 | 响应码 |
|------|------|--------|--------|
| GET | 查询资源 | 是 | 200 |
| POST | 创建资源 | 否 | 201 |
| PUT | 全量更新 | 是 | 200 |
| PATCH | 部分更新 | 否 | 200 |
| DELETE | 删除资源 | 是 | 204 |

### 响应格式

成功响应:
```json
{
  "id": 1,
  "name": "...",
  ...
}
```

分页响应:
```json
{
  "content": [...],
  "totalElements": 100,
  "totalPages": 10,
  "number": 0,
  "size": 10
}
```

错误响应:
```json
{
  "code": "RESOURCE_NOT_FOUND",
  "message": "模板不存在",
  "timestamp": "2025-01-01T00:00:00Z"
}
```

### 版本控制

- API 版本通过 URL 前缀: `/api/v1/...`（当前版本不加前缀，未来破坏性变更时引入）

## 前端 API 调用规范

- 所有 API 调用通过 `src/api/request.ts` 的 Axios 实例
- 每个功能模块一个 API 文件: `src/api/segments.ts`, `src/api/composite-templates.ts`
- 使用 TypeScript 类型定义请求和响应

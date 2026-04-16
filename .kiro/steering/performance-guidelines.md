---
inclusion: manual
name: performance-guidelines
description: 性能优化指南，包括后端性能基线、数据库查询优化、缓存策略和前端性能要求
---

# 性能优化指南

## 后端基线

| 文档大小 | 时限 | 模式 |
|----------|------|------|
| ≤10 页 | ≤5s | 同步 |
| 11-100 页 | ≤15s | 同步/异步 |
| 101-1000 页 | ≤30s | 强制异步 |

API < 100 TPS，DB 连接池 max 20 / min idle 5，Redis 超时 5000ms

## 数据库

- 所有查询走索引，禁止全表扫描
- 分页用 `Pageable`，默认 size 20
- N+1 用 `@EntityGraph` 或 `JOIN FETCH`
- 批量操作用 `@Modifying` 批量 SQL
- `tenant_id` 必须在复合索引中

## Redis 缓存

| 场景 | Key 格式 | TTL |
|------|----------|-----|
| 限流计数器 | `rl:{apiKey}:{window}` | 窗口时间 |
| Refresh Token | `rt:{userId}` | 7天 |
| 模板元数据 | `tpl:{tenantId}:{templateId}` | 5分钟 |

缓存 Key 必须含 tenantId，数据变更时主动清除，穿透用空值短 TTL。

## 前端

- 路由懒加载 + Element Plus 按需导入 + 大列表虚拟滚动
- 搜索/滚动防抖节流，大对象用 `shallowRef`

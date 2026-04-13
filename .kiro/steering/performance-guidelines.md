---
description: 性能优化指南，包括后端性能基线、数据库查询优化、缓存策略和前端性能要求
inclusion: manual
---

# 性能优化指南

## 后端性能基线

### 文档生成响应时间

| 文档大小 | 目标时间 | 说明 |
|----------|----------|------|
| 小型 (≤ 10 页) | ≤ 5 秒 | 同步返回 |
| 中型 (11-100 页) | ≤ 15 秒 | 同步或异步 |
| 大型 (101-1000 页) | ≤ 30 秒 | 强制异步 |

### 并发要求

- API 吞吐量: < 100 TPS
- 数据库连接池: 最大 20 连接，最小空闲 5
- Redis 连接超时: 5000ms

## 数据库查询优化

### 必须遵守

- 所有查询必须走索引，禁止全表扫描
- 分页查询使用 Spring Data `Pageable`，默认 page size 20
- N+1 查询问题使用 `@EntityGraph` 或 `JOIN FETCH` 解决
- 大批量操作使用 `@Modifying` 批量 SQL 而非逐条操作
- 复杂查询使用 `@Query` 自定义 JPQL/Native SQL

### 索引策略

- `tenant_id` 必须在所有查询条件的复合索引中
- 高频查询字段（name, status, created_at）创建索引
- JSONB 字段使用 GIN 索引

## 缓存策略

### Redis 缓存使用场景

| 场景 | Key 格式 | TTL |
|------|----------|-----|
| 数据源响应缓存 | `ds:{tenantId}:{dsId}:{paramHash}` | 用户配置 |
| 限流计数器 | `rl:{apiKey}:{window}` | 窗口时间 |
| Refresh Token | `rt:{userId}` | 7 天 |
| 模板元数据缓存 | `tpl:{tenantId}:{templateId}` | 5 分钟 |

### 缓存原则

- 读多写少的数据适合缓存
- 缓存 Key 必须包含 `tenantId` 防止跨租户数据泄露
- 数据变更时主动清除相关缓存
- 缓存穿透使用空值缓存（短 TTL）

## 前端性能

### 加载性能

- 路由组件使用懒加载 (`() => import(...)`)
- Element Plus 组件按需导入（unplugin-vue-components）
- 图片资源使用懒加载

### 运行时性能

- 大列表使用虚拟滚动
- 频繁触发的事件（搜索、滚动）使用防抖/节流
- 避免不必要的响应式数据（大对象使用 `shallowRef`）

## 组合模板性能考量

- 段落渲染支持并行化（无依赖的 Segment 可并行渲染）
- 段落预览使用缓存（相同版本 + 相同数据 = 缓存命中）
- Assembly_Config 中的 Segment 数量建议上限: 50 个
- 单个 Segment 文件大小建议上限: 10MB

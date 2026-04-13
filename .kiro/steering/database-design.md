---
description: 数据库设计规范，包括 Flyway 迁移、表设计、索引、命名规范和 PostgreSQL 特性使用
inclusion: auto
fileMatchPattern: '**/migration/**/*.sql,**/*entity*/**/*.java,**/*Entity*.java'
---

# 数据库设计规范

## Flyway 迁移规范

- 文件路径: `backend/src/main/resources/db/migration/`
- 命名: `V{序号}__{描述}.sql`（双下划线）
- 当前最大版本: V28，新迁移从 V29 开始递增
- 每个迁移文件只做一件事（创建表、添加列、创建索引等）
- 禁止修改已发布的迁移文件

## 表设计规范

### 必须字段

每张业务表必须包含：
- `id BIGSERIAL PRIMARY KEY` — 自增主键
- `tenant_id BIGINT NOT NULL REFERENCES tenants(id)` — 多租户隔离
- `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()` — 创建时间
- `updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()` — 更新时间

### 索引规范

- 所有外键字段必须创建索引
- `tenant_id` 必须包含在复合索引中（多租户查询优化）
- 频繁查询的字段创建索引
- 使用 `CREATE INDEX CONCURRENTLY` 避免锁表（生产环境）

### 命名规范

- 表名: 小写复数 `segments`, `composite_templates`, `segment_versions`
- 列名: 小写下划线 `segment_id`, `version_number`, `is_component`
- 索引名: `idx_{表名}_{列名}` 如 `idx_segments_tenant_id`
- 外键名: `fk_{表名}_{引用表名}` 如 `fk_segments_tenants`
- 唯一约束: `uq_{表名}_{列名}` 如 `uq_segment_versions_segment_id_version_number`

### JSONB 使用规范

- 配置类数据（Assembly_Config 等）使用 JSONB 类型存储
- 需要查询的字段不放在 JSONB 中，提取为独立列
- JSONB 字段创建 GIN 索引以支持查询

## PostgreSQL 特性使用

- 使用 `TIMESTAMPTZ` 而非 `TIMESTAMP`
- 使用 `TEXT` 而非 `VARCHAR` (除非有明确长度限制)
- 使用 `BIGSERIAL` 而非 `SERIAL`
- 使用 `BOOLEAN` 而非 `SMALLINT` 表示布尔值

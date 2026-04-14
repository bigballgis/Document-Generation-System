---
inclusion: auto
name: database-design
description: 数据库设计规范。在创建 Flyway 迁移、修改 Entity 或编写 SQL 时使用。
---

# 数据库设计规范

## Flyway 迁移

- 路径: `backend/src/main/resources/db/migration/V{序号}__{描述}.sql`
- 当前最大版本 V28，新迁移从 V29 递增
- 每个迁移只做一件事，禁止修改已发布的迁移

## 必须字段

每张业务表: `id BIGSERIAL PRIMARY KEY` + `tenant_id BIGINT NOT NULL REFERENCES tenants(id)` + `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()` + `updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`

## 命名

- 表: 小写复数 `segments`, `composite_templates`
- 列: 小写下划线 `segment_id`, `is_component`
- 索引: `idx_{表}_{列}` / 外键: `fk_{表}_{引用表}` / 唯一: `uq_{表}_{列}`

## 规则

- 所有外键必须建索引，`tenant_id` 必须在复合索引中
- 生产环境用 `CREATE INDEX CONCURRENTLY`
- JSONB 用于配置类数据，需查询的字段提取为独立列，JSONB 建 GIN 索引
- 使用 `TIMESTAMPTZ`、`TEXT`、`BIGSERIAL`、`BOOLEAN`

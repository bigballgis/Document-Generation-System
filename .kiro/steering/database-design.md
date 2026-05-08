---
inclusion: auto
name: database-design
description: Database design — use when authoring Flyway migrations, entities, or SQL
---

# Database design

## Flyway

- Path: `backend/src/main/resources/db/migration/V{version}__{description}.sql`
- New version = next integer after the highest existing `V*__*.sql` in that folder
- One concern per migration; never edit shipped migrations

## Required columns (business tables)

`id BIGSERIAL PRIMARY KEY` + `tenant_id BIGINT NOT NULL REFERENCES tenants(id)` + `created_at TIMESTAMPTZ DEFAULT NOW()` + `updated_at TIMESTAMPTZ DEFAULT NOW()`

## Naming

| Object | Rule | Example |
|--------|------|---------|
| Table | lowercase plural | `parameter_definitions` |
| Column | snake_case | `template_id` |
| Index | `idx_{table}_{cols}` | `idx_parameters_template_id` |
| FK | `fk_{table}_{ref}` | `fk_parameters_templates` |
| Unique | `uq_{table}_{cols}` | `uq_parameters_name` |

## Rules

- Index foreign keys; `tenant_id` must participate in composite indexes where queried
- Production: prefer `CREATE INDEX CONCURRENTLY`
- JSONB for structured config; promote queried fields to columns + GIN when needed
- Types: `TIMESTAMPTZ` / `TEXT` / `BIGSERIAL` / `BOOLEAN`

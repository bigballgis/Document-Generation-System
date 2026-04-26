---
name: migration-safety
description: Handle Flyway migration review and database upgrade planning for this project. Use for tasks involving V30/V36/V39, segment_versions, PostgreSQL schema changes, migration runbooks, or existing-database upgrade risks.
---

# Migration Safety

## Required Context

Read the active task card in:

`docs/audits/full-project-review-2026-04-26/09-task-cards.md`

Also read:

- `AGENTS.md`
- `docs/audits/full-project-review-2026-04-26/04-evidence-index.md`
- Relevant Flyway migration files.

## Rules

- Do not edit migration SQL unless the task explicitly allows it.
- Treat already-applied migrations as immutable unless a migration repair plan is explicitly approved.
- Distinguish new database install behavior from existing database upgrade behavior.
- Document backup requirements before destructive or table-replacement changes.
- Preserve tenant data and template version history unless a product decision explicitly says otherwise.
- Prefer additive migrations for existing production databases.

## Required Questions for Migration Tasks

Before changing migration behavior, confirm:

- Has the migration already run in any environment?
- Is this a new-install-only branch or an upgrade path?
- Must historical `segment_versions` data be preserved?
- What is the rollback strategy?

If answers are missing, stop and report.

## Testing Guidance

- Use Testcontainers PostgreSQL when available.
- Test migration from an empty schema.
- Test migration from a representative previous schema when practical.
- Validate JPA schema compatibility after migration.

## Stop Conditions

Stop and report if:

- Production schema history is unknown.
- Data preservation requirements are unclear.
- A migration would drop or recreate a table with possible user data.
- A migration requires manual DBA steps not documented in the task.

## Useful References

- Flyway documentation should be checked against the project's Flyway version.
- PostgreSQL migration behavior should be validated with PostgreSQL, not only H2.

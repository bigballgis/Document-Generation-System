# Database migration runbook: V30, V36, V39 (`segment_versions` lifecycle)

**Task:** WS-07-T05  
**Date:** 2026-04-26  
**Audience:** operators and engineers applying Flyway migrations to PostgreSQL.

## Scope

This runbook explains **greenfield installs** versus **upgrades** for the Flyway scripts:

- `V30__create_segment_versions.sql`
- `V36__migrate_assembly_config_and_drop_segments.sql`
- `V39__create_segment_versions_inline.sql`

It does **not** change migration SQL. It documents **risk**, **backup expectations**, and **rollback limits**.

## Greenfield (empty database)

1. Deploy an application version whose Flyway baseline includes all migrations through **V39** (and later versions as shipped).
2. Start the backend with a JDBC URL pointing at an **empty** PostgreSQL database and valid credentials.
3. Flyway applies migrations **in version order** (`V3`, …, `V30`, …, `V36`, …, `V39`, …).

**Observed lifecycle of the name `segment_versions`:**

- **After V30:** table `segment_versions` exists with columns keyed to **`segments`** (`segment_id` FK, `version_number`, `file_path`, …).
- **After V36:** that table is **dropped** (with the rest of the segment-library tables). Composite templates have **`assembly_config` rewritten** to inline segment metadata (`filePath`, `name`, `segmentType`, …). Orphan `segmentId` references become disabled rows with `INVALID_SEGMENT_*` names.
- **After V39:** a **new** `segment_versions` table is created for **template-scoped** segment publish history (`tenant_id`, `template_id`, `segment_name`, `version_number`, `config_snapshot`, …). This is **not** a schema evolution of the V30 table; it is a **new** physical table after the old one was removed.

**Greenfield implication:** there is never production data in the V30-shaped `segment_versions` when V36 runs, so the drop is harmless from a data-retention perspective.

## Existing database upgrades

### Preconditions

- Know the current **`flyway_schema_history`** max version on the target database.
- Ensure **no** other migration or DDL is running concurrently.

### Backup requirements (mandatory before V36 on any DB that ever used the segment library)

Take a **full logical backup** (or disk snapshot of the volume) **before** applying **V36** if any of the following is true:

- The database ever ran migrations **through V35** inclusive (segment library era).
- You rely on historical rows in **`segments`**, **`segment_versions` (V30 shape)**, `segment_reviews`, `segment_favorites`, `segment_test_data`, or `segment_tag_mappings`.
- You cannot trivially rebuild composite templates from exports.

**V36 is destructive** to those tables: it runs `DROP TABLE … CASCADE` including **`segment_versions`** (the V30 table). **Flyway does not copy** old `segment_versions` rows into the V39 schema.

### Risk summary by migration

| Migration | Primary risk |
| --- | --- |
| **V30** | Introduces segment-library `segment_versions` tied to `segments`. Safe only while the segment-library model is still the source of truth. |
| **V36** | **Data loss** for all segment-library tables listed in the script; rewrites `assembly_config` for composite templates; deletes `permissions` rows with `resource_type = 'SEGMENT'`. Orphan segment references are preserved as disabled inline placeholders. |
| **V39** | Creates the **new** `segment_versions` table. On upgrades from V36, the table is new/empty until the application writes publish history. No automatic backfill from pre-V36 version rows (those rows are already gone once V36 applied). |

### Upgrade ordering

- **Never** skip Flyway versions. Apply the chain the application ships.
- If you restore a backup to **before V36**, treat the database as a **point-in-time** copy; re-applying forward migrations must follow the same order.

## Rollback limitations

- **Flyway “undo”** is **not** defined for these migrations in the repository; **`migrate` down** is not a supported operational rollback for DDL-heavy steps.
- **Practical rollback:** restore PostgreSQL from backup taken **before** the failing migration, or apply a **forward-fix** migration in a new change request (out of scope for this runbook).
- After **V36** succeeds, the old segment-library schema is gone unless restored from backup.

## Unresolved decisions and follow-ups

1. **Historical segment version payloads:** If the product requires retaining old `segment_versions` (V30) content across V36, that requires a **separate, explicit data migration design** (not present in current V36/V39 scripts). Treat as **product / DBA decision**.
2. **R7 composite ZIP `render-config.json`:** Deferred per [17-composite-r7-render-config-scope.md](17-composite-r7-render-config-scope.md); unrelated to V30/V36/V39 DDL but may affect import/export expectations.
3. **Automated migration tests:**
   - **`SegmentVersionsFlywaySchemaIT`** (extends `BaseIntegrationTest`: PostgreSQL + Redis + MinIO + Spring Flyway on the **integration** profile) asserts **`segment_versions`** matches **V39** (columns, unique constraint, indexes), and that **V36-dropped** segment-library tables (**`segments`**, **`segment_tag_mappings`**, **`segment_reviews`**, **`segment_favorites`**, **`segment_test_data`**) are **absent** on a greenfield database.
   - **`SegmentVersionsFlywayUpgradeIT`** (Testcontainers PostgreSQL + programmatic Flyway) migrates to **V35**, inserts a **minimal** composite template + **`segments`** / legacy **`segment_versions`** row, then migrates to **latest** and asserts **V36** inline rewrite (valid `segmentId` and missing `segmentId` → **`INVALID_SEGMENT_*`**) and **V39** `segment_versions` table shape. This is **not** a full production clone; it is a **bounded** regression for Flyway ordering + the `assembly_config` UPDATE.
   - **`MigrationPropertyTest`** covers V36 **assembly_config** JSON transformation in isolation (Java simulation of the SQL step).
   - **`@Testcontainers(disabledWithoutDocker = true)`** skips these tests when Docker is unavailable (common on laptops and some CI agents).

## Engineering decision: upgrade-path automated tests (amended)

**Date:** 2026-04-26 (amended same day)  
**Status:** Supersedes the earlier “defer all upgrade-path IT” note for a **minimal** automated gate only.

**Decision:** Implement **`SegmentVersionsFlywayUpgradeIT`** as described in *Unresolved decisions* §3. **Do not** claim coverage for arbitrary historical V30 `segment_versions` payloads, large permission matrices, or full application data — those still belong to **staging clones** and DBA review (*Unresolved decisions* §1).

**Rationale for the amendment:**

- A **small deterministic seed** after **V35** exercises the **same Flyway scripts** operators run, without inventing a full customer dump.
- **Docker remains optional** in default `mvn test`; teams that require the gate should run Docker-backed tests in CI or locally before applying **V36** on shared environments.

## Operational checklist (upgrade)

1. Record current Flyway version and application version.
2. Take a verified backup; store off-host per retention policy.
3. Run migrations in a **staging** clone first; smoke-test composite templates (open, render, segment publish if used).
4. Apply to production in a maintenance window if DDL lock time is a concern.
5. After migration, verify `segment_versions` exists with **V39** columns (`tenant_id`, `template_id`, `segment_name`, …), not V30 columns (`segment_id`).

## References

- `backend/src/main/resources/db/migration/V30__create_segment_versions.sql`
- `backend/src/main/resources/db/migration/V36__migrate_assembly_config_and_drop_segments.sql`
- `backend/src/main/resources/db/migration/V39__create_segment_versions_inline.sql`
- `docs/audits/full-project-review-2026-04-26/04-evidence-index.md` (pointers to the same files)

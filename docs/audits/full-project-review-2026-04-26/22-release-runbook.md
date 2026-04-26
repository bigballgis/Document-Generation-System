# Release runbook (build, test, backup, deploy, rollback)

**Task:** WS-07-T06  
**Date:** 2026-04-26  
**Audience:** release managers and on-call engineers.

This document is **platform-agnostic**: substitute your orchestrator (Kubernetes, VM, PaaS, Docker Compose on a server) and secret store. **Do not** embed real credentials, connection strings, or API keys here.

## 1. Pre-release checks

| Check | Notes |
| --- | --- |
| **Change scope** | Release notes list user-visible changes, migrations, and config flag changes. |
| **CI green** | Default quality gate: GitHub Actions workflows under `.github/workflows/` for touched areas (`backend-ci.yml`, `frontend-ci.yml`, `docxtemplater-ci.yml`). Require green on the **merge commit** that will be tagged. |
| **Database plan** | If the release includes Flyway changes, read [21-database-migration-runbook-v30-v36-v39.md](21-database-migration-runbook-v30-v36-v39.md) for **V30 / V36 / V39** risks and backup rules. |
| **Config freeze** | Freeze environment-specific toggles during the window unless a hotfix is approved. |
| **Secrets** | Confirm secrets are injected from the environment or a vault — **never** from git history or chat logs. |
| **Maintenance window** | Agree expected downtime or degraded mode (e.g. long DDL, cold starts). |

## 2. Build and test (parity with CI)

Use the same commands CI uses so local “works on my machine” matches the pipeline.

| Component | Working directory | Commands (representative) |
| --- | --- | --- |
| Backend | `backend/` | `mvn -B test` (or full suite per policy) |
| Frontend | `frontend/` | `npm ci`, `npm run type-check`, `npm test`, `npm run build` |
| Docxtemplater | `docxtemplater-service/` | `npm ci`, `npm test` |

See also: [06-validation-commands.md](06-validation-commands.md).

## 3. Backup (before deploy)

| Asset | Minimum recommendation |
| --- | --- |
| **PostgreSQL** | Logical dump (`pg_dump`) or vendor snapshot **after** quiescing heavy writers if required by RPO. |
| **Object storage (MinIO/S3)** | Bucket-level snapshot or replication-aware backup if templates/documents are irreplaceable. |
| **Redis** | Align with RPO/RTO: snapshot or accept rebuild if data is cache-only. |
| **Release artifact** | Immutable container image digest or build ID recorded in the change ticket. |

**Database rollback reality:** rolling back **only** the application binary **does not** undo applied Flyway migrations. Reverting schema requires **restore from backup** taken before the migration or a **new forward migration** designed for that purpose — see section 6 and the migration runbook.

## 4. Deployment steps (generic)

1. **Announce** start/end of window to stakeholders.
2. **Drain** optional: stop taking new long-running jobs if the product supports it.
3. **Apply database migrations** (if any): typically by starting the new application version against the DB so Flyway runs, **or** by a controlled migration job — follow your standard; ensure **one writer** to `flyway_schema_history` at a time.
4. **Deploy application tiers** in dependency order (e.g. database reachable → Redis/MinIO → Docxtemplater → Spring Boot → frontend/edge).
5. **Wait for health checks** to pass before sending user traffic (see section 5).
6. **Enable traffic** or complete blue/green switch.

For **local Docker Compose** smoke validation (non-production), use the project’s compose file and environment templates; do not reuse production secrets on developer laptops.

## 5. Post-release smoke tests

Run a **short** scripted checklist after health goes green:

| Target | Suggested check |
| --- | --- |
| **Spring Boot** | `GET /actuator/health` or `/actuator/health/ping` (as configured in deployment). |
| **Docxtemplater** | `GET /health` on the Node service port. |
| **Frontend** | Load login or static landing page; confirm no 5xx from edge. |
| **Critical path** | Login, open one template workspace, render or generate a trivial document (tenant-scoped). |

Record pass/fail and timestamps in the change ticket.

## 6. Rollback limitations

| Layer | Typical action | Limitation |
| --- | --- | --- |
| **Application** | Redeploy previous image/build | Fast if artifacts are retained. |
| **Frontend assets** | Revert CDN or previous container | Same as above. |
| **Database** | **Not** automatic with app rollback | Without a prior backup or forward-fix migration, **schema cannot be safely reverted**. Plan backups per section 3. |
| **Object storage** | Restore from backup if bad writes occurred | Versioning/lifecycle policies matter. |

If a release **must** be reverted after destructive DDL, treat it as an **incident**: restore DB from backup to a new instance or point-in-time, then reconcile data drift.

## 7. Related documents

- [21-database-migration-runbook-v30-v36-v39.md](21-database-migration-runbook-v30-v36-v39.md) — Flyway V30/V36/V39 and `segment_versions`.
- [06-validation-commands.md](06-validation-commands.md) — command reference.
- [11-execution-sequence.md](11-execution-sequence.md) — remediation ordering.

## 8. Post-release

- Monitor error rates and latency for **24–48h** (or per SLO).
- Close the change ticket; attach CI links and smoke results.
- Schedule **npm audit** / dependency hygiene outside the critical path if not part of this release.

---
name: local-deployment-operations
description: Run and troubleshoot local deployment for this repository. Use when starting Docker Compose, validating local services, checking health endpoints, configuring .env for local use, or performing local smoke tests after remediation.
---

# Local Deployment Operations

## Required Context

Read:

- `AGENTS.md`
- `docs/audits/full-project-review-2026-04-26/06-validation-commands.md`
- `docs/audits/full-project-review-2026-04-26/11-execution-sequence.md`
- Active task card if deployment is part of a remediation task.

## Local Only

This skill is for local development and isolated test environments only.

Do not:

- Use production secrets.
- Connect to production databases.
- Run destructive database operations.
- Expose services to public networks.
- Treat local Compose readiness as production readiness.

## Preflight

Before running local deployment commands:

- Check whether required terminal processes are already running.
- Confirm `.env` exists or document that `.env.example` must be copied manually by the user.
- Verify Docker is available if a Docker command is required.
- Review `docker-compose.yml` for exposed ports.
- Confirm the task does not require production-like security settings.

## Common Commands

Validate Compose configuration:

```powershell
docker compose config
```

Build services:

```powershell
docker compose build
```

Start services:

```powershell
docker compose up
```

Start in detached mode only when the task explicitly needs background services:

```powershell
docker compose up -d
```

Check service status:

```powershell
docker compose ps
```

Stop local services:

```powershell
docker compose down
```

## Local Smoke Checks

Use only when services are intentionally running:

- Backend health endpoint.
- Docxtemplater `/health`.
- Frontend loads through Nginx.
- MinIO readiness.
- PostgreSQL and Redis container health.

Do not run smoke checks against production hosts.

## Troubleshooting Order

1. Check container status.
2. Check service logs.
3. Check environment variables.
4. Check health checks.
5. Check network names and internal URLs.
6. Check local port conflicts.

## Stop Conditions

Stop and report if:

- Docker is unavailable.
- Required environment variables are missing and cannot be safely defaulted.
- A command would delete volumes or data.
- A service attempts to connect to production.
- Ports conflict with existing user processes.
- A database migration failure occurs.

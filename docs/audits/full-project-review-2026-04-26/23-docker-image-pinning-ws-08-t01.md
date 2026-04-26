# Docker Compose image pinning (WS-08-T01)

**Task:** WS-08-T01  
**Date:** 2026-04-26

## Goal

Replace **floating upstream** image references in `docker-compose.yml` with **pinned** tags or digests where a safe target is known.

## Changes

| Service | Before | After | Notes |
| --- | --- | --- | --- |
| **onlyoffice** | `ghcr.io/euro-office/documentserver:latest` | `ghcr.io/euro-office/documentserver@sha256:8e78211dd45407c92d5f60193ecb3f23be371fd3a359a65bef2f9310f09b3d0b` | Resolves to the `latest` manifest at pin time (2026-04-26). **Upgrade:** pull a newer digest or switch to a published semver tag when Euro-Office publishes one. |
| **minio** | `minio/minio:latest` | `minio/minio:RELEASE.2025-04-08T15-41-24Z` | Official **RELEASE** tag format. **Upgrade:** choose a newer `RELEASE.*` from [Docker Hub tags](https://hub.docker.com/r/minio/minio/tags) after testing. |
| **redis** | `redis:7.2-alpine` | `redis:7.2.7-alpine` | Pins patch level on the 7.2 Alpine line. |
| **postgres** | `postgres:16.5` | *(unchanged)* | Already pinned to a minor release. Optional future hardening: `postgres:16.5-bookworm` or digest pin. |

## Local build `image:` names (`:latest`)

Services **frontend**, **app**, and **docxtemplater** use `build:` plus `image: docgen-*:latest`. These names tag **locally built** images for Compose project reuse; they are **not** pulls of a floating registry `latest`. Renaming to `:local` is optional cosmetic hygiene and was **not** required for WS-08-T01.

## Validation

```powershell
docker compose config
```

Expected: exit code **0** (YAML and references resolve).

## Conservative upgrade guidance

- **PostgreSQL / Redis / MinIO:** read upstream release notes before bumping; take a **backup** before major jumps (see [21-database-migration-runbook-v30-v36-v39.md](21-database-migration-runbook-v30-v36-v39.md) for DB-specific risks).
- **Document Server:** verify editor + JWT + callback integration after digest bumps.

# Non-root runtime users in application Dockerfiles (WS-08-T04)

**Task:** WS-08-T04  
**Date:** 2026-04-26

## Purpose

Run **built** application images as **non-root** users while keeping **listen :80** for the frontend (Compose port mapping unchanged) and preserving **LibreOffice** + **Node** behavior for Docxtemplater.

## Changes

| Image | User | Notes |
| --- | --- | --- |
| **Backend** (`backend/Dockerfile`, `backend/Dockerfile.local`) | `app` (UID **10001**) | `chown` on `app.jar`; JVM uses system `/tmp`. |
| **Frontend** (`frontend/Dockerfile`, `frontend/Dockerfile.local`) | `nginx` (UID **101**) | **`cap_net_bind_service`** on `/usr/sbin/nginx` at build time, then **`libcap` removed**; cache/log/conf dirs owned by `nginx`; `/etc/nginx/templates` created when absent on base image. |
| **Docxtemplater** (`docxtemplater-service/Dockerfile`) | `node` (UID **1000**) | `chown -R node:node /app`; `HOME=/app` for writable profile/cache paths used by LibreOffice/UNO. |

## Constraints respected

- **Compose networking** unchanged (no `ports` edits).
- **Named volumes** unchanged.
- **Entrypoints / commands** unchanged (`java -jar`, `nginx -g 'daemon off;'`, `node server.js`).

## Risks and follow-ups

- **File capabilities:** Nginx retains `cap_net_bind_service` on the binary (not a broad `privileged` container). Some hardened policies disallow file caps; in that case use an unprivileged image on **>1024** ports and adjust Compose in a dedicated task (out of scope for WS-08-T04).
- **Docxtemplater:** If headless LibreOffice fails under `node` in a specific workload, capture logs and revisit `HOME`, `TMPDIR`, or dedicated writable paths without widening filesystem permissions unnecessarily.

## Validation

```powershell
docker compose build
```

Expected: exit code **0** for all services with a `build:` section.

Runtime UID check (override entrypoint so Spring Boot does not start):

```powershell
docker run --rm --entrypoint id docgen-frontend:latest
docker run --rm --entrypoint id docgen-app:latest
docker run --rm --entrypoint id docgen-docxtemplater:latest
```

Expected: `nginx`, `app` (10001), `node` respectively — not `root`.

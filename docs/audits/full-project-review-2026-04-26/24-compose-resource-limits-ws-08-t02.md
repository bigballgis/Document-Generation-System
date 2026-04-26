# Docker Compose resource limits (WS-08-T02)

**Task:** WS-08-T02  
**Date:** 2026-04-26

## Purpose

Add **conservative** `deploy.resources` memory limits and reservations to root `docker-compose.yml` for local and small-machine use. Values are **guidance** for the Compose engine; some Docker setups apply them only when using compatible runtimes.

## Limits (memory)

| Service | Limit | Reservation | Rationale |
| --- | --- | --- | --- |
| **frontend** | 512M | 64M | Nginx + static assets. |
| **app** | 2G | 512M | Spring Boot JVM + headroom. |
| **docxtemplater** | 3G | 512M | Node + LibreOffice conversion spikes. |
| **onlyoffice** | 4G | 1G | Document Server editor footprint. |
| **postgres** | 1G | 256M | OLTP default for dev datasets. |
| **redis** | 512M | 128M | Cache / session store. |
| **minio** | 1G | 256M | Object API + console. |

**CPU limits** were intentionally omitted to avoid throttling JVM/LO on laptops with few cores; add `cpus` under `limits` in production if your platform requires it.

## Tuning

- Increase **onlyoffice** and **docxtemplater** limits if editors time out or OOM during large documents.
- Decrease limits on constrained hosts only if services remain stable after smoke tests.

## Validation

```powershell
docker compose config
```

Expected: exit code **0**.

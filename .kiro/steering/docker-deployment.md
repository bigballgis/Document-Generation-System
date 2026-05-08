---
inclusion: auto
name: docker-deployment
description: Docker and deployment — use when editing Dockerfiles, Compose, or k8s manifests
---

# Docker and deployment

## Topology

```
frontend(80/Nginx) → app(8080/Spring Boot) → postgres(5432) + redis(6379) + minio(9000)
                                              docxtemplater(3000/Node.js) + onlyoffice(8088)
```

Startup order: postgres → redis → minio → docxtemplater → app → frontend

## Dockerfiles

| Service | Base image | Healthcheck |
|---------|------------|-------------|
| Backend | Multi-stage Maven → `eclipse-temurin:17-jre-alpine` | `wget -qO- http://localhost:8080/actuator/health/ping` |
| Frontend | Multi-stage Node → `nginx:alpine` | — |
| Docxtemplater | Node.js + LibreOffice headless | `curl -f http://localhost:3000/health` |

- Reference: #[[file:backend/Dockerfile.local]] #[[file:docker-compose.yml]]

## Environment

- Local: `.env` (gitignored) / template: #[[file:.env.example]]
- Prod: K8s Secrets + ConfigMaps (#[[file:k8s/secret.yaml]] #[[file:k8s/configmap.yaml]])
- Secrets: `DB_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`, `ENCRYPTION_KEY`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`

## Build commands

```bash
# Backend
docker build -f Dockerfile.local -t docgen-app:latest .       # cwd: backend/
# Frontend
docker build -f Dockerfile.local -t docgen-frontend:latest .  # cwd: frontend/
# Run stack
docker compose up -d
```

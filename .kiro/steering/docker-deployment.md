---
inclusion: auto
name: docker-deployment
description: Docker 与部署规范。在修改 Dockerfile、docker-compose 或 k8s 配置时使用。
---

# Docker 与部署规范

## 服务架构

frontend(80/Nginx) → app(8080/Spring Boot) → postgres(5432) + redis(6379) + minio(9000)
docxtemplater(3000/Node.js) + onlyoffice(8088)

启动顺序: postgres → redis → minio → docxtemplater → app → frontend

## Dockerfile 规范

- 后端: 多阶段 Maven→`eclipse-temurin:17-jre-alpine`，healthcheck `wget -qO- http://localhost:8080/actuator/health/ping`
- 前端: 多阶段 Node→`nginx:alpine`，构建参数通过 ARG 传入
- Docxtemplater: Node.js + LibreOffice Headless，healthcheck `curl -f http://localhost:3000/health`

## 环境变量

- 本地: `.env` (gitignored) / 示例: `.env.example` (tracked)
- 生产: K8s Secrets + ConfigMaps
- 敏感: `DB_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`, `ENCRYPTION_KEY`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`

## K8s

- 配置路径: `k8s/`，Deployment + Service + Ingress
- 所有服务必须配置 healthcheck + `depends_on: condition: service_healthy`

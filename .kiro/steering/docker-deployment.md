---
inclusion: auto
name: docker-deployment
description: Docker 与部署规范。在修改 Dockerfile、docker-compose 或 k8s 配置时使用。
---

# Docker 与部署规范

## 服务拓扑

```
frontend(80/Nginx) → app(8080/Spring Boot) → postgres(5432) + redis(6379) + minio(9000)
                                              docxtemplater(3000/Node.js) + onlyoffice(8088)
```

启动顺序: postgres → redis → minio → docxtemplater → app → frontend

## Dockerfile

| 服务 | 基础镜像 | Healthcheck |
|------|----------|-------------|
| 后端 | 多阶段 Maven → `eclipse-temurin:17-jre-alpine` | `wget -qO- http://localhost:8080/actuator/health/ping` |
| 前端 | 多阶段 Node → `nginx:alpine` | — |
| Docxtemplater | Node.js + LibreOffice Headless | `curl -f http://localhost:3000/health` |

- 参考: #[[file:backend/Dockerfile.local]] #[[file:docker-compose.yml]]

## 环境变量

- 本地: `.env` (gitignored) / 示例: #[[file:.env.example]]
- 生产: K8s Secrets + ConfigMaps (#[[file:k8s/secret.yaml]] #[[file:k8s/configmap.yaml]])
- 敏感: `DB_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`, `ENCRYPTION_KEY`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`

## 构建命令

```bash
# 后端
docker build -f Dockerfile.local -t docgen-app:latest .       # cwd: backend/
# 前端
docker build -f Dockerfile.local -t docgen-frontend:latest .   # cwd: frontend/
# 部署
docker-compose up -d
```

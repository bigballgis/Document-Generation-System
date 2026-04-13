---
description: Docker 与部署规范，包括服务架构、Dockerfile 规范、环境变量管理和 Kubernetes 部署
inclusion: auto
fileMatchPattern: '**/Dockerfile*,**/docker-compose*,**/k8s/**'
---

# Docker 与部署规范

## 服务架构

| 服务 | 端口 | 技术 | 说明 |
|------|------|------|------|
| frontend | 80 | Nginx + Vue 3 | 前端静态资源 |
| app | 8080 | Spring Boot | 后端主应用 |
| docxtemplater | 3000 | Node.js + Express | 文档渲染 + PDF 转换 |
| onlyoffice | 8088/8443 | OnlyOffice Document Server | 在线编辑器 |
| postgres | 5432 | PostgreSQL 16.5 | 主数据库 |
| redis | 6379 | Redis 7.2 Alpine | 缓存/限流/队列 |
| minio | 9000/9001 | MinIO | 对象存储 |

## Dockerfile 规范

### 后端 (Spring Boot)
- 多阶段构建: Maven 构建 → JRE 运行
- 基础镜像: `eclipse-temurin:17-jre-alpine`
- 健康检查: `wget -qO- http://localhost:8080/actuator/health/ping`

### 前端 (Vue 3)
- 多阶段构建: Node 构建 → Nginx 运行
- 基础镜像: `nginx:alpine`
- 构建参数: `VITE_ONLYOFFICE_URL` 等通过 ARG 传入

### Docxtemplater 服务
- 基础镜像: 包含 LibreOffice Headless 的 Node.js 镜像
- 健康检查: `curl -f http://localhost:3000/health`

## 环境变量管理

- 本地开发: `.env` 文件 (git ignored)
- 示例配置: `.env.example` (git tracked)
- 生产环境: Kubernetes Secrets / ConfigMaps
- 敏感变量: `DB_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`, `ENCRYPTION_KEY`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`

## Kubernetes 部署

- 配置文件路径: `k8s/`
- 使用 Deployment + Service + Ingress
- 敏感信息使用 Secret
- 配置信息使用 ConfigMap
- 健康检查使用 livenessProbe + readinessProbe

## 服务依赖顺序

```
postgres → redis → minio → docxtemplater → app → frontend
```

所有服务必须配置 healthcheck，下游服务通过 `depends_on: condition: service_healthy` 等待上游就绪。

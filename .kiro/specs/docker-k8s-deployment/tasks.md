# 实现计划：Docker 与 Kubernetes 部署

## 概述

按依赖顺序实现 DocGen 系统的容器化部署能力：先完成基础 Dockerfile 和配置文件，再修改 Docker Compose 编排，然后创建 K8s 清单，最后编写部署脚本。所有任务围绕基础设施配置文件的创建和修改展开。

## 任务

- [x] 1. 前端容器化基础文件
  - [x] 1.1 创建 `frontend/.dockerignore` 文件
    - 排除 `node_modules`、`dist`、`.git`、`*.log`、`.env*` 等不必要文件
    - _需求: 1.3_

  - [x] 1.2 创建 `frontend/nginx/default.conf.template` Nginx 配置模板
    - 配置 `/api/`、`/actuator/`、`/swagger-ui.html`、`/api-docs/` 反向代理到 `${BACKEND_URL}`
    - 使用 `set $backend` + `proxy_pass $backend` 实现动态 DNS 解析
    - 配置 `resolver 127.0.0.11 valid=30s`（Docker 内置 DNS）
    - 设置代理超时：connect 60s、read 300s、send 60s
    - 配置 Vue Router history 模式 `try_files $uri $uri/ /index.html`
    - 配置静态资源长期缓存（Vite 构建产物带 hash）
    - _需求: 1.4, 1.5, 1.6_

  - [x] 1.3 创建 `frontend/Dockerfile` 多阶段构建文件
    - 阶段 1（build）：基于 `node:18-alpine`，使用 `ARG VITE_ONLYOFFICE_URL` 传入构建时变量，执行 `npm ci` + `npm run build`
    - 阶段 2（runtime）：基于 `nginx:1.25-alpine`，复制构建产物到 `/usr/share/nginx/html`，复制 Nginx 模板到 `/etc/nginx/templates/`
    - 设置 `ENV NGINX_ENVSUBST_FILTER=BACKEND_URL` 限定 envsubst 只替换 `BACKEND_URL`
    - 设置 `ENV BACKEND_URL=http://app:8080` 默认值
    - _需求: 1.1, 1.2, 1.5_

- [x] 2. 后端 Dockerfile 增强
  - [x] 2.1 修改 `backend/Dockerfile`，在运行阶段添加 `curl` 安装
    - 在 `eclipse-temurin:17-jre-alpine` 阶段添加 `RUN apk add --no-cache curl`
    - 确保健康检查命令可用
    - _需求: 3.9_

- [x] 3. 检查点 - 确认 Dockerfile 文件正确
  - 确保所有 Dockerfile 和配置文件语法正确，如有疑问请询问用户。

- [x] 4. Docker Compose 环境变量统一与服务编排
  - [x] 4.1 修改 `docker-compose.yml` 中 DocGen_Backend 服务的环境变量
    - 将 `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` 替换为 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD`
    - 将 `SPRING_DATA_REDIS_HOST/PORT`、`SPRING_REDIS_PASSWORD` 替换为 `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`
    - 将 `SPRING_MAIL_*` 替换为 `MAIL_HOST`、`MAIL_PORT`、`MAIL_USERNAME`、`MAIL_PASSWORD`
    - 添加 `ONLYOFFICE_JWT_SECRET` 环境变量
    - _需求: 2.1, 2.2, 2.3_

  - [x] 4.2 在 `docker-compose.yml` 中为所有服务添加健康检查
    - PostgreSQL: `pg_isready -U docgen -d docgen`，间隔 10s，超时 5s，重试 5 次
    - Redis: `redis-cli --no-auth-warning -a $$REDIS_PASSWORD ping`，间隔 10s，超时 5s，重试 5 次
    - MinIO: `mc ready local`，间隔 10s，超时 5s，重试 5 次
    - Docxtemplater: `curl -f http://localhost:3000/health`，间隔 15s，超时 5s，重试 3 次
    - Backend: `curl -f http://localhost:8080/actuator/health`，间隔 30s，超时 10s，重试 5 次，启动等待 60s
    - _需求: 3.3, 3.4, 3.5, 3.6, 3.7_

  - [x] 4.3 在 `docker-compose.yml` 中添加 DocGen_Frontend 服务定义
    - 构建上下文 `./frontend`，传入 `VITE_ONLYOFFICE_URL` 构建参数（默认 `http://localhost:8443`）
    - 映射端口 80:80
    - 设置 `BACKEND_URL=http://app:8080` 环境变量
    - 配置 `depends_on` 使用 `condition: service_healthy` 依赖 Backend
    - _需求: 3.1, 3.2_

  - [x] 4.4 修改 `docker-compose.yml` 中现有服务的 `depends_on` 为健康检查条件
    - Backend 依赖 PostgreSQL、Redis、MinIO 的 `service_healthy` 条件
    - Docxtemplater 依赖 MinIO 的 `service_healthy` 条件（如需要）
    - _需求: 3.8_

  - [x] 4.5 更新 `.env.example` 文件
    - 补充完整变量列表，包含所有服务所需的环境变量
    - 每个变量附带中文注释说明用途
    - 变量名与 `application.yml` 保持一致
    - _需求: 2.4_

- [x] 5. 检查点 - 验证 Docker Compose 配置
  - 确保 `docker-compose.yml` 语法正确，环境变量映射与 `application.yml` 一致，如有疑问请询问用户。

- [x] 6. Kubernetes 基础资源清单
  - [x] 6.1 创建 `k8s/namespace.yaml`
    - 定义 `docgen` 命名空间
    - _需求: 4.1_

  - [x] 6.2 创建 `k8s/configmap.yaml`
    - 定义 `docgen-config` ConfigMap，包含外部 PostgreSQL 连接信息、Redis/MinIO/Docxtemplater/OnlyOffice 内部地址、邮件非敏感配置
    - 所有配置项附带注释说明
    - 注释说明首次部署前需确保外部 PostgreSQL 已创建 `docgen` 数据库
    - _需求: 5.1, 5.4_

  - [x] 6.3 创建 `k8s/secret.yaml`
    - 定义 `docgen-secrets` Secret，包含数据库凭证、Redis 密码、MinIO 凭证、JWT 密钥、加密密钥、OnlyOffice JWT 密钥、邮件凭证
    - 使用 base64 编码占位符，附带注释说明
    - _需求: 5.2_

- [x] 7. Kubernetes 有状态服务部署（Redis、MinIO、OnlyOffice）
  - [x] 7.1 创建 `k8s/redis-pvc.yaml`、`k8s/redis-deployment.yaml`、`k8s/redis-service.yaml`
    - PVC 默认 2Gi
    - Deployment 引用 Secret 中的 `REDIS_PASSWORD`
    - 配置 liveness 探针：exec `redis-cli ping`，间隔 15s
    - Service 类型 ClusterIP，端口 6379
    - 镜像引用位置添加注释提示替换为私有仓库地址
    - _需求: 4.6, 4.8, 4.11, 6.7_

  - [x] 7.2 创建 `k8s/minio-pvc.yaml`、`k8s/minio-deployment.yaml`、`k8s/minio-service.yaml`
    - PVC 默认 20Gi
    - Deployment 引用 Secret 中的 MinIO 凭证
    - 配置 liveness 探针：HTTP GET `/minio/health/live`，间隔 15s
    - Service 类型 ClusterIP，端口 9000 和 9001
    - 镜像引用位置添加注释提示替换为私有仓库地址
    - _需求: 4.7, 4.8, 4.11, 6.8_

  - [x] 7.3 创建 `k8s/onlyoffice-pvc.yaml`、`k8s/onlyoffice-deployment.yaml`、`k8s/onlyoffice-service.yaml`
    - PVC 默认 8Gi
    - Deployment 引用 Secret 中的 `ONLYOFFICE_JWT_SECRET`
    - Service 类型 ClusterIP，端口 80 和 443
    - 镜像引用位置添加注释提示替换为私有仓库地址
    - _需求: 4.5, 4.8, 4.11_

- [x] 8. Kubernetes 应用服务部署（Backend、Frontend、Docxtemplater）
  - [x] 8.1 创建 `k8s/backend-deployment.yaml` 和 `k8s/backend-service.yaml`
    - Deployment 通过 `envFrom` 引用 ConfigMap 和 Secret
    - 资源限制：requests cpu 500m / memory 1Gi，limits cpu 2 / memory 2Gi
    - liveness 探针：HTTP GET `/actuator/health`，初始延迟 60s，间隔 30s，超时 5s，失败阈值 3
    - readiness 探针：HTTP GET `/actuator/health`，初始延迟 30s，间隔 10s，超时 5s，失败阈值 3
    - Service 类型 ClusterIP，端口 8080
    - 镜像引用位置添加注释提示替换为私有仓库地址
    - _需求: 4.2, 4.8, 4.10, 4.11, 5.3, 6.1, 6.2_

  - [x] 8.2 创建 `k8s/frontend-deployment.yaml` 和 `k8s/frontend-service.yaml`
    - Deployment 设置 `BACKEND_URL=http://docgen-backend:8080` 环境变量
    - 资源限制：requests cpu 100m / memory 128Mi，limits cpu 500m / memory 256Mi
    - liveness 探针：HTTP GET `/`，间隔 30s
    - readiness 探针：HTTP GET `/`，初始延迟 5s，间隔 10s
    - Service 类型 ClusterIP，端口 80
    - 镜像引用位置添加注释提示替换为私有仓库地址
    - _需求: 4.3, 4.8, 4.11, 6.3, 6.4_

  - [x] 8.3 创建 `k8s/docxtemplater-deployment.yaml` 和 `k8s/docxtemplater-service.yaml`
    - Deployment 通过 `envFrom` 引用 ConfigMap 和 Secret（MinIO 凭证等）
    - 资源限制：requests cpu 500m / memory 512Mi，limits cpu 2 / memory 2Gi
    - liveness 探针：HTTP GET `/health`，初始延迟 30s，间隔 30s
    - readiness 探针：HTTP GET `/health`，初始延迟 15s，间隔 10s
    - Service 类型 ClusterIP，端口 3000
    - 镜像引用位置添加注释提示替换为私有仓库地址
    - _需求: 4.4, 4.8, 4.11, 6.5, 6.6_

- [x] 9. Kubernetes Ingress 与 Kustomization
  - [x] 9.1 创建 `k8s/ingress.yaml`
    - 将外部流量路由到 Frontend Service
    - 支持配置自定义域名（使用占位符 `docgen.example.com`）
    - _需求: 4.9_

  - [x] 9.2 创建 `k8s/kustomization.yaml`
    - 按正确顺序引用所有 K8s 资源文件
    - 支持 `kubectl apply -k k8s/` 一键部署
    - _需求: 7.5_

- [x] 10. 检查点 - 验证 K8s 清单
  - 确保所有 K8s YAML 文件语法正确，资源引用关系完整，如有疑问请询问用户。

- [x] 11. 部署脚本
  - [x] 11.1 创建 `deploy.sh` 本地 Docker 部署脚本
    - 检查 `docker` 和 `docker compose` 是否可用
    - 检查 `.env` 文件是否存在，不存在则从 `.env.example` 复制并提示修改
    - 执行 `docker compose build` 构建镜像
    - 执行 `docker compose up -d` 启动服务
    - 轮询等待各服务健康检查通过
    - 输出访问地址（前端 http://localhost:80，后端 http://localhost:8080）
    - 失败时输出对应服务日志
    - 设置可执行权限
    - _需求: 7.1, 7.2, 7.6_

  - [x] 11.2 创建 `k8s/k8s-deploy.sh` K8s 部署脚本
    - 检查 `kubectl` 是否可用并能连接集群
    - 按顺序应用资源：namespace → configmap/secret → pvc → deployments/services → ingress
    - 等待各 Deployment rollout 完成
    - 失败时输出 Pod 日志和诊断信息
    - 设置可执行权限
    - _需求: 7.3, 7.4, 7.6_

- [x] 12. 最终检查点 - 确认所有文件完整
  - 确保所有文件已创建，环境变量映射一致，依赖关系正确，如有疑问请询问用户。

## 备注

- 本功能为基础设施部署配置，不涉及业务逻辑代码，不适用属性基测试（PBT）
- 每个任务引用了具体的需求编号以确保可追溯性
- 检查点任务用于阶段性验证，确保增量正确性
- K8s 清单不包含 PostgreSQL Deployment，使用公司自建的外部数据库

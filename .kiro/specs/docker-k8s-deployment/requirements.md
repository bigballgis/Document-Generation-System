# 需求文档：Docker 与 Kubernetes 部署

## 简介

为现有的低代码文档生成系统（DocGen）添加完整的容器化部署能力，包括本地 Docker Compose 一键部署和公司 Kubernetes 集群部署。本地部署使用 Docker Compose 编排所有服务（含 PostgreSQL），K8s 部署使用公司自建的外部 PostgreSQL 数据库。

## 术语表

- **DocGen_Backend**: 基于 Spring Boot 3.2 + Java 17 的后端应用服务
- **DocGen_Frontend**: 基于 Vue 3 + TypeScript 的前端 Web 应用，使用 Vite 构建，Nginx 提供静态文件服务
- **Docxtemplater_Service**: 基于 Node.js 的文档模板渲染服务，集成 LibreOffice
- **OnlyOffice**: 在线文档编辑服务（OnlyOffice Document Server）
- **Nginx_Proxy**: DocGen_Frontend 容器内的 Nginx 服务器，负责静态文件服务和 API 反向代理
- **Docker_Compose_Stack**: 使用 docker-compose.yml 编排的本地完整服务栈
- **K8s_Manifests**: Kubernetes 部署清单文件集合，包含 Deployment、Service、ConfigMap、Secret、Ingress 等资源定义
- **Health_Probe**: Kubernetes 中用于检测容器健康状态的探针，包括 liveness 和 readiness 两种类型
- **External_PostgreSQL**: 公司自建的 PostgreSQL 数据库实例，不在 K8s 集群内部署

## 需求

### 需求 1：前端容器化构建

**用户故事：** 作为开发者，我希望将 Vue 3 前端应用容器化，以便在 Docker 和 K8s 环境中统一部署。

#### 验收标准

1. THE DocGen_Frontend SHALL 提供一个多阶段构建的 Dockerfile，第一阶段使用 Node 18 构建 Vue 3 应用，第二阶段使用 Nginx Alpine 镜像提供静态文件服务
2. WHEN DocGen_Frontend 的 Dockerfile 执行构建时，THE DocGen_Frontend SHALL 使用 `npm ci` 安装依赖并使用 `npm run build`（内含 `vue-tsc && vite build`）生成生产构建产物
3. THE DocGen_Frontend SHALL 提供 `.dockerignore` 文件，排除 `node_modules`、`dist`、`.git`、`*.log` 等不必要的文件以减小构建上下文
4. THE DocGen_Frontend SHALL 提供 Nginx 配置文件（`nginx.conf`），包含以下路由规则：
   - 将 `/api` 路径的请求反向代理到 DocGen_Backend 服务
   - 将 `/actuator` 路径的请求反向代理到 DocGen_Backend 服务
   - 将 `/swagger-ui.html` 和 `/api-docs` 路径的请求反向代理到 DocGen_Backend 服务
   - 所有其他路径返回 `index.html` 以支持 Vue Router 的 history 模式
5. THE Nginx_Proxy SHALL 使用 `envsubst` 机制在容器启动时从环境变量 `BACKEND_URL`（默认值 `http://app:8080`）动态生成 Nginx 配置，以便在不同部署环境中灵活配置后端地址
6. THE Nginx 配置 SHALL 设置合理的代理超时时间（proxy_connect_timeout 60s、proxy_read_timeout 300s），以支持大型文档生成等长时间请求

### 需求 2：Docker Compose 环境变量统一

**用户故事：** 作为开发者，我希望 docker-compose.yml 中的环境变量与 Spring Boot application.yml 中的变量名保持一致，以避免配置混乱。

#### 验收标准

1. THE Docker_Compose_Stack SHALL 修改 DocGen_Backend 服务的环境变量，使用 application.yml 中定义的变量名（`DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD`）替代当前的 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD`
2. THE Docker_Compose_Stack SHALL 修改 Redis 相关环境变量，使用 `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD` 替代当前的 `SPRING_DATA_REDIS_HOST`、`SPRING_DATA_REDIS_PORT`、`SPRING_REDIS_PASSWORD`
3. THE Docker_Compose_Stack SHALL 修改邮件相关环境变量，使用 `MAIL_HOST`、`MAIL_PORT`、`MAIL_USERNAME`、`MAIL_PASSWORD` 替代当前的 `SPRING_MAIL_HOST`、`SPRING_MAIL_PORT`、`SPRING_MAIL_USERNAME`、`SPRING_MAIL_PASSWORD`
4. THE Docker_Compose_Stack SHALL 更新 `.env.example` 文件，包含所有服务所需的环境变量，每个变量附带中文注释说明用途

### 需求 3：Docker Compose 本地完整部署

**用户故事：** 作为开发者，我希望通过 `docker-compose up` 一键启动所有服务，以便在本地快速搭建完整的开发和测试环境。

#### 验收标准

1. THE Docker_Compose_Stack SHALL 在现有 docker-compose.yml 中添加 DocGen_Frontend 服务定义，映射端口 80 到容器的 80 端口
2. THE Docker_Compose_Stack SHALL 配置 DocGen_Frontend 服务依赖 DocGen_Backend 服务（`depends_on` 使用 `condition: service_healthy`）
3. THE Docker_Compose_Stack SHALL 为 DocGen_Backend 服务配置健康检查，使用 `curl -f http://localhost:8080/actuator/health` 命令，间隔 30 秒，超时 10 秒，重试 5 次，启动等待期 60 秒
4. THE Docker_Compose_Stack SHALL 为 PostgreSQL 服务配置健康检查，使用 `pg_isready -U docgen -d docgen` 命令，间隔 10 秒，超时 5 秒，重试 5 次
5. THE Docker_Compose_Stack SHALL 为 Redis 服务配置健康检查，使用 `redis-cli -a $REDIS_PASSWORD ping` 命令，间隔 10 秒，超时 5 秒，重试 5 次
6. THE Docker_Compose_Stack SHALL 为 MinIO 服务配置健康检查，使用 `curl -f http://localhost:9000/minio/health/live` 命令，间隔 10 秒，超时 5 秒，重试 5 次
7. THE Docker_Compose_Stack SHALL 为 Docxtemplater_Service 服务配置健康检查，使用 `curl -f http://localhost:3000/health` 命令，间隔 15 秒，超时 5 秒，重试 3 次
8. WHEN DocGen_Backend 服务启动时，THE Docker_Compose_Stack SHALL 确保 PostgreSQL、Redis、MinIO 服务已通过健康检查（使用 `depends_on` 的 `condition: service_healthy`）
9. THE Docker_Compose_Stack SHALL 为 DocGen_Backend 的 Dockerfile 添加 `curl` 工具安装（Alpine 镜像默认不含 curl），以支持健康检查命令

### 需求 4：Kubernetes 部署清单

**用户故事：** 作为运维工程师，我希望拥有完整的 K8s 部署清单，以便将 DocGen 系统部署到公司 Kubernetes 集群。

#### 验收标准

1. THE K8s_Manifests SHALL 提供 Namespace 资源定义，创建 `docgen` 命名空间
2. THE K8s_Manifests SHALL 为 DocGen_Backend 提供 Deployment 资源定义，包含资源限制（requests: cpu 500m / memory 1Gi，limits: cpu 2 / memory 2Gi）
3. THE K8s_Manifests SHALL 为 DocGen_Frontend 提供 Deployment 资源定义，包含资源限制（requests: cpu 100m / memory 128Mi，limits: cpu 500m / memory 256Mi）
4. THE K8s_Manifests SHALL 为 Docxtemplater_Service 提供 Deployment 资源定义，包含资源限制（requests: cpu 500m / memory 512Mi，limits: cpu 2 / memory 2Gi），因为 LibreOffice PDF 转换需要较多资源
5. THE K8s_Manifests SHALL 为 OnlyOffice 提供 Deployment 资源定义，包含持久卷声明（PVC，默认 8Gi）
6. THE K8s_Manifests SHALL 为 Redis 提供 Deployment 资源定义，包含持久卷声明（PVC，默认 2Gi）
7. THE K8s_Manifests SHALL 为 MinIO 提供 Deployment 资源定义，包含持久卷声明（PVC，默认 20Gi）
8. THE K8s_Manifests SHALL 为每个 Deployment 提供对应的 Service 资源定义（ClusterIP 类型）
9. THE K8s_Manifests SHALL 提供 Ingress 资源定义，将外部流量路由到 DocGen_Frontend 服务，支持配置自定义域名
10. THE K8s_Manifests SHALL 不包含 PostgreSQL 的 Deployment（使用公司自建的外部数据库），通过 ConfigMap 和 Secret 配置外部数据库连接信息
11. THE K8s_Manifests SHALL 使用注释标注所有镜像引用位置，提示运维人员替换为公司私有镜像仓库地址

### 需求 5：K8s 外部数据库与配置管理

**用户故事：** 作为运维工程师，我希望 K8s 部署使用公司自建的外部 PostgreSQL 数据库，并通过 ConfigMap/Secret 集中管理所有配置。

#### 验收标准

1. THE K8s_Manifests SHALL 提供 ConfigMap 资源定义（`docgen-config`），包含以下非敏感配置项并附带注释：
   - External_PostgreSQL 的连接主机（`DB_HOST`）、端口（`DB_PORT`）和数据库名称（`DB_NAME`）
   - Redis 服务内部地址（`REDIS_HOST`、`REDIS_PORT`）
   - MinIO 服务内部地址（`MINIO_ENDPOINT`）
   - Docxtemplater_Service 内部地址（`DOCXTEMPLATER_SERVICE_URL`）
   - OnlyOffice 内部地址（`ONLYOFFICE_URL`）
2. THE K8s_Manifests SHALL 提供 Secret 资源定义（`docgen-secrets`），包含以下敏感信息（使用 base64 编码占位符）并附带注释：
   - External_PostgreSQL 的用户名（`DB_USERNAME`）和密码（`DB_PASSWORD`）
   - Redis 密码（`REDIS_PASSWORD`）
   - MinIO 凭证（`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`）
   - JWT 密钥（`JWT_SECRET`）
   - AES-256 加密密钥（`ENCRYPTION_KEY`）
   - OnlyOffice JWT 密钥（`ONLYOFFICE_JWT_SECRET`）
3. WHEN DocGen_Backend 的 Deployment 引用配置时，THE K8s_Manifests SHALL 通过 `envFrom` 同时引用 ConfigMap 和 Secret，使环境变量自动注入到容器中
4. THE K8s_Manifests SHALL 在 ConfigMap 和 Secret 的注释中说明：首次部署前需确保外部 PostgreSQL 数据库已创建（数据库名 `docgen`），Flyway 会在应用启动时自动执行数据库迁移

### 需求 6：健康检查与就绪探针

**用户故事：** 作为运维工程师，我希望所有服务都配置了健康检查和就绪探针，以便 K8s 能够自动检测和恢复故障服务。

#### 验收标准

1. THE K8s_Manifests SHALL 为 DocGen_Backend 配置 liveness 探针，使用 HTTP GET 请求 `/actuator/health` 端点，初始延迟 60 秒，间隔 30 秒，超时 5 秒，失败阈值 3
2. THE K8s_Manifests SHALL 为 DocGen_Backend 配置 readiness 探针，使用 HTTP GET 请求 `/actuator/health` 端点，初始延迟 30 秒，间隔 10 秒，超时 5 秒，失败阈值 3
3. THE K8s_Manifests SHALL 为 DocGen_Frontend 配置 liveness 探针，使用 HTTP GET 请求根路径 `/`，间隔 30 秒
4. THE K8s_Manifests SHALL 为 DocGen_Frontend 配置 readiness 探针，使用 HTTP GET 请求根路径 `/`，初始延迟 5 秒，间隔 10 秒
5. THE K8s_Manifests SHALL 为 Docxtemplater_Service 配置 liveness 探针，使用 HTTP GET 请求 `/health` 端点，初始延迟 30 秒，间隔 30 秒
6. THE K8s_Manifests SHALL 为 Docxtemplater_Service 配置 readiness 探针，使用 HTTP GET 请求 `/health` 端点，初始延迟 15 秒，间隔 10 秒
7. THE K8s_Manifests SHALL 为 Redis 配置 liveness 探针，使用 `redis-cli ping` 命令执行检查，间隔 15 秒
8. THE K8s_Manifests SHALL 为 MinIO 配置 liveness 探针，使用 HTTP GET 请求 `/minio/health/live` 端点，间隔 15 秒

### 需求 7：部署脚本

**用户故事：** 作为开发者或运维工程师，我希望有清晰的部署脚本，以便快速完成本地 Docker 部署和 K8s 集群部署。

#### 验收标准

1. THE 项目根目录 SHALL 提供本地 Docker 部署脚本（`deploy.sh`），自动执行以下步骤：检查 Docker 和 Docker Compose 是否已安装、检查 `.env` 文件是否存在（不存在则从 `.env.example` 复制并提示修改）、执行 `docker compose build` 构建镜像、执行 `docker compose up -d` 启动服务、等待服务健康检查通过后输出访问地址
2. WHEN 部署脚本检测到 Docker 或 Docker Compose 未安装时，THE 部署脚本 SHALL 输出安装指引并退出
3. THE K8s_Manifests 目录 SHALL 提供 K8s 部署脚本（`k8s-deploy.sh`），按正确顺序应用所有 K8s 资源清单：namespace → configmap/secret → pvc → deployments/services → ingress
4. WHEN K8s 部署脚本执行时，THE K8s 部署脚本 SHALL 检查 `kubectl` 是否已安装并能连接到集群
5. THE K8s_Manifests 目录 SHALL 提供 `kustomization.yaml` 文件，支持使用 `kubectl apply -k` 一键部署所有资源
6. IF 部署过程中某个服务启动失败，THEN THE 部署脚本 SHALL 输出该服务的日志信息以便排查问题

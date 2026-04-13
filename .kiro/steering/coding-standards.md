---
inclusion: always
---

# 项目编码标准与开发规范

## 项目概述

本项目是一个低代码文档生成系统（Document Generation System），采用前后端分离的单体应用架构。

## 技术栈

- **后端**: Java 17 + Spring Boot 3.2 + Spring Security 6 + Spring Data JPA + PostgreSQL 16.5 + Redis 7.2
- **前端**: Vue 3 + TypeScript + Element Plus + Vite
- **文档引擎**: Docxtemplater (Node.js 独立服务) + LibreOffice Headless (PDF 转换)
- **在线编辑器**: OnlyOffice Document Editor
- **对象存储**: MinIO (S3 兼容)
- **测试框架**: JUnit 5 + jqwik (Property-Based Testing) + Testcontainers

## 后端开发规范

### 包结构

```
com.docgen/
├── config/       # Spring 配置类 (@Configuration)
├── controller/   # REST 控制器 (@RestController)
├── dto/          # 数据传输对象 (Request/Response/DTO)
├── entity/       # JPA 实体 (@Entity)
├── exception/    # 异常类和全局异常处理
├── filter/       # Servlet 过滤器 (Security, RateLimit, Tenant)
├── health/       # Actuator 健康检查指示器
├── repository/   # Spring Data JPA 仓库 (@Repository)
├── service/      # 业务逻辑服务 (@Service)
└── util/         # 工具类 (TenantContext 等)
```

### Entity 规范

- 使用 JPA 注解 (`@Entity`, `@Table`, `@Column`)
- 所有实体必须包含 `tenant_id` 字段并添加 Hibernate `@Filter(name = "tenantFilter")` 实现多租户隔离
- 使用 `@PrePersist` 和 `@PreUpdate` 管理 `createdAt` / `updatedAt` 时间戳
- 时间类型统一使用 `java.time.Instant`
- 使用 `GenerationType.IDENTITY` 作为主键生成策略
- 不使用 Lombok，手动编写 getter/setter

### Service 规范

- 使用 `@Service` 注解
- 使用构造器注入（不使用 `@Autowired` 字段注入）
- 事务管理使用 `@Transactional`，只读操作使用 `@Transactional(readOnly = true)`
- 使用 SLF4J Logger: `private static final Logger log = LoggerFactory.getLogger(XxxService.class);`
- 异常使用项目自定义异常类 (`BusinessException`, `ResourceNotFoundException`)，配合 `ErrorCode` 枚举
- 敏感信息使用 `EncryptionService` (AES-256-GCM) 加密存储

### Controller 规范

- 使用 `@RestController` + `@RequestMapping("/api/...")`
- 返回 DTO 对象，不直接返回 Entity
- 使用 `@Valid` 进行请求参数校验
- 遵循 RESTful 设计规范

### DTO 规范

- 使用 Java Record 或普通类
- Request DTO 使用 Jakarta Validation 注解 (`@NotBlank`, `@Size`, `@Email` 等)
- Response DTO 命名: `XxxDTO`, Request 命名: `CreateXxxRequest`, `UpdateXxxRequest`

### 数据库迁移

- 使用 Flyway 管理数据库迁移
- 迁移文件路径: `backend/src/main/resources/db/migration/`
- 命名规范: `V{序号}__{描述}.sql` (双下划线分隔)
- 当前最大版本号: V28，新迁移从 V29 开始
- 所有表必须包含 `tenant_id` 列以支持多租户隔离

### MinIO 文件存储

- 模板文件路径格式: `templates/{tenantId}/{uuid}_{filename}`
- 生成文档路径格式: `documents/{tenantId}/{uuid}_{filename}`
- 使用 MinIO Java SDK 操作对象存储
- Bucket 名称通过配置 `minio.bucket-name` 指定，默认 `docgen`

## 前端开发规范

### 目录结构

```
frontend/src/
├── api/          # API 调用层 (Axios 封装)
├── components/   # 通用组件 (.vue)
├── composables/  # 组合式函数 (useXxx.ts)
├── i18n/         # 国际化 (en-US, zh-CN, zh-TW)
├── layouts/      # 布局组件
├── router/       # Vue Router 路由配置
├── stores/       # Pinia 状态管理
├── types/        # TypeScript 类型定义
├── views/        # 页面视图 (按功能模块分目录)
└── __tests__/    # 前端测试
```

### 编码规范

- 使用 Composition API (`<script setup lang="ts">`)
- 使用 Element Plus 组件库
- API 调用统一通过 `src/api/request.ts` 封装的 Axios 实例
- 国际化使用 vue-i18n，所有用户可见文本必须使用 i18n key
- 支持三种语言: en-US (默认), zh-CN, zh-TW

## Docxtemplater Node.js 服务规范

- 服务路径: `docxtemplater-service/`
- REST API 端点: `/render`, `/evaluate`, `/convert-pdf`, `/health`
- 安全沙箱使用 `isolated-vm` 库
- 测试使用 Jest

## 测试规范

### Property-Based Testing (PBT)

- 使用 jqwik 框架
- 测试文件路径: `backend/src/test/java/com/docgen/property/`
- 命名规范: `Xxx PropertyTest.java`
- 使用 `@Property(tries = 100)` 注解
- 使用 `@Provide` 注解提供自定义 Arbitrary

### 集成测试

- 使用 Testcontainers (PostgreSQL)
- 测试文件路径: `backend/src/test/java/com/docgen/integration/`

### 前端测试

- 测试文件路径: `frontend/src/__tests__/`
- 使用 Vitest

## 安全规范

- JWT 认证 (Access Token 2h + Refresh Token 7d)
- API Key 认证 (外部系统调用)
- 多租户数据隔离 (Hibernate Filter + TenantContext ThreadLocal)
- 敏感信息 AES-256-GCM 加密存储
- SQL 注入防护 (PreparedStatement)
- 表达式执行安全沙箱 (isolated-vm)
- 密码 BCrypt 哈希存储

## 部署规范

- Docker 容器化部署
- Kubernetes 编排 (k8s/ 目录)
- docker-compose.yml 用于本地开发
- 环境变量通过 .env 文件管理

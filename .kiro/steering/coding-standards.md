---
inclusion: always
---

# 项目编码标准

## 技术栈

- 后端: Java 17 + Spring Boot 3.2 + Spring Security 6 + Spring Data JPA + PostgreSQL 16.5 + Redis 7.2
- 前端: Vue 3 + TypeScript + Element Plus + Vite
- 文档引擎: Docxtemplater (Node.js) + LibreOffice Headless (PDF)
- 在线编辑器: OnlyOffice Document Editor
- 对象存储: MinIO (S3 兼容)
- 测试: JUnit 5 + jqwik (PBT) + Testcontainers + Vitest

## 后端核心约定

- 包结构: `com.docgen/{config,controller,dto,entity,exception,filter,health,repository,service,util}`
- Entity: JPA 注解 + `tenant_id` + `@Filter(name = "tenantFilter")` + `@PrePersist/@PreUpdate` + `java.time.Instant` + `GenerationType.IDENTITY` + 无 Lombok
- Service: 构造器注入 + `@Transactional` (只读加 `readOnly = true`) + SLF4J Logger + `BusinessException`/`ResourceNotFoundException` + `ErrorCode` 枚举
- Controller: `@RestController` + 返回 DTO + `@Valid` 校验
- DTO: Record 或普通类 + Jakarta Validation + 命名 `XxxDTO`/`CreateXxxRequest`/`UpdateXxxRequest`
- Flyway: `backend/src/main/resources/db/migration/V{序号}__{描述}.sql`，当前最大 V28
- MinIO: `templates/{tenantId}/{uuid}_{filename}` / `documents/{tenantId}/{uuid}_{filename}`

## 前端核心约定

- 目录: `frontend/src/{api,components,composables,i18n,layouts,router,stores,types,views,__tests__}`
- Composition API (`<script setup lang="ts">`) + Element Plus + vue-i18n (en-US/zh-CN/zh-TW)
- API 统一通过 `src/api/request.ts` 封装的 Axios 实例

## 安全约定

- JWT (Access 2h + Refresh 7d) + API Key + 多租户隔离 (Hibernate Filter + TenantContext)
- 敏感信息 AES-256-GCM 加密 + BCrypt 密码哈希 + isolated-vm 沙箱

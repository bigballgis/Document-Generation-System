---
inclusion: always
---

# 项目技术栈与核心约定

## 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Java 17 + Spring Boot 3.2 + Spring Security 6 + Spring Data JPA + PostgreSQL 16.5 + Redis 7.2 |
| 前端 | Vue 3 + TypeScript + Element Plus + Vite |
| 文档引擎 | Docxtemplater (Node.js) + LibreOffice Headless (PDF) |
| 在线编辑 | OnlyOffice Document Editor |
| 对象存储 | MinIO (S3 兼容) |
| 测试 | JUnit 5 + jqwik (PBT) + Testcontainers + Vitest + fast-check |

## 后端约定 (仅项目特有)

- 包结构: `com.docgen/{config,controller,dto,entity,exception,filter,health,repository,service,util}`
- Entity: 无 Lombok + `tenant_id` + `@Filter(name = "tenantFilter")` + `@PrePersist/@PreUpdate` + `Instant` + `IDENTITY`
- DTO: Record 或普通类 + `XxxDTO` / `CreateXxxRequest` / `UpdateXxxRequest`
- Flyway: `V{序号}__{描述}.sql`，当前最大 V38
- MinIO 路径: `templates/{tenantId}/{uuid}_{filename}` / `documents/{tenantId}/{uuid}_{filename}`
- 参考: #[[file:backend/src/main/java/com/docgen/service/ParameterService.java]]

## 前端约定 (仅项目特有)

- 目录: `frontend/src/{api,components,composables,i18n,layouts,router,stores,types,views,__tests__}`
- API 统一通过 `src/api/request.ts` 封装的 Axios 实例
- i18n 三语言: en-US / zh-CN / zh-TW
- Stores: `useUserStore` (认证) + `useTemplateWorkspaceStore` (模板工作区)

## 安全约定

- JWT (Access 2h + Refresh 7d) + API Key + 多租户隔离 (Hibernate Filter + TenantContext)
- 敏感信息 AES-256-GCM + BCrypt + isolated-vm 沙箱

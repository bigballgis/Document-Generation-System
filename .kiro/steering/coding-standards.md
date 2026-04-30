---
inclusion: always
---

# Tech stack and core conventions

## Stack

| Layer | Technology |
|-------|------------|
| Backend | Java 17 + Spring Boot 3.2 + Spring Security 6 + Spring Data JPA + PostgreSQL 16.5 + Redis 7.2 |
| Frontend | Vue 3 + TypeScript + Element Plus + Vite |
| Document engine | Docxtemplater (Node.js) + LibreOffice headless (PDF) |
| Online editing | OnlyOffice Document Editor |
| Object storage | MinIO (S3-compatible) |
| Testing | JUnit 5 + jqwik (PBT) + Testcontainers + Vitest + fast-check |

## Backend (project-specific)

- Packages: `com.docgen/{config,controller,dto,entity,exception,filter,health,repository,service,util}`
- Entity: no Lombok + `tenant_id` + `@Filter(name = "tenantFilter")` + `@PrePersist/@PreUpdate` + `Instant` + `IDENTITY`
- DTO: records or POJOs named `XxxDTO` / `CreateXxxRequest` / `UpdateXxxRequest`
- Flyway: `V{version}__{description}.sql`; new scripts use the next integer after the highest `V*__*.sql` in #[[file:backend/src/main/resources/db/migration/]]
- MinIO paths: `templates/{tenantId}/{uuid}_{filename}` / `documents/{tenantId}/{uuid}_{filename}`
- Reference: #[[file:backend/src/main/java/com/docgen/service/ParameterService.java]]

## Frontend (project-specific)

- Tree: `frontend/src/{api,components,composables,i18n,layouts,router,stores,types,views,__tests__}`
- All HTTP via Axios instance in `src/api/request.ts`
- i18n: en-US / zh-CN / zh-TW
- Stores: `useUserStore` (auth) + `useTemplateWorkspaceStore` (template workspace)

## Security

- JWT (access ~2h + refresh ~7d in Redis) + API keys + multi-tenant isolation (Hibernate filter + `TenantContext`)
- Secrets: AES-256-GCM + BCrypt; expressions in isolated-vm sandbox

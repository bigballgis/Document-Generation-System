---
inclusion: auto
name: security-standards
description: Security — auth, multi-tenant isolation, data protection, audit
---

# Security

## Authentication

- JWT: access ~2h + refresh ~7d (Redis); claims include userId/tenantId/role
- API keys: AES-256-GCM at rest; enable/disable supported
- RBAC: VIEW/EDIT/DELETE/CALL_API; `PermissionService` guards resource access
- Reference: #[[file:backend/src/main/java/com/docgen/config/SecurityConfig.java]]

## Multi-tenant isolation

- All queries filtered by `tenant_id`
- Hibernate `@Filter(name = "tenantFilter")` applies tenant scope
- Clear `TenantContext` (ThreadLocal) after each request
- Cross-tenant access is forbidden
- Reference: #[[file:backend/src/main/java/com/docgen/config/HibernateFilterConfig.java]]

## Data protection

- Sensitive fields: AES-256-GCM; passwords: BCrypt
- Masking: show first/last 4, mask middle
- SQL via `PreparedStatement`; expressions in isolated-vm sandbox

## Audit

- Critical actions emit audit logs (who/when/type/payload/IP)
- Audit records must not be rewritten or deleted by business flows

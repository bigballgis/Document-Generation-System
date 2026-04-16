---
inclusion: auto
name: security-standards
description: 安全开发规范，包括认证授权、多租户隔离、数据安全和审计要求
---

# 安全开发规范

## 认证

- JWT: Access Token 2h + Refresh Token 7d (Redis)，Token 含 userId/tenantId/role
- API Key: AES-256-GCM 加密存储，支持启用/禁用
- RBAC: 权限类型 VIEW/EDIT/DELETE/CALL_API，所有资源访问经 PermissionService 验证
- 参考: #[[file:backend/src/main/java/com/docgen/config/SecurityConfig.java]]

## 多租户隔离

- 所有查询必须含 `tenant_id` 过滤
- Hibernate `@Filter(name = "tenantFilter")` 自动注入
- TenantContext (ThreadLocal) 请求结束时必须清理
- 跨租户访问严格禁止
- 参考: #[[file:backend/src/main/java/com/docgen/config/HibernateFilterConfig.java]]

## 数据安全

- 敏感信息 AES-256-GCM 加密，密码 BCrypt 哈希
- 脱敏: 仅显示前4后4位，中间星号
- SQL 用 PreparedStatement，表达式在 isolated-vm 沙箱执行

## 审计

- 关键操作必须记录审计日志 (操作者/时间/类型/内容/IP)
- 审计日志不可被业务操作修改或删除

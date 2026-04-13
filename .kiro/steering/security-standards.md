---
description: 安全开发规范，包括认证授权、多租户隔离、数据安全和审计要求
inclusion: auto
fileMatchPattern: '**/security/**,**/filter/**,**/config/Security*.java,**/auth/**'
---

# 安全开发规范

## 认证与授权

### JWT 认证
- Access Token 有效期: 2 小时
- Refresh Token 有效期: 7 天
- Token 中包含: userId, tenantId, role
- Refresh Token 存储在 Redis 中

### API Key 认证
- 用于外部系统 API 调用
- API Key 使用 AES-256-GCM 加密存储
- 支持启用/禁用状态

### 权限控制
- 基于角色的访问控制 (RBAC)
- 权限类型: VIEW, EDIT, DELETE, CALL_API
- 所有资源访问必须经过 PermissionService 验证

## 多租户隔离

- 所有数据库查询必须包含 tenant_id 过滤
- 使用 Hibernate @Filter 自动注入租户条件
- TenantContext (ThreadLocal) 在请求结束时必须清理
- 跨租户数据访问严格禁止

## 数据安全

- 敏感信息（密码、API Key、Token）使用 AES-256-GCM 加密存储
- 密码使用 BCrypt 哈希（不可逆）
- 脱敏显示：仅显示前 4 位和后 4 位，中间星号替代
- SQL 参数使用 PreparedStatement 防注入
- 表达式执行在 isolated-vm 安全沙箱中

## 审计

- 所有关键操作必须记录审计日志
- 审计日志不可被业务操作修改或删除
- 包含: 操作者、操作时间、操作类型、操作内容、IP 地址

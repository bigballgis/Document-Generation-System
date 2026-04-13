---
description: Spec 文档质量标准，包括 EARS 模式、INCOSE 规则、正确性属性和任务文档规范
inclusion: auto
fileMatchPattern: '.kiro/specs/**/*.md'
---

# Spec 文档质量标准

## 需求文档 (requirements.md) 质量规则

### EARS 模式 (Easy Approach to Requirements Syntax)

所有验收标准必须使用以下 EARS 模式之一：

- **Ubiquitous**: `THE <system> SHALL <action>` — 系统始终执行的行为
- **Event-driven**: `WHEN <trigger>, THE <system> SHALL <action>` — 事件触发的行为
- **State-driven**: `WHERE <state>, THE <system> SHALL <action>` — 特定状态下的行为
- **Unwanted**: `IF <condition>, THEN THE <system> SHALL <action>` — 异常/边界情况处理
- **Optional**: `WHERE <feature is included>, THE <system> SHALL <action>` — 可选功能

### INCOSE 质量规则

每条验收标准必须满足：
- **原子性**: 一条标准只描述一个可验证的行为
- **可测试性**: 必须能通过自动化测试或手动测试验证
- **无歧义**: 避免"等"、"可能"、"应该考虑"等模糊用语
- **完整性**: 包含正常流程和异常/边界情况
- **一致性**: 术语使用与术语表一致，不引入未定义术语

### 需求完整性检查清单

每个需求必须覆盖：
- [ ] 正常流程 (Happy Path)
- [ ] 异常/错误处理
- [ ] 边界条件
- [ ] 与现有功能的交互/兼容性
- [ ] 多租户隔离影响
- [ ] 权限控制影响
- [ ] 性能约束（如适用）

## 设计文档 (design.md) 质量规则

### 必须包含的章节

1. **Overview**: 功能概述和核心目标
2. **Architecture**: 架构图和架构决策
3. **Components and Interfaces**: 组件定义、接口签名、交互流程
4. **Data Model**: 数据库表设计、实体关系
5. **API Design**: REST API 端点设计
6. **Correctness Properties**: 正确性属性定义（用于 PBT）

### 正确性属性 (Correctness Properties)

每个核心功能必须定义至少一个可执行的正确性属性，格式：

```
Property: <属性名称>
Description: <属性描述>
Formal: ∀ <变量> ∈ <域>, <谓词>
Test Strategy: <PBT 测试策略>
```

## 任务文档 (tasks.md) 质量规则

### 任务粒度

- 每个任务应在 2-4 小时内可完成
- 任务之间的依赖关系必须明确
- 每个任务必须包含可验证的完成标准

### 任务结构

```markdown
- [ ] N. 任务标题
  - [ ] N.1 子任务 1
  - [ ] N.2 子任务 2
```

## 与现有系统的集成规范

新功能的 spec 必须考虑：
- 与现有 Template 实体的关系和兼容性
- 多租户隔离 (tenant_id + Hibernate Filter)
- 权限控制 (PermissionService)
- 审计日志 (AuditLogService)
- 模板状态机 (TemplateStateMachineService)
- Flyway 数据库迁移（从 V29 开始）
- MinIO 文件存储路径规范

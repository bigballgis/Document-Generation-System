---
inclusion: always
---

# Spec 工作流自动化

在 spec 模式下开发功能时，按以下流程自动执行。审查标准:
- #[[file:.kiro/steering/spec-review-checklist.md]]
- #[[file:.kiro/steering/spec-quality.md]]

## 流程

| 阶段 | 内容 | 审查 | 暂停 |
|------|------|------|------|
| 1 | 生成 requirements.md | 五轮 (完整性→准确性→依赖→边界→矛盾) | ✅ 用户确认 |
| 2 | 生成 design.md | 五轮 (需求覆盖→可行性→代码库一致→副作用→迁移) | 自动 |
| 3 | 生成 tasks.md | 五轮 (完整性→顺序→粒度→可验证→风险) | 自动 |
| 4 | 三文档联合审查 | 五轮 (追溯→一致→代码库→模拟→遗漏) | 自动 |
| 5 | 执行所有任务 | 每任务完成后验证编译和测试 | — |
| 6 | 构建与部署 | Docker build → compose up → healthcheck | — |

## 审查要求

- 每轮必须实际读取代码文件验证 (readCode/grepSearch/fileSearch)
- 发现问题立即修正，不推迟
- 未发现问题时声明"第 N 轮未发现问题"

## 强制执行规则 (MANDATORY)

- **五轮审查必须在聊天中逐轮展示**，不得委托给子代理 (subagent)。用户必须能在聊天中看到每一轮的审查过程、发现的问题和修正结果。
- **禁止跳过审查**：即使子代理已生成文档，主代理仍必须在聊天中执行完整的五轮审查后才能告知用户文档已完成。
- **禁止合并审查轮次**：每轮只关注一个维度，不得在同一轮中混合多个维度。
- **审查结果必须包含总结表格**：五轮完成后，输出一个表格汇总每轮的维度、发现问题数和修复内容。

## 构建命令

```bash
docker build -f Dockerfile.local -t docgen-app:latest .        # cwd: backend/
docker build -f Dockerfile.local -t docgen-frontend:latest .    # cwd: frontend/
docker-compose up -d
curl http://localhost:8080/actuator/health                      # 验证 UP
```

## 遗漏检查清单

SecurityConfig URL、OpenApiConfig tag、application.yml、Dockerfile/docker-compose、前端导航菜单、前端路由权限、AuditLog 操作类型

## 子 Spec 拆分

超过 8 个需求或 30 个子任务时，按功能边界拆分到 `.kiro/specs/{parent}/{sub}/`，串行执行。

---
inclusion: always
---

# Spec 工作流自动化

在 spec 模式下开发功能时，按以下流程自动执行。引用审查标准:
- #[[file:.kiro/steering/spec-review-checklist.md]]
- #[[file:.kiro/steering/spec-quality.md]]

## 流程

### 阶段 1: 需求 → 五轮审查 → 用户确认 (唯一暂停点)

生成 requirements.md，执行五轮审查 (完整性→准确性→依赖关系→边界条件→矛盾冗余)，每轮遵循"修复-再审查循环"，修正后提交用户确认。

### 阶段 2: 设计 → 五轮审查 → 自动继续

生成 design.md，五轮审查 (需求覆盖→技术可行性→代码库一致性→副作用→迁移安全性)。

### 阶段 3: 任务 → 五轮审查 → 自动继续

生成 tasks.md，五轮审查 (完整性→顺序依赖→粒度→可验证性→风险)。

### 阶段 4: 三文档联合审查 (五轮)

可追溯性→一致性→代码库验证→执行顺序模拟→遗漏兜底搜索。

### 阶段 5: 执行

所有任务标记为必须，按顺序执行，每个任务完成后验证编译和测试。

### 阶段 6: 构建部署

构建 Docker 容器，验证启动正常。

## 审查要求

- 每轮必须实际读取代码文件验证 (fileSearch/listDirectory/readCode/grepSearch)
- 发现问题立即修正，不推迟
- 未发现问题时声明"第 N 轮未发现问题"

## 遗漏检查清单

SecurityConfig URL、OpenApiConfig tag、application.yml 配置、Dockerfile/docker-compose、README、前端导航菜单、前端路由权限、AuditLog 操作类型、Metrics 指标、CI/CD 配置

## 子 Spec 拆分

超过 8 个需求或 30 个子任务时，按功能边界拆分到 `.kiro/specs/{parent}/{sub}/`，每个子 spec 完整流程，串行执行，父级创建 execution-order.md。

---
inclusion: manual
---

# Spec 文档质量标准

## 前后端一致性检查规则

### 任务粒度要求

1. 前端任务不得将多个独立页面合并为一个子任务。每个前端页面（含 API 层、视图、组件、i18n）应为独立子任务。
2. 每个前端子任务必须明确列出：
   - 要创建的 API 调用文件（如 `frontend/src/api/xxx.ts`）
   - 要创建的视图文件（如 `frontend/src/views/xxx/Index.vue`）
   - 要创建的组件文件
   - 要扩展的路由配置
   - 要添加的 i18n key

### 检查点验收标准

检查点任务必须包含以下验证步骤：

1. **API 覆盖率检查**: 遍历 `backend/src/main/java/com/docgen/controller/` 下所有 Controller，确认每个 `@RequestMapping` 端点在 `frontend/src/api/` 中有对应的调用函数
2. **路由完整性检查**: 确认每个前端页面在 `frontend/src/router/index.ts` 中注册了路由
3. **i18n 完整性检查**: 确认所有用户可见文本使用了 i18n key，且 en-US、zh-CN、zh-TW 三个语言文件中都有对应翻译

### 后端 Controller 与前端 API 映射规则

每个后端 Controller 必须有对应的前端 API 文件：

| 后端 Controller | 前端 API 文件 |
|---|---|
| `XxxController.java` | `frontend/src/api/xxx.ts` |

例外情况（仅后端使用的端点，如 OnlyOffice callback）需在任务文档中明确标注。

### 前端任务拆分模式

推荐的前端任务拆分方式（以"表达式管理"为例）：

```
- [ ] N. 前端 — 表达式管理
  - [ ] N.1 创建前端 API 调用层
    - 创建 `frontend/src/api/expressions.ts`
    - 包含: createExpression, listExpressions, updateExpression, deleteExpression, validateExpression
    - 对应后端: ExpressionController 的所有端点
  - [ ] N.2 创建表达式管理组件
    - 创建 ExpressionFormDialog.vue（创建/编辑表达式对话框）
    - 创建 ExpressionList.vue（表达式列表组件）
    - 创建 ExpressionValidator.vue（表达式验证/测试组件）
  - [ ] N.3 集成到模板详情页
    - 在模板详情页中添加"表达式"标签页
    - 集成 ExpressionList 和 ExpressionFormDialog
  - [ ] N.4 扩展 i18n 翻译
    - 在 en-US.json、zh-CN.json、zh-TW.json 中添加 expression.* 前缀的翻译 key
```

## EARS 需求模式

需求文档中的验收标准应使用 EARS（Easy Approach to Requirements Syntax）模式：

- **Ubiquitous**: THE [system] SHALL [action]
- **Event-driven**: WHEN [trigger], THE [system] SHALL [action]
- **State-driven**: WHILE [state], THE [system] SHALL [action]
- **Unwanted behavior**: IF [condition], THEN THE [system] SHALL [action]
- **Optional**: WHERE [feature], THE [system] SHALL [action]

## 正确性属性（Correctness Properties）

每个 spec 应定义可执行的正确性属性，使用 Property-Based Testing 验证：

- 后端属性测试使用 jqwik 框架
- 前端属性测试使用 fast-check 框架
- 每个属性测试必须引用对应的需求编号

---
inclusion: auto
name: writing-steering
description: Steering 文件编写规范。在创建或修改 .kiro/steering/ 下的 steering 文件时使用。
---

# Steering 编写规范

## Front Matter

必须是文件第一行，无前置空行。

| 模式 | 场景 | 必填字段 |
|------|------|----------|
| `auto` | 领域特定（首选） | `name` + `description` |
| `fileMatch` | 文件类型触发 | `fileMatchPattern` |
| `always` | 仅全局标准（≤3 个） | 无 |
| `manual` | 偶尔使用 | 无 |

优先 `auto`，减少上下文噪音。`description` 用中文一句话说明触发时机。

## 防幻觉

1. 写入前验证 — 用 readCode/grepSearch 确认代码库中存在该模式
2. 引用锚定 — 用 `#[[file:path]]` 引用真实文件，不凭记忆描述
3. 可审计 — 每条声明能在代码库中找到证据，找不到就删
4. 允许不知道 — 不确定的标注 `<!-- TODO: 待验证 -->`，不编造
5. 冲突时提问 — 代码与规则矛盾时明确指出，不静默忽略

## Token 效率

每行问："删掉它会导致 AI 犯错吗？" 不会就删。

- 删 AI 已知的 — 标准惯例不写，只写项目特有约定
- 表格 > 段落，要点 > 完整句
- `#[[file:]]` 引用 > 内联代码块
- 一个示例 > 十条规则

| 指标 | 上限 |
|------|------|
| 单文件 | ≤ 100 行 |
| `always` 文件数 | ≤ 3 |
| `always` 合计 | ≤ 300 行 |

## 结构模板

```markdown
---
inclusion: auto
name: {kebab-case}
description: {一句话，中文}
---
# {领域}
## 规则
- 要点式，可执行可验证
## 示例（可选）
- 用 #[[file:]] 引用真实代码
```

单一职责，一个文件一个领域。命名 kebab-case。

## 引用

- 文件引用: `#[[file:relative/path]]`
- fileMatch: `fileMatchPattern: ["**/*.ts"]` (数组) 或 `"**/*.test.*"` (单一)

## 检查清单

1. front matter 正确，inclusion 合理
2. 单一职责，不重叠
3. 所有事实已验证，无编造
4. 文件引用路径存在
5. ≤ 100 行

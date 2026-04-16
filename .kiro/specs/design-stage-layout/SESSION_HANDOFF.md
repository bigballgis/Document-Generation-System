# Session Handoff: design-stage-layout spec

## 当前进度

| 阶段 | 状态 | 说明 |
|------|------|------|
| Requirements | ✅ 完成 | 10 个需求 + 7 个正确性属性，五轮审查已完成 |
| Design | ⏳ 待开始 | 下一步：生成 design.md + 五轮审查 |
| Tasks | ⏳ 待开始 | design.md 完成后生成 |
| Execution | ⏳ 待开始 | tasks.md 完成后执行 |

## Spec 信息

- 功能名称: `design-stage-layout`
- Spec 路径: `.kiro/specs/design-stage-layout/`
- Config: `.kiro/specs/design-stage-layout/.config.kiro`
- 工作流: Requirements-First
- 语言: 中文

## 需求文档核心内容（10 个需求）

1. **三步子流程框架** — 参数表设计 → 片段编排 → 片段详细设计，有完成条件判定
2. **参数表设计** — 表/子表/关联表（ARRAY=子表一对多，OBJECT=关联表一对一），面包屑导航，递归嵌套最大5层，内联编辑，删除（级联警告），拖拽排序
3. **参数总览** — 抽屉面板，树形/JSON Schema 视图，三步都可用
4. **片段编排拖拽画布** — 左侧组件面板 + 右侧画布，内容片段（7种类型）+ 控制节点（分页符/页眉/页脚/页码规则），视觉区分（实心卡片 vs 细条），自动创建空白 .docx（不需要上传），控制节点可删除，影响范围标记，数据模型扩展（headerFilePath/footerFilePath/pageNumberFormat/pageNumberStart）
5. **片段详细设计** — 编辑器(70%) + 参数侧边栏(30%)，**点击插入**（不是拖拽），通过 OnlyOffice executeCommand API，快速新增参数
6. **内容隔离校验** — 前端软预防（提示条 + 预配置模板）+ 后端硬校验（解压 .docx 检查 XML），OnlyOffice 开源版不支持隐藏菜单项
7. **工具栏整合** — 导入 ZIP + 齿轮设置 + 参数总览按钮 + 片段选择器（仅第三步）
8. **拖拽交互一致性** — sortablejs，统一视觉反馈
9. **参数侧边栏搜索过滤** — 模糊搜索 + 类型过滤
10. **i18n 三语言** — 子步骤名称、片段类型、控制节点名称、侧边栏标题、面包屑标签

## 关键设计决策（用户确认）

- 片段上传是进阶功能，先不做 → 系统自动创建空白 .docx
- 参数插入用**点击**而非拖拽（跨 iframe 拖拽技术复杂）
- 控制节点数据**扩展现有 AssemblySegmentEntry**（不新建模型）
- 页眉页脚作为迷你 .docx 片段，用 OnlyOffice 编辑
- 前端预防改为**软预防**（UI 提示条 + 预配置模板），后端校验为硬保障
- 参数名重名检查**已在后端实现**（ParameterService.checkDuplicateName）

## 后端变更

- 新增"创建空白片段"API（自动生成空白 .docx 上传到 MinIO）
- 新增内容隔离校验 API（OnlyOffice 回调时检查 .docx 内容）
- 扩展 OnlyOfficeService 支持片段级别回调和内容校验
- 扩展 AssemblySegmentEntry 后端模型（headerFilePath 等字段）

## Steering 规则提醒

- 五轮审查必须在聊天中逐轮展示，不得委托给子代理
- 每轮必须实际读取代码文件验证
- 审查结果必须包含总结表格
- Design 阶段审查维度：需求覆盖→可行性→代码库一致→副作用→迁移

## 新 Session 启动指令

在新 session 中发送以下消息即可继续：

```
继续 design-stage-layout spec 的 design 阶段，请先读取 .kiro/specs/design-stage-layout/SESSION_HANDOFF.md 了解上下文和进度
```

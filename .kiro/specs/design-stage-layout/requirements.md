# 需求文档：设计阶段版面与交互重设计

## 简介

重新设计模板工作区"设计阶段"（DesignStage）的版面和交互，将当前的"编辑器 + 参数抽屉 + 片段弹出面板"单一视图，重构为三步子流程：**参数表设计 → 片段编排（拖拽画布）→ 片段详细设计（编辑器 + 参数侧边栏）**。

核心设计理念：
- **苹果式设计**：封闭但强大、渐进式披露、拖拽驱动
- **统一交互语言**：参数表和片段编排以拖拽为核心，片段编辑以点击插入为核心
- **层级驱动导航**：参数表用表/子表/关联表概念组织，每个表是独立编辑页面，通过面包屑导航
- **单页不信息爆炸**：每一步只展示当前层级的内容
- **内容隔离**：前端预防 + 后端校验双重保障，确保内容片段、页眉、页脚各自的编辑区域不越界

### 与现有 spec 的关系

- 替换 `template-workflow-stages` spec 中 DesignStage 的实现（`frontend/src/views/template-workspace/components/DesignStage.vue`）
- 参数表的后端数据模型不变（`ParameterDefinition` entity、`ParameterController` API），只是前端展示方式从树形改为表/子表/关联表
- 片段编排的后端数据模型不变（`AssemblyConfig`、`AssemblySegmentEntry`），只是前端交互从列表改为拖拽画布
- 复用现有 `OnlyOfficeEditor` 组件、`useAssemblyConfig` composable、`useSegmentDrag` composable
- 复用现有参数 API（`getParameters`、`createParameter`、`updateParameter`、`deleteParameter`、`scanPlaceholders`）

### 变更范围

| 层 | 变更 | 影响 |
|----|------|------|
| 前端 | 重写 `DesignStage.vue`，新增三步子视图组件 | 大 |
| 前端 | 新增 `ParameterTableDesign` 组件（表/子表/关联表视图） | 大 |
| 前端 | 新增 `SegmentCanvas` 组件（拖拽画布 + 控制节点） | 大 |
| 前端 | 重写 `ParameterDrawer` 为 `ParameterSidebar`（点击插入标签） | 中 |
| 前端 | 新增 `ParameterOverviewPanel` 组件（参数总览抽屉） | 小 |
| 前端 | 新增 `ControlNodeEditor` 组件（页眉/页脚迷你 OnlyOffice 编辑器） | 中 |
| 后端 | 新增"创建空白片段"API（自动生成空白 .docx 文件并上传到 MinIO） | 小 |
| 后端 | 新增内容隔离校验 API（OnlyOffice 回调时检查 .docx 内容） | 中 |
| 后端 | 扩展 `OnlyOfficeService` 支持片段级别回调和内容校验 | 中 |
| i18n | 三语言新增设计子步骤名称、控制节点名称和提示文本 | 小 |

## 术语表

- **Design_Stage**：设计阶段主组件（`DesignStage.vue`），包含三步子流程的容器
- **Design_Step_Indicator**：设计阶段内部的三步指示器，显示"参数表设计 → 片段编排 → 片段详细设计"
- **Parameter_Table_Design**：第一步"参数表设计"视图组件，以表/子表/关联表概念展示参数层级
- **Main_Table_View**：参数表设计中的主表视图，显示根级参数（STRING/NUMBER/DATE/BOOLEAN 类型）和子表/关联表的占位链接
- **Sub_Table_View**：参数表设计中的子表视图，显示 ARRAY 类型参数的子参数（一对多关系，如"订单明细"）
- **Related_Table_View**：参数表设计中的关联表视图，显示 OBJECT 类型参数的子参数（一对一关系，如"甲方信息"）
- **Table_Breadcrumb**：参数表设计中的面包屑导航，显示当前表的层级路径（如"主表 > 甲方信息 > 地址"），点击可回到上一层
- **Parameter_Overview_Panel**：参数总览面板，通过按钮打开抽屉，以树形或 JSON Schema 形式展示所有参数的完整结构
- **Segment_Canvas**：第二步"片段编排"视图组件，左侧组件面板 + 右侧拖拽画布
- **Component_Panel**：片段编排中的左侧组件面板，包含内容片段类型和控制节点组件
- **Canvas_Area**：片段编排中的右侧画布区域，展示文档结构预览，支持拖入和排序
- **Content_Segment_Card**：画布中的内容片段卡片，白色背景 + 实线边框 + 左侧彩色类型条，高度较大
- **Control_Node_Card**：画布中的控制节点卡片，浅灰背景 + 虚线边框 + 小图标，高度约内容片段的 1/3
- **Page_Break_Node**：分页符控制节点，修改后方片段的 `pageBreakBefore` 属性，强制分页
- **Header_Node**：页眉控制节点，设置后续片段的页眉内容，作为迷你 .docx 片段用 OnlyOffice 编辑，上边框加粗样式
- **Footer_Node**：页脚控制节点，设置后续片段的页脚内容，作为迷你 .docx 片段用 OnlyOffice 编辑，下边框加粗样式
- **Page_Number_Node**：页码规则控制节点，设置后续片段的页码格式（1,2,3 / i,ii,iii / A-1,A-2）和起始值（可重新从 1 开始）
- **Control_Node_Editor**：控制节点的迷你 OnlyOffice 编辑器，用于编辑页眉/页脚的 .docx 内容
- **Content_Isolation_Validator**：内容隔离校验器，后端服务，在 OnlyOffice 回调保存时检查 .docx 内容是否越界
- **Segment_Detail_Design**：第三步"片段详细设计"视图组件，OnlyOffice 编辑器 + 参数侧边栏
- **Parameter_Sidebar**：片段详细设计中的参数侧边栏，包含参数列表、循环块、条件块三个区域，支持点击标签插入占位符到编辑器
- **Parameter_List_Section**：参数侧边栏中的"参数列表"区域，展示所有参数，点击标签在编辑器光标位置插入 `{参数名}`
- **Loop_Block_Section**：参数侧边栏中的"循环块"区域，展示 ARRAY 类型参数，点击标签在编辑器光标位置插入 `{#数组名}...{/数组名}`
- **Condition_Block_Section**：参数侧边栏中的"条件块"区域，展示条件表达式模板，点击标签在编辑器光标位置插入 `{#if 条件}...{/if}`
- **Inline_Parameter_Creator**：参数侧边栏中的快速新增参数入口，允许用户在编辑片段时直接新增参数而不需要回到第一步
- **AssemblyConfig**：片段编排配置数据模型（`frontend/src/types/segment.ts`），包含 segments 数组
- **AssemblySegmentEntry**：单个片段条目（`frontend/src/types/segment.ts`），包含 filePath、name、segmentType、position、enabled、pageBreakBefore、conditionExpression、dataScope
- **ParameterDTO**：参数数据传输对象（`frontend/src/types/parameter.ts`），包含 id、name、dataType、children 等字段
- **OnlyOffice_Editor**：在线文档编辑器组件（`frontend/src/components/OnlyOfficeEditor.vue`），通过 DocsAPI 实现 iframe 内联编辑

## 需求

### 需求 1：设计阶段三步子流程框架

**用户故事：** 作为模板设计人员，我希望设计阶段分为三个清晰的子步骤，以便我能按照"定义参数 → 编排片段 → 编辑内容"的逻辑顺序完成模板设计。

#### 验收标准

1. THE Design_Stage SHALL 在顶部显示 Design_Step_Indicator，包含三个子步骤：参数表设计、片段编排、片段详细设计，按此固定顺序从左到右排列。
2. THE Design_Step_Indicator SHALL 为每个子步骤显示以下状态之一：未开始（灰色）、进行中（蓝色高亮）、已完成（绿色勾选）。
3. THE Design_Step_Indicator SHALL 根据以下条件判断子步骤完成状态：参数表设计完成 = 至少定义了 1 个参数；片段编排完成 = 至少添加了 1 个内容片段；片段详细设计完成 = 所有已启用的片段均已编辑（filePath 非空）。
4. WHEN 用户点击 Design_Step_Indicator 中的某个子步骤时，THE Design_Stage SHALL 切换到该子步骤的视图（三个子步骤之间可自由切换，无强制顺序限制）。
5. THE Design_Stage SHALL 在子步骤视图区域下方显示"上一步"和"下一步"按钮，方便用户按顺序推进。
6. WHILE 模板状态不为 DRAFT（只读模式），THE Design_Stage SHALL 隐藏所有编辑操作按钮，三个子步骤均以只读模式展示。
7. THE Design_Stage SHALL 替换现有 DesignStage.vue 的实现，移除当前的"编辑器 + ParameterDrawer + SegmentPopover + SettingsPopover"布局。

### 需求 2：参数表设计 — 表/子表/关联表视图

**用户故事：** 作为模板设计人员，我希望参数表以"主表 + 子表 + 关联表"的概念展示，以便我能像设计数据库表一样直观地理解参数的层级关系。

#### 验收标准

1. THE Parameter_Table_Design SHALL 将根级参数（parentId 为 null）显示为 Main_Table_View，其中 STRING、NUMBER、DATE、BOOLEAN 类型参数显示为主表字段行，ARRAY 类型参数显示为"子表"占位链接行（带一对多图标），OBJECT 类型参数显示为"关联表"占位链接行（带一对一图标）。
2. WHEN 用户点击某个 ARRAY 类型参数的占位链接时，THE Parameter_Table_Design SHALL 导航到该参数的 Sub_Table_View，显示该 ARRAY 参数的所有子参数作为子表字段。
3. WHEN 用户点击某个 OBJECT 类型参数的占位链接时，THE Parameter_Table_Design SHALL 导航到该参数的 Related_Table_View，显示该 OBJECT 参数的所有子参数作为关联表字段。
4. THE Parameter_Table_Design SHALL 在视图顶部显示 Table_Breadcrumb，展示当前表的层级路径（如"主表 > items（子表）> address（关联表）"），每个层级可点击回到对应视图。
5. THE Parameter_Table_Design SHALL 支持递归嵌套：子表和关联表内部的 ARRAY/OBJECT 类型参数同样显示为占位链接，可继续钻入下一层级，最大支持 5 层嵌套。
6. THE Parameter_Table_Design SHALL 在每个表视图中提供"添加字段"按钮，点击后在当前表中新增一个参数（parentId 自动设置为当前表的参数 ID，根级表则为 null）。
7. THE Parameter_Table_Design SHALL 对每个字段行提供内联编辑功能：点击名称可编辑参数名，点击类型可通过下拉选择 DataType，点击必填可切换 required 开关，点击默认值可编辑 defaultValue。
8. THE Parameter_Table_Design SHALL 对每个字段行提供删除按钮，点击后显示确认对话框；IF 被删除的参数为 ARRAY 或 OBJECT 类型且有子参数，THEN 确认对话框 SHALL 警告所有子参数也将被级联删除。
9. THE Parameter_Table_Design SHALL 支持拖拽字段行调整排序，拖拽放置后自动更新所有同级参数的 sortOrder 值。
10. WHILE 模板状态不为 DRAFT，THE Parameter_Table_Design SHALL 以只读模式显示所有字段，隐藏"添加字段"按钮和编辑控件。

### 需求 3：参数总览

**用户故事：** 作为模板设计人员，我希望能快速查看所有参数的完整结构，以便我在编排片段或编辑内容时了解可用的参数。

#### 验收标准

1. THE Design_Stage SHALL 在统一工具栏中提供"参数总览"按钮，该按钮在三个子步骤中均可见。
2. WHEN 用户点击"参数总览"按钮时，THE Parameter_Overview_Panel SHALL 以抽屉（el-drawer）形式从右侧滑出，展示所有参数的完整结构。
3. THE Parameter_Overview_Panel SHALL 提供两种展示模式切换：树形视图（缩进层级展示参数名和类型）和 JSON Schema 视图（以标准 JSON Schema 格式展示参数结构）。
4. THE Parameter_Overview_Panel SHALL 为只读展示，不提供编辑功能。
5. WHEN 用户在 Parameter_Overview_Panel 中点击某个参数名时，THE Parameter_Overview_Panel SHALL 关闭并导航到该参数所在的表视图（自动钻入到对应层级）。

### 需求 4：片段编排 — 拖拽画布与控制节点

**用户故事：** 作为模板设计人员，我希望通过拖拽组件到画布来编排文档结构，并能插入分页符、页眉、页脚、页码规则等控制节点，以便我能精确控制文档的版面布局。

#### 验收标准

1. THE Segment_Canvas SHALL 采用左右分栏布局：左侧为 Component_Panel（约 25% 宽度），右侧为 Canvas_Area（约 75% 宽度）。
2. THE Component_Panel SHALL 显示两组组件：内容片段类型（封面 COVER、目录 TOC、章节 CHAPTER、表格 TABLE、签名 SIGNATURE、法律条款 LEGAL、附录 APPENDIX）和控制节点（分页符、页眉、页脚、页码规则），两组之间以视觉分隔线区分。
3. THE Canvas_Area SHALL 以两种视觉样式区分内容片段和控制节点：Content_Segment_Card 使用白色背景 + 实线边框 + 左侧彩色类型条且高度较大，Control_Node_Card 使用浅灰背景 + 虚线边框 + 小图标且高度约为内容片段的 1/3。
4. WHEN 用户从 Component_Panel 拖拽一个内容片段类型到 Canvas_Area 时，THE Segment_Canvas SHALL 在画布的放置位置创建一个新的 Content_Segment_Card，自动弹出命名输入框让用户输入片段名称，系统自动创建一个空白 .docx 文件（用户无需上传），该空白文件将在第三步"片段详细设计"中通过 OnlyOffice 编辑器进行编辑。IF 用户输入空名称，THEN 命名输入框 SHALL 显示错误提示"片段名称不能为空"；IF 用户输入的名称与已有片段重复，THEN 命名输入框 SHALL 显示错误提示"片段名称已存在"。
5. WHEN 用户从 Component_Panel 拖拽"分页符"控制节点到 Canvas_Area 时，THE Segment_Canvas SHALL 在画布的放置位置插入一个 Page_Break_Node，该节点修改其后方相邻内容片段的 `pageBreakBefore: true` 属性，Page_Break_Node 不创建新的 AssemblySegmentEntry。
6. WHEN 用户从 Component_Panel 拖拽"页眉"控制节点到 Canvas_Area 时，THE Segment_Canvas SHALL 在画布的放置位置插入一个 Header_Node，该节点设置后续片段的页眉内容，Header_Node 以上边框加粗样式显示。
7. WHEN 用户从 Component_Panel 拖拽"页脚"控制节点到 Canvas_Area 时，THE Segment_Canvas SHALL 在画布的放置位置插入一个 Footer_Node，该节点设置后续片段的页脚内容，Footer_Node 以下边框加粗样式显示。
8. WHEN 用户从 Component_Panel 拖拽"页码规则"控制节点到 Canvas_Area 时，THE Segment_Canvas SHALL 在画布的放置位置插入一个 Page_Number_Node，该节点提供页码格式选择（1,2,3 / i,ii,iii / A-1,A-2）和起始值设置（可选"重新从 1 开始"）。
9. WHEN 用户点击 Header_Node 或 Footer_Node 的"编辑"按钮时，THE Segment_Canvas SHALL 打开 Control_Node_Editor（迷你 OnlyOffice 编辑器），加载该控制节点对应的 .docx 片段进行编辑。
10. THE Canvas_Area SHALL 支持拖拽排序：用户可拖拽内容片段和控制节点调整顺序（复用 sortablejs）。
11. WHEN 用户点击 Canvas_Area 中某个 Content_Segment_Card 时，THE Segment_Canvas SHALL 展开该卡片的配置面板，显示：片段名称编辑、启用/禁用开关、条件表达式输入、数据作用域配置。
12. THE Segment_Canvas SHALL 在画布顶部显示"保存"按钮，点击后调用 `updateAssemblyConfig` API 保存当前编排配置。
13. THE Segment_Canvas SHALL 在画布顶部显示撤销/重做按钮（复用现有 `useAssemblyConfig` composable 的 undo/redo 功能）。
14. IF Canvas_Area 中没有任何片段和控制节点，THEN THE Segment_Canvas SHALL 显示空状态提示"从左侧拖拽组件到此处开始编排文档结构"。
15. WHEN 用户点击 Canvas_Area 中某个 Control_Node_Card 的删除图标时，THE Segment_Canvas SHALL 移除该控制节点；IF 删除的是 Page_Break_Node，THEN 恢复其后方片段的 pageBreakBefore 为 false；IF 删除的是 Header_Node 或 Footer_Node，THEN 后续片段的页眉/页脚恢复为上一个同类控制节点的设置（如果没有则无页眉/页脚）。
16. THE Canvas_Area SHALL 对受控制节点影响的内容片段显示影响范围标记：受 Header_Node 影响的片段在卡片右上角显示页眉小图标，受 Footer_Node 影响的片段在卡片右下角显示页脚小图标，受 Page_Number_Node 影响的片段显示页码格式标记。影响范围从控制节点位置开始，到下一个同类控制节点位置结束。
17. WHILE 模板状态不为 DRAFT，THE Segment_Canvas SHALL 禁用拖拽功能、隐藏"保存"按钮和删除按钮，画布以只读模式展示。

> **数据模型扩展**：控制节点的数据通过扩展现有 AssemblySegmentEntry 类型实现，新增字段：headerFilePath（页眉 .docx 路径）、footerFilePath（页脚 .docx 路径）、pageNumberFormat（页码格式：ARABIC/ROMAN/ALPHA）、pageNumberStart（页码起始值）。分页符通过现有 pageBreakBefore 字段实现。

### 需求 5：片段详细设计 — 编辑器与参数侧边栏

**用户故事：** 作为模板设计人员，我希望在编辑片段内容时，右侧有一个参数侧边栏，以便我能通过点击将参数标签插入到编辑器中。

#### 验收标准

1. THE Segment_Detail_Design SHALL 采用左右分栏布局：左侧为 OnlyOffice_Editor 主区域（约 70% 宽度），右侧为 Parameter_Sidebar（约 30% 宽度）。
2. THE Segment_Detail_Design SHALL 在编辑器上方显示片段选择器（el-select 下拉），列出所有已启用且有 filePath 的片段，用户选择片段后加载对应片段的 OnlyOffice 编辑器。
3. THE Parameter_Sidebar SHALL 分为三个可折叠区域：Parameter_List_Section（参数列表）、Loop_Block_Section（循环块）、Condition_Block_Section（条件块）。
4. THE Parameter_List_Section SHALL 以扁平列表展示所有参数（包括嵌套参数的完整路径，如 `company.name`），每个参数显示为可点击的标签卡片。
5. WHEN 用户点击 Parameter_List_Section 中的参数标签时，THE Parameter_Sidebar SHALL 在编辑器光标位置自动插入 `{参数名}` 占位符文本（通过 OnlyOfficeEditor 组件的 `executeCommand` API）。
6. THE Loop_Block_Section SHALL 列出所有 ARRAY 类型参数，每个参数显示为可点击的循环块标签。
7. WHEN 用户点击 Loop_Block_Section 中的循环块标签时，THE Parameter_Sidebar SHALL 在编辑器光标位置自动插入 `{#数组名}\n\n{/数组名}` 循环块文本（通过 OnlyOfficeEditor 组件的 `executeCommand` API）。
8. THE Condition_Block_Section SHALL 提供条件表达式输入框和"添加条件"按钮，用户输入条件表达式后生成可点击的条件块标签。
9. WHEN 用户点击 Condition_Block_Section 中的条件块标签时，THE Parameter_Sidebar SHALL 在编辑器光标位置自动插入 `{#if 条件表达式}\n\n{/if}` 条件块文本（通过 OnlyOfficeEditor 组件的 `executeCommand` API）。
10. THE Parameter_Sidebar SHALL 提供 Inline_Parameter_Creator：一个"+ 新增参数"按钮，点击后展开内联表单（参数名、数据类型下拉），提交后调用 `createParameter` API 创建参数并刷新侧边栏列表，用户不需要回到第一步即可新增参数。
11. WHEN 用户点击 Parameter_Sidebar 的收起按钮时，THE Parameter_Sidebar SHALL 收起为一个窄条图标，编辑器扩展为 100% 宽度。
12. WHILE 模板状态不为 DRAFT，THE Parameter_Sidebar SHALL 以只读模式显示参数列表，隐藏点击插入功能和新增参数入口，编辑器以只读模式打开。
13. IF 当前没有已启用的片段（enabledSegments 为空），THEN THE Segment_Detail_Design SHALL 显示空状态提示"请先在片段编排步骤中添加片段"。

### 需求 6：内容隔离校验

**用户故事：** 作为系统管理员，我希望内容片段、页眉、页脚的编辑区域严格隔离，以便防止用户在错误的区域插入内容导致文档生成异常。

#### 验收标准

1. WHILE 用户在 Segment_Detail_Design 中编辑内容片段时，THE OnlyOffice_Editor SHALL 在编辑器上方显示提示条"此编辑器仅用于编辑正文内容，请勿在此处添加页眉或页脚"，并使用预配置的 .docx 模板文件（仅包含空 body，不包含 header/footer 区域）。
2. WHILE 用户在 Control_Node_Editor 中编辑 Header_Node 时，THE Control_Node_Editor SHALL 在编辑器上方显示提示条"此编辑器仅用于编辑页眉内容"，并使用预配置的 .docx 模板文件（仅包含 header 区域内容）。
3. WHILE 用户在 Control_Node_Editor 中编辑 Footer_Node 时，THE Control_Node_Editor SHALL 在编辑器上方显示提示条"此编辑器仅用于编辑页脚内容"，并使用预配置的 .docx 模板文件（仅包含 footer 区域内容）。
4. WHEN OnlyOffice 回调保存内容片段的 .docx 文件时，THE Content_Isolation_Validator SHALL 解压 .docx 文件并检查 `header*.xml` 和 `footer*.xml` 是否包含非空内容，IF 检测到非空页眉或页脚内容，THEN THE Content_Isolation_Validator SHALL 拒绝保存并返回错误信息。
5. WHEN OnlyOffice 回调保存 Header_Node 的 .docx 文件时，THE Content_Isolation_Validator SHALL 解压 .docx 文件并检查 `document.xml` 的 body 和 `footer*.xml` 是否包含非空内容，IF 检测到非空 body 或 footer 内容，THEN THE Content_Isolation_Validator SHALL 拒绝保存并返回错误信息。
6. WHEN OnlyOffice 回调保存 Footer_Node 的 .docx 文件时，THE Content_Isolation_Validator SHALL 解压 .docx 文件并检查 `document.xml` 的 body 和 `header*.xml` 是否包含非空内容，IF 检测到非空 body 或 header 内容，THEN THE Content_Isolation_Validator SHALL 拒绝保存并返回错误信息。
7. THE Content_Isolation_Validator SHALL 作为后端服务集成到 `OnlyOfficeService.handleCallback` 流程中，在保存文件到 MinIO 之前执行校验。

### 需求 7：设计阶段工具栏整合

**用户故事：** 作为模板设计人员，我希望设计阶段的工具栏在三步子流程中保持一致，以便导入 ZIP 和设置功能始终可用。

#### 验收标准

1. THE Design_Stage SHALL 在 Design_Step_Indicator 上方显示统一工具栏，包含"导入 ZIP"按钮和齿轮图标（设置）按钮。
2. WHEN 用户点击"导入 ZIP"按钮时，THE Design_Stage SHALL 打开文件选择器，仅接受 .zip 文件，并调用现有的 `importCompositeFromZip` API（复用现有 DesignStage 的导入逻辑）。
3. WHEN 用户点击齿轮图标时，THE Design_Stage SHALL 打开 SettingsPopover（复用现有 SettingsPopover 组件）。
4. WHILE 模板状态不为 DRAFT，THE Design_Stage SHALL 隐藏"导入 ZIP"按钮。
5. THE Design_Stage SHALL 在工具栏中显示当前片段选择器（仅在第三步"片段详细设计"时可见），用于切换编辑的片段。

### 需求 8：拖拽交互一致性

**用户故事：** 作为模板设计人员，我希望三步子流程中的拖拽交互保持一致的视觉反馈，以便我能形成统一的操作直觉。

#### 验收标准

1. THE Design_Stage SHALL 在所有拖拽操作中使用统一的视觉反馈：拖拽源元素半透明化（opacity: 0.5），拖拽目标位置显示蓝色插入线指示器，无效放置区域显示禁止光标。
2. WHEN 用户在 Parameter_Table_Design 中拖拽字段行调整排序时，THE Parameter_Table_Design SHALL 显示蓝色插入线指示放置位置，并在放置后更新 sortOrder。
3. WHEN 用户在 Segment_Canvas 中拖拽片段卡片或控制节点时，THE Canvas_Area SHALL 显示蓝色插入线指示放置位置。
4. WHEN 用户在 Parameter_Sidebar 中点击参数标签时，THE OnlyOffice_Editor 区域 SHALL 在光标位置插入对应文本并短暂高亮显示已插入内容。
5. THE Design_Stage SHALL 使用 sortablejs（已安装）实现所有拖拽功能，保持拖拽库的统一性。

### 需求 9：参数侧边栏搜索与过滤

**用户故事：** 作为模板设计人员，我希望参数侧边栏支持搜索和过滤，以便在参数较多时快速找到需要的参数。

#### 验收标准

1. THE Parameter_Sidebar SHALL 在顶部提供搜索输入框，支持按参数名称模糊搜索。
2. WHEN 用户在搜索框中输入文本时，THE Parameter_Sidebar SHALL 实时过滤三个区域（参数列表、循环块、条件块）中的参数，仅显示名称包含搜索文本的参数。
3. THE Parameter_List_Section SHALL 支持按数据类型过滤：提供类型筛选标签（STRING、NUMBER、DATE、BOOLEAN、ARRAY、OBJECT），点击标签切换过滤。
4. WHEN 搜索或过滤结果为空时，THE Parameter_Sidebar SHALL 显示"无匹配参数"提示。

### 需求 10：i18n 三语言支持

**用户故事：** 作为国际化用户，我希望设计阶段三步子流程的所有文本都支持三种语言。

#### 验收标准

1. THE Design_Stage SHALL 为三个子步骤名称（参数表设计/片段编排/片段详细设计）提供 en-US、zh-CN、zh-TW 三种语言的翻译。
2. THE Design_Stage SHALL 为组件面板中的内容片段类型名称（封面/目录/章节/表格/签名/法律条款/附录）提供三种语言的翻译。
3. THE Design_Stage SHALL 为组件面板中的控制节点名称（分页符/页眉/页脚/页码规则）提供三种语言的翻译。
4. THE Design_Stage SHALL 为参数侧边栏的区域标题（参数列表/循环块/条件块）和操作按钮提供三种语言的翻译。
5. THE Design_Stage SHALL 为面包屑导航中的"主表"标签和表类型标识（子表/关联表）提供三种语言的翻译。
6. THE Design_Stage SHALL 使用 `workspace.design.step.{stepName}` 命名规范定义子步骤名称的 i18n key。


## 正确性属性

### CP-1: 参数层级视图与数据模型同构 (需求 2)
对于任意参数树结构，Parameter_Table_Design 的表/子表/关联表视图必须与 ParameterDTO 树同构：根级 STRING/NUMBER/DATE/BOOLEAN 参数显示为主表字段，根级 ARRAY 参数显示为子表链接，根级 OBJECT 参数显示为关联表链接。递归地，每个子表/关联表内部的参数遵循相同映射规则。面包屑层级深度等于当前查看的参数在树中的深度。

### CP-2: 片段编排画布与 AssemblyConfig 双向一致 (需求 4)
对于任意 Canvas_Area 中的片段卡片序列，其顺序和属性必须与 `useAssemblyConfig` composable 中的 `segments` 数组严格一致：内容片段卡片数量等于 segments.length，第 i 个内容片段卡片的名称等于 segments[i].name，第 i 个内容片段卡片的 position 等于 i。控制节点不计入 segments 数组，而是通过修改相邻片段的属性（如 pageBreakBefore）来生效。保存后，后端返回的 AssemblyConfig 与画布状态一致。

### CP-3: 参数侧边栏标签插入正确性 (需求 5)
对于任意参数 P，从 Parameter_List_Section 点击插入的文本必须严格等于 `{P.parameterPath}`；从 Loop_Block_Section 点击插入的文本必须严格等于 `{#P.name}\n\n{/P.name}`（仅 ARRAY 类型参数出现在循环块区域）；从 Condition_Block_Section 点击插入的文本必须严格等于 `{#if expr}\n\n{/if}`。

### CP-4: 子步骤切换状态保持 (需求 1)
在设计阶段的三个子步骤之间切换时，每个子步骤的编辑状态必须保持：参数表设计的当前面包屑位置、片段编排的未保存更改、片段详细设计的当前选中片段，在切换回来后必须恢复到切换前的状态。

### CP-5: 拖拽排序后 position 连续性 (需求 4, 8)
在 Canvas_Area 或 Parameter_Table_Design 中执行拖拽排序后，所有同级元素的 position/sortOrder 值必须是从 0 开始的连续整数序列，序列长度等于同级元素数量。

### CP-6: 内容隔离校验完备性 (需求 6)
对于任意 .docx 文件，Content_Isolation_Validator 的校验结果必须满足：(a) 内容片段的 .docx 中 header*.xml 和 footer*.xml 均为空或不存在时通过校验，否则拒绝；(b) Header_Node 的 .docx 中 document.xml body 和 footer*.xml 均为空或不存在时通过校验，否则拒绝；(c) Footer_Node 的 .docx 中 document.xml body 和 header*.xml 均为空或不存在时通过校验，否则拒绝。校验为幂等操作，对同一文件多次校验结果一致。

### CP-7: 控制节点与内容片段视觉区分 (需求 4)
在 Canvas_Area 中，所有 Content_Segment_Card 和 Control_Node_Card 必须在视觉上明确区分：Content_Segment_Card 使用白色背景 + 实线边框 + 左侧彩色类型条，Control_Node_Card 使用浅灰背景 + 虚线边框 + 小图标，两者高度比约为 3:1。

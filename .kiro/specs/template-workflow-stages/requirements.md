# 需求文档：模板工作流阶段化重构

## 简介

将模板工作区从当前的 7 个平铺 Tab + 8 步指示器结构，重构为苹果式四阶段工作流：**设计 → 测试 → 审批 → 发布**。设计理念为"封闭但强大，渐进式披露，一次只做一件事"。每个阶段聚焦单一目标，不可用的操作直接隐藏而非灰显。API 管理从工作流中移除，作为独立页面存在。后端模板状态机（DRAFT → PENDING_REVIEW → REVIEWED → ACTIVE → ARCHIVED）保持不变。

## 术语表

- **Template_Workspace**：模板工作区主页面（`frontend/src/views/template-workspace/Index.vue`），用户编辑和管理单个模板的主界面
- **Stage_Indicator**：四阶段指示器组件，替代现有的八步 `WorkflowStepIndicator.vue`，显示设计/测试/审批/发布四个阶段
- **Design_Stage**：设计阶段，包含编辑器主区域 + 参数伴随抽屉 + 片段编排弹出面板 + 工具栏（导入 ZIP、设置齿轮）
- **Test_Stage**：测试阶段，左右分栏布局：测试数据表单面板（左 40%）+ 文档预览（右 60%），顶部覆盖率条
- **Approval_Stage**：审批阶段，审批时间线 + 只读模板快照预览
- **Publish_Stage**：发布阶段，发布摘要卡片 + 激活按钮 + 导出和新版本按钮
- **Parameter_Drawer**：参数伴随抽屉，编辑器右侧可收起的参数面板（30% 宽度），类似 Figma 属性面板
- **Segment_Popover**：片段编排弹出面板，从顶部工具栏按钮触发的弹出面板，替代独立的片段编排 Tab
- **Settings_Popover**：设置弹出面板，从齿轮图标触发，收纳版本历史、权限、Webhook 功能
- **Test_Data_Form**：测试数据表单，根据参数表自动生成的表单控件，替代现有的 JSON 编辑器
- **Coverage_Bar**：覆盖率条，显示分支/循环/参数三维度覆盖率的顶部进度条
- **Approval_Timeline**：审批时间线，以聊天记录式时间线展示审批历史
- **Publish_Summary_Card**：发布摘要卡片，显示版本号、参数数量、覆盖率、审核状态
- **API_Management_Page**：API 管理独立页面，从模板列表页或顶部导航进入，管理版本列表、端点信息、API Key、调用统计
- **TemplateState**：模板状态枚举（`backend/src/main/java/com/docgen/entity/TemplateState.java`），包含 DRAFT、PENDING_REVIEW、REVIEWED、ACTIVE、ARCHIVED
- **Template_State_Machine**：模板状态机服务（`TemplateStateMachineService`），管理状态转换
- **Composite_Coverage_Report**：组合模板覆盖率报告（`CompositeCoverageReport`），包含 overallCoveragePercent 和 segmentCoverages，通过 `GET /api/composite-templates/{id}/coverage` 获取
- **Template_Coverage_Report**：单模板三维覆盖率报告（`CoverageReport`），包含 branchCoverage、loopCoverage、parameterCoverage、overallCoverage，通过 `GET /api/templates/{id}/coverage` 获取
- **Composite_Template_Controller**：组合模板控制器（`CompositeTemplateController`），提供导出 ZIP 等端点

## 需求

### 需求 1：四阶段指示器与导航

**用户故事：** 作为模板设计人员，我希望工作区顶部有一个清晰的四阶段进度指示器，以便我能一眼看到"我在哪、我能做什么、下一步去哪"。

#### 验收标准

1. THE Stage_Indicator SHALL 显示四个阶段：设计、测试、审批、发布，按此固定顺序从左到右排列。
2. THE Stage_Indicator SHALL 为每个阶段显示以下状态之一：未开始（灰色圆圈）、进行中（蓝色脉冲动画）、已完成（绿色勾选图标）。
3. WHEN 用户点击一个可用阶段的指示器时，THE Template_Workspace SHALL 切换到该阶段的视图。
4. WHEN 用户点击一个不可用阶段的指示器时，THE Stage_Indicator SHALL 不响应点击事件（不可用阶段的指示器不可点击）。
5. THE Stage_Indicator SHALL 在相邻阶段之间显示连接线，已完成阶段之间的连接线为绿色实线，未完成阶段之间的连接线为灰色虚线。
6. THE Stage_Indicator SHALL 替换现有的八步 WorkflowStepIndicator 组件和 `useWorkflowSteps` composable。

### 需求 2：阶段可用性规则

**用户故事：** 作为模板设计人员，我希望工作区根据模板当前状态自动控制各阶段的可用性，以便我不会看到当前无法执行的操作。

#### 验收标准

1. WHILE 模板状态为 DRAFT，THE Template_Workspace SHALL 将设计阶段和测试阶段标记为可用，将审批阶段和发布阶段在 Stage_Indicator 中显示为灰色不可点击状态（用户可以看到完整的四阶段流程，但无法进入未解锁的阶段）。
2. WHILE 模板状态为 DRAFT 且 Composite_Coverage_Report 的 overallCoveragePercent 达到 100，THE Template_Workspace SHALL 额外将审批阶段标记为可用（允许提交审核）。
3. WHILE 模板状态为 PENDING_REVIEW，THE Template_Workspace SHALL 将审批阶段标记为当前活跃阶段，设计阶段和测试阶段标记为只读可查看，发布阶段在 Stage_Indicator 中显示为灰色不可点击状态。
4. WHILE 模板状态为 REVIEWED，THE Template_Workspace SHALL 将发布阶段标记为当前活跃阶段，其余三个阶段标记为只读可查看。
5. WHILE 模板状态为 ACTIVE，THE Template_Workspace SHALL 将发布阶段标记为当前活跃阶段，其余三个阶段标记为只读可查看。
6. WHILE 模板状态为 ARCHIVED，THE Template_Workspace SHALL 将所有四个阶段标记为只读可查看。

### 需求 3：阶段完成条件判定

**用户故事：** 作为模板设计人员，我希望阶段指示器能自动判断每个阶段是否完成，以便我能直观地了解模板的整体进度。

#### 验收标准

1. THE Stage_Indicator SHALL 在以下条件全部满足时将设计阶段标记为已完成：模板已创建、至少定义一个参数、至少一个片段已启用且已编辑（filePath 非空）。
2. THE Stage_Indicator SHALL 在 Composite_Coverage_Report 的 overallCoveragePercent 达到 100 时将测试阶段标记为已完成。
3. THE Stage_Indicator SHALL 在模板状态为 REVIEWED 或 ACTIVE 时将审批阶段标记为已完成。
4. THE Stage_Indicator SHALL 在模板状态为 ACTIVE 时将发布阶段标记为已完成。

### 需求 4：设计阶段 — 编辑器与参数伴随抽屉

**用户故事：** 作为模板设计人员，我希望在设计阶段看到编辑器占主区域、参数面板作为右侧可收起抽屉，以便我能在编辑模板的同时管理参数。

#### 验收标准

1. THE Design_Stage SHALL 将编辑器（OnlyOffice 编辑器或片段列表视图）显示在主区域，占据约 70% 宽度。
2. THE Design_Stage SHALL 将 Parameter_Drawer 显示在编辑器右侧，占据约 30% 宽度，作为可收起的抽屉面板。
3. WHEN 用户点击 Parameter_Drawer 的收起按钮时，THE Parameter_Drawer SHALL 收起为一个窄条图标，编辑器扩展为 100% 宽度。
4. WHEN 用户点击 Parameter_Drawer 顶部的"扫描占位符"按钮时，THE Parameter_Drawer SHALL 调用扫描 API 检测编辑器中的占位符，并高亮显示新发现的参数条目。
5. THE Parameter_Drawer SHALL 复用现有 ParameterTableTab 组件的核心功能（参数 CRUD、树形表格、扫描、JSON 导入），但以抽屉面板形式呈现。
6. WHILE 模板状态不为 DRAFT，THE Parameter_Drawer SHALL 以只读模式显示参数列表，隐藏所有编辑操作按钮。

### 需求 5：设计阶段 — 片段编排弹出面板

**用户故事：** 作为模板设计人员，我希望片段编排从独立 Tab 改为工具栏的弹出面板，以便我不需要离开编辑器就能管理片段顺序。

#### 验收标准

1. THE Design_Stage SHALL 在顶部工具栏显示一个"片段编排"按钮。
2. WHEN 用户点击"片段编排"按钮时，THE Segment_Popover SHALL 以弹出面板形式显示在编辑器上方，覆盖编辑器区域。
3. THE Segment_Popover SHALL 复用现有 SegmentArrangementTab 组件的核心功能（拖拽排序、启用/禁用、条件表达式、数据作用域配置）。
4. WHEN 用户点击 Segment_Popover 外部区域或关闭按钮时，THE Segment_Popover SHALL 关闭并返回编辑器视图。
5. IF Segment_Popover 中存在未保存的更改且用户尝试关闭面板，THEN THE Segment_Popover SHALL 显示确认对话框询问是否保存更改。

### 需求 6：设计阶段 — 工具栏与设置

**用户故事：** 作为模板设计人员，我希望导入 ZIP 和设置功能收纳在工具栏中，以便设计阶段界面保持简洁。

#### 验收标准

1. THE Design_Stage SHALL 在顶部工具栏显示"导入 ZIP"按钮，仅在设计阶段可见。
2. WHEN 用户点击"导入 ZIP"按钮时，THE Template_Workspace SHALL 打开文件选择器，仅接受 .zip 文件，并调用现有的 `POST /api/composite-templates/import` 端点。
3. THE Design_Stage SHALL 在顶部工具栏显示齿轮图标按钮。
4. WHEN 用户点击齿轮图标时，THE Settings_Popover SHALL 以弹出面板形式显示，包含版本历史、权限管理、Webhook 配置（复用现有 SettingsTab 的子组件）。
5. WHILE 模板状态不为 DRAFT，THE Design_Stage SHALL 隐藏"导入 ZIP"按钮。

### 需求 7：测试阶段 — 左右分栏布局

**用户故事：** 作为模板设计人员，我希望测试阶段以左右分栏展示测试数据和文档预览，以便我能直观地看到测试数据对文档的影响。

#### 验收标准

1. THE Test_Stage SHALL 以左右分栏布局显示：左侧 40% 为测试数据面板，右侧 60% 为文档预览区域。
2. THE Test_Stage SHALL 在顶部显示 Coverage_Bar，通过调用 `GET /api/templates/{id}/coverage` 获取 Template_Coverage_Report（三维度详细覆盖率），展示分支覆盖率（branchCoverage）、循环覆盖率（loopCoverage）、参数覆盖率（parameterCoverage）三个维度的进度条。注意：阶段完成条件判定（需求 2、3）使用 store 中已有的 Composite_Coverage_Report.overallCoveragePercent，而 Coverage_Bar 使用 Template_Coverage_Report 的三维度数据提供更详细的展示。
3. THE Coverage_Bar SHALL 在三个维度全部达到 100% 时显示绿色成功状态，否则显示对应维度的当前百分比。

### 需求 8：测试阶段 — 测试数据表单

**用户故事：** 作为模板设计人员，我希望测试数据输入从 JSON 编辑器改为自动生成的表单，以便我不需要了解 JSON 语法就能填写测试数据。

#### 验收标准

1. THE Test_Data_Form SHALL 根据模板参数表自动生成表单控件：STRING 类型生成文本输入框，NUMBER 类型生成数字输入框，BOOLEAN 类型生成开关，DATE 类型生成日期选择器；当参数的 validation_rules 包含 enum_values 时，生成下拉选择器替代文本/数字输入框。
2. THE Test_Data_Form SHALL 对 OBJECT 类型参数生成可折叠的嵌套表单组，对 ARRAY 类型参数生成可增减条目的动态列表。
3. WHEN 参数定义了 required 属性为 true 时，THE Test_Data_Form SHALL 在对应表单控件上显示必填标记，并在提交时验证非空。
4. WHEN 参数定义了 defaultValue 时，THE Test_Data_Form SHALL 将该值作为表单控件的初始值。
5. THE Test_Data_Form SHALL 提供"查看 JSON"切换按钮，允许用户在表单模式和 JSON 编辑模式之间切换。

### 需求 9：测试阶段 — 实时预览与智能引导

**用户故事：** 作为模板设计人员，我希望预览区域能实时更新，并且系统能主动提示未覆盖的分支，以便我能高效地完成测试覆盖。

#### 验收标准

1. WHEN 用户在 Test_Data_Form 中修改任意字段值时，THE Test_Stage SHALL 在 500ms 防抖延迟后自动调用预览接口并更新右侧文档预览区域。
2. THE Test_Stage SHALL 在 Coverage_Bar 下方显示智能引导区域，列出当前未覆盖的分支和条件表达式。
3. WHEN 用户点击某个未覆盖分支的引导条目时，THE Test_Stage SHALL 在 Test_Data_Form 中高亮显示与该分支相关的参数字段，并在字段旁显示提示文本说明需要填入什么值才能触发该分支（例如"将 contract_type 设为 enterprise 以触发此分支"）。
4. IF 预览接口调用失败，THEN THE Test_Stage SHALL 在预览区域显示错误信息和重试按钮，保留当前表单数据不变。

### 需求 10：审批阶段 — 审批时间线

**用户故事：** 作为模板设计人员，我希望审批历史以时间线形式展示，以便我能像查看聊天记录一样直观地了解审批进度。

#### 验收标准

1. THE Approval_Stage SHALL 在主区域显示 Approval_Timeline，以垂直时间线形式展示所有审批记录，每条记录包含审批人姓名、审批状态（待审核/通过/有条件通过/拒绝）、评论内容和时间戳。
2. THE Approval_Stage SHALL 在右侧显示只读模板快照预览（调用现有预览接口）。
3. WHILE 模板状态为 DRAFT 且 Composite_Coverage_Report 的 overallCoveragePercent 未达到 100，THE Approval_Stage SHALL 不可进入（Stage_Indicator 中审批阶段显示为灰色不可点击，用户无法切换到审批阶段视图）。
4. WHILE 模板状态为 DRAFT 且 Composite_Coverage_Report 的 overallCoveragePercent 达到 100（审批阶段可见），THE Approval_Stage SHALL 显示提交审核按钮。
5. WHEN 审核被拒绝时，THE Approval_Stage SHALL 在时间线底部显示"返回修改"按钮。
6. WHEN 用户点击"返回修改"按钮时，THE Template_Workspace SHALL 刷新模板数据（后端已自动将状态回退到 DRAFT）并自动切换到设计阶段。
7. WHEN 所有审核通过后（后端 AutoActivationService 已自动完成 PENDING_REVIEW → REVIEWED → ACTIVE 转换），THE Template_Workspace SHALL 刷新模板数据并自动切换到发布阶段。

### 需求 11：发布阶段 — 摘要卡片与激活

**用户故事：** 作为模板设计人员，我希望发布阶段有一个清晰的摘要卡片和大按钮，以便我能一键完成发布操作。

#### 验收标准

1. THE Publish_Stage SHALL 显示 Publish_Summary_Card，包含以下信息：版本号、参数数量、覆盖率百分比、审核状态。
2. WHILE 模板状态为 REVIEWED，THE Publish_Stage SHALL 在摘要卡片下方居中显示一个大尺寸"激活"按钮。
3. WHEN 用户点击"激活"按钮时，THE Publish_Stage SHALL 调用 `POST /api/templates/{id}/activate` 端点将模板状态转换为 ACTIVE。
4. WHILE 模板状态为 ACTIVE，THE Publish_Stage SHALL 隐藏"激活"按钮，替换为"导出 ZIP 包"按钮和"编辑为新版本"按钮。
5. WHEN 用户点击"导出 ZIP 包"按钮时，THE Publish_Stage SHALL 调用 `GET /api/composite-templates/{id}/export` 端点下载完整 ZIP 包。
6. WHEN 用户点击"编辑为新版本"按钮时，THE Publish_Stage SHALL 调用 `createDraftVersion` 接口创建草稿版本，刷新模板数据并自动切换到设计阶段。

### 需求 12：导出阶段约束（后端）

**用户故事：** 作为系统管理员，我希望后端强制执行导出约束，以便确保只有 ACTIVE 状态的模板才能被导出为完整 ZIP 包。

#### 验收标准

1. WHEN 后端收到 `GET /api/composite-templates/{id}/export` 请求且模板状态不为 ACTIVE 时，THE Composite_Template_Controller SHALL 返回 HTTP 400 错误，错误码为 TEMPLATE_EXPORT_NOT_ACTIVE。
2. THE ErrorCode 类 SHALL 新增常量 `TEMPLATE_EXPORT_NOT_ACTIVE = "TEMPLATE_EXPORT_NOT_ACTIVE"`。

### 需求 13：API 管理独立页面

**用户故事：** 作为模板设计人员，我希望 API 管理从工作流中移除，作为独立页面存在，以便工作流保持简洁聚焦。

#### 验收标准

1. THE API_Management_Page SHALL 作为独立路由页面存在，路径为 `/templates/:id/api`。
2. THE API_Management_Page SHALL 显示以下内容：该模板的 ACTIVE 版本列表、API 端点信息、API Key 管理、调用统计、默认版本设置。
3. THE API_Management_Page SHALL 复用现有 ApiEndpointInfo 组件的端点信息展示功能。
4. WHEN 用户在模板列表页点击某模板行的"API"图标按钮时，THE Template_Workspace SHALL 导航到该模板的 API_Management_Page。
5. THE MainLayout 导航菜单 SHALL 不为 API_Management_Page 添加独立菜单项（通过模板列表页的按钮进入）。

### 需求 14：版本编辑与阶段回退

**用户故事：** 作为模板设计人员，我希望创建新版本或审核被拒后能自动回到设计阶段，以便我能从头开始新的编辑流程。

> 注：具体的回退触发点已在需求 10 AC6（审核拒绝→返回修改）和需求 11 AC6（编辑为新版本）中定义，本需求作为汇总引用。

#### 验收标准

1. WHEN 用户在 ACTIVE 模板上点击"编辑为新版本"按钮后（参见需求 11 AC6），THE Template_Workspace SHALL 刷新模板数据并自动切换到设计阶段的编辑器视图。
2. WHEN 审核被拒绝且用户点击"返回修改"按钮后（参见需求 10 AC6），THE Template_Workspace SHALL 刷新模板数据并自动切换到设计阶段的编辑器视图。

### 需求 15：阶段切换时的数据按需加载

**用户故事：** 作为模板设计人员，我希望切换阶段时数据能按需加载，以便工作区的初始加载速度不受影响。

#### 验收标准

1. WHEN 用户切换到测试阶段时，THE Template_Workspace SHALL 按需加载测试用例列表和覆盖率数据（如果尚未加载）。
2. WHEN 用户切换到审批阶段时，THE Template_Workspace SHALL 按需加载审核记录数据（如果尚未加载）。
3. THE Template_Workspace SHALL 在阶段切换时显示加载骨架屏，直到该阶段的数据加载完成。
4. IF 阶段数据加载失败，THEN THE Template_Workspace SHALL 在该阶段视图内显示错误提示和重试按钮。

### 需求 16：i18n 支持

**用户故事：** 作为国际化用户，我希望所有新增的阶段名称和提示文本都支持三种语言。

#### 验收标准

1. THE Template_Workspace SHALL 为四个阶段名称（设计/测试/审批/发布）提供 en-US、zh-CN、zh-TW 三种语言的翻译。
2. THE Template_Workspace SHALL 为阶段不可用原因提示、覆盖率条标签、审批时间线状态文本、发布摘要卡片标签提供三种语言的翻译。
3. THE Template_Workspace SHALL 使用 `workspace.stage.{stageName}` 命名规范定义阶段名称的 i18n key。
4. THE API_Management_Page SHALL 为页面标题和所有功能标签提供三种语言的翻译。

### 需求 17：移除的功能

**用户故事：** 作为产品负责人，我希望明确记录本次重构中移除的功能，以便团队了解变更范围。

#### 验收标准

1. THE Template_Workspace SHALL 移除独立的"数据结构"Tab 页（参数管理功能迁移到 Parameter_Drawer）。
2. THE Template_Workspace SHALL 移除独立的"片段编排"Tab 页（功能迁移到 Segment_Popover）。
3. THE Template_Workspace SHALL 移除独立的"导出/导入"Tab 页（导入迁移到设计阶段工具栏，导出迁移到发布阶段）。
4. THE Template_Workspace SHALL 移除独立的"设置"Tab 页（功能迁移到 Settings_Popover）。
5. THE Template_Workspace SHALL 从工作流中移除 API 端点信息展示（迁移到 API_Management_Page）。
6. THE Template_Workspace SHALL 移除 JSON 配置导入/导出功能（仅保留 ZIP 包导入/导出）。


## 正确性属性

### CP-1: 阶段可用性与状态一致性 (需求 2)
对于任意模板状态 S，Stage_Indicator 显示的可用阶段集合必须严格等于需求 2 中定义的映射：DRAFT→{设计,测试}，DRAFT+100%覆盖→{设计,测试,审批}，PENDING_REVIEW→{设计(只读),测试(只读),审批}，REVIEWED→{设计(只读),测试(只读),审批(只读),发布}，ACTIVE→同 REVIEWED，ARCHIVED→全部只读。

### CP-2: 阶段完成条件单调性 (需求 3)
阶段完成状态是单调递增的：一旦设计阶段标记为已完成，在同一版本内不会回退为未完成（除非创建新版本）。测试、审批、发布阶段同理。

### CP-3: 测试数据表单与参数表同构 (需求 8)
对于任意参数树结构，Test_Data_Form 生成的表单控件树必须与参数树同构：每个参数对应一个表单控件，OBJECT 对应嵌套表单组，ARRAY 对应动态列表，叶子节点对应类型适配的输入控件。

### CP-4: 导出约束后端强制 (需求 12)
对于任意非 ACTIVE 状态的模板，导出 ZIP 请求必须返回 HTTP 400，不会生成任何文件。

### CP-5: 阶段回退状态一致性 (需求 14)
执行"编辑为新版本"或"返回修改"后，模板状态必须为 DRAFT，且 Template_Workspace 必须显示设计阶段视图。

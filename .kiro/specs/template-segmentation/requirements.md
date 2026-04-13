# 需求文档 - 模板分段/组件化

## 简介

模板分段/组件化功能旨在解决当前低代码文档生成系统中长模板编辑和管理不友好的问题。当前系统的模板以单一 .docx 文件存储，对于包含数十甚至上百页的复杂文档模板，编辑、维护和协作效率低下。本功能引入"段落（Segment）"和"组件模板（Component Template）"概念，将长模板拆分为可独立编辑、独立版本控制、可复用的小单元，并通过组合配置将多个段落/组件拼装为完整模板，同时支持按段增量渲染和预览。

### 核心设计理念

本功能借鉴现代前端组件化架构（Atomic Design）和 Headless CMS 的内容块（Content Block）理念，将文档模板视为可组合的组件树：

- **原子化拆分**: 将长模板拆分为最小可编辑单元（Segment），每个 Segment 是一个独立的 .docx 文件片段
- **组合优于继承**: 通过 Assembly_Config 定义组合关系，而非模板继承，降低耦合度
- **引用而非复制**: Component_Template 以引用方式被多个 Composite_Template 共享，实现"单一数据源"原则
- **渐进式迁移**: 完全兼容现有单文件模板，支持按需迁移，零停机升级
- **段落级协作**: 不同团队成员可并行编辑不同段落，通过权限控制和版本管理避免冲突

## 术语表

- **Document_Generation_System**: 低代码文档生成系统，本需求文档描述的核心系统
- **Template**: 文档模板，定义文档结构和数据绑定规则的配置，以 .docx 格式存储在 MinIO 中；通过 `template_type` 字段区分传统单文件模板（SINGLE）和组合模板（COMPOSITE）
- **Composite_Template**: 组合模板，由多个 Segment 按照指定顺序组合而成的模板，本身不包含文档内容，仅定义段落的组合关系和顺序
- **Segment**: 段落，模板的最小可编辑单元，每个段落是一个独立的 .docx 文件片段，可独立编辑、独立版本控制；归属于特定租户
- **Component_Template**: 组件模板，一种特殊的可复用 Segment（`is_component = true`），可被多个 Composite_Template 引用，修改后所有引用方自动获取更新
- **Segment_Version**: 段落版本，Segment 的历史版本记录，每次修改保存时创建新版本，包含 .docx 文件快照（在 MinIO 中独立存储）
- **Assembly_Config**: 组装配置，定义 Composite_Template 中各 Segment 的排列顺序、是否启用、条件渲染规则、分页符配置、版本锁定和数据作用域映射等，以 JSONB 格式存储在数据库中
- **Template_Designer**: 模板设计器，用于创建和编辑文档模板的可视化工具
- **Document_Generator**: 文档生成器，根据模板和数据生成最终文档的组件，底层通过 RestTemplate + CircuitBreaker 调用 Docxtemplater 服务
- **Docxtemplater_Service**: Docxtemplater 服务，独立的 Node.js 服务，负责解析 .docx 模板并填充数据生成最终文档，提供 `/render`、`/evaluate`、`/convert-pdf`、`/health` 端点
- **OnlyOffice_Editor**: OnlyOffice Document Editor，在线文档编辑器，通过自定义插件支持插入 Docxtemplater 模板变量
- **MinIO**: MinIO 对象存储服务（S3 兼容），用于存储模板文件和生成的文档
- **Segment_Renderer**: 段落渲染器，负责对单个 Segment 进行数据填充和渲染的组件，调用 Docxtemplater_Service 的 `/render` 端点
- **Assembly_Engine**: 组装引擎，负责将多个已渲染的 Segment 按照 Assembly_Config 合并为最终文档的组件
- **Segment_Data_Scope**: 段落数据作用域，定义单个 Segment 可访问的数据变量范围，支持从 Composite_Template 的全局数据上下文中映射子集，避免变量命名冲突
- **Dependency_Graph**: 依赖图，描述 Component_Template 与 Composite_Template 之间引用关系的有向图，用于影响分析、变更通知和级联操作保护

## 需求

### 需求 1: 段落创建与管理

**用户故事:** 作为模板设计者，我希望能够将长模板拆分为多个独立的段落，以便分别编辑和管理各个部分。

#### 验收标准

1. THE Template_Designer SHALL 提供创建新 Segment 的功能，每个 Segment 包含名称（最长 200 字符）、描述（TEXT 类型）和一个独立的 .docx 文件
2. WHEN 用户创建 Segment 时，THE Document_Generation_System SHALL 在 MinIO 中为该 Segment 创建独立的 .docx 文件（路径格式: `segments/{tenantId}/{uuid}_{filename}`）并将元数据持久化到数据库
3. THE Document_Generation_System SHALL 为每个 Segment 记录 tenant_id 并添加 Hibernate `@Filter(name = "tenantFilter")`，确保 Segment 数据在租户之间完全隔离
4. THE Template_Designer SHALL 提供在 OnlyOffice_Editor 中独立编辑单个 Segment 的功能，复用现有 OnlyOfficeEditor.vue 组件
5. THE Template_Designer SHALL 提供 Segment 列表查询功能，支持按名称模糊搜索、按标签筛选和分页（复用现有分页模式 `Page<SegmentDTO>`）
6. THE Template_Designer SHALL 提供删除 Segment 的功能
7. IF 用户尝试删除一个被 Composite_Template 引用的 Segment，THEN THE Document_Generation_System SHALL 拒绝删除并返回包含引用该 Segment 的 Composite_Template 名称列表的错误响应（ErrorCode: `SEGMENT_REFERENCED`，HTTP 409 Conflict）
8. THE Template_Designer SHALL 提供复制已有 Segment 创建新 Segment 的功能
9. WHEN 用户复制 Segment 时，THE Document_Generation_System SHALL 为新 Segment 生成独立的名称（原名称加"- 副本"后缀）和独立的 .docx 文件副本（通过 MinIO CopyObject 操作）
10. THE Document_Generation_System SHALL 在 Segment 创建、修改、删除、复制时通过 AuditLogService 记录审计日志（包含操作者、操作时间、Segment ID、操作类型和变更摘要）
11. THE Template_Designer SHALL 支持为 Segment 添加标签（复用现有 TemplateTag 和 TemplateTagMapping 机制）


### 需求 2: 组合模板创建与管理

**用户故事:** 作为模板设计者，我希望能够通过组合多个段落来构建完整的文档模板，以便灵活管理长模板的结构。

#### 验收标准

1. THE Template_Designer SHALL 提供创建 Composite_Template 的功能，Composite_Template 包含名称、描述和 Assembly_Config
2. THE Template_Designer SHALL 提供可视化的拖拽排序界面，用于配置 Composite_Template 中各 Segment 的排列顺序
3. WHEN 用户向 Composite_Template 添加 Segment 时，THE Document_Generation_System SHALL 在 Assembly_Config（JSONB）中记录该 Segment 的引用 ID、排列位置和默认配置（启用状态、分页符、版本锁定、数据作用域）
4. THE Template_Designer SHALL 支持在 Composite_Template 中为每个 Segment 配置是否启用（默认启用）
5. THE Template_Designer SHALL 支持在 Composite_Template 中为每个 Segment 配置条件渲染表达式（JavaScript 或 Excel 公式，复用现有 ExpressionEngine），WHEN 条件表达式计算结果为 false 时，THE Document_Generator SHALL 跳过该 Segment 的渲染
6. THE Template_Designer SHALL 支持在 Composite_Template 中配置相邻 Segment 之间是否插入分页符（默认插入）
7. THE Template_Designer SHALL 提供 Composite_Template 的整体预览功能，将所有启用的 Segment 按顺序组合后展示完整文档效果
8. WHEN 用户保存 Composite_Template 时，THE Document_Generation_System SHALL 验证 Assembly_Config 中至少包含一个启用的 Segment，IF 验证失败，THEN THE Document_Generation_System SHALL 返回验证错误响应（ErrorCode: `COMPOSITE_TEMPLATE_EMPTY`，HTTP 422）
9. IF Composite_Template 的 Assembly_Config 引用了不存在的 Segment ID，THEN THE Document_Generation_System SHALL 在保存时返回包含无效 Segment ID 列表的验证错误响应（ErrorCode: `SEGMENT_NOT_FOUND`，HTTP 422）
10. THE Document_Generation_System SHALL 为 Composite_Template 记录 tenant_id 并添加 Hibernate `@Filter(name = "tenantFilter")`，确保组合模板数据在租户之间完全隔离
11. THE Composite_Template SHALL 复用现有 Template 实体的状态机（TemplateStateMachineService: DRAFT → PENDING_REVIEW → REVIEWED → ACTIVE → ARCHIVED），通过 `template_type` 字段（值为 `COMPOSITE`）区分
12. WHEN Composite_Template 激活时，THE Document_Generation_System SHALL 验证所有引用的 Segment 均存在且可访问，IF 验证失败，THEN THE Document_Generation_System SHALL 阻止激活并返回包含不可用 Segment 列表的错误响应
13. THE Template_Designer SHALL 在 Composite_Template 编辑界面中展示各 Segment 的缩略预览（名称、描述、最新版本号、组件/普通标识）

### 需求 3: 组件模板（可复用段落）

**用户故事:** 作为模板设计者，我希望能够创建可复用的组件模板，以便在多个文档模板中共享相同的内容块（如公司信息头、法律声明尾部、标准条款等）。

#### 验收标准

1. THE Template_Designer SHALL 支持将 Segment 标记为 Component_Template（通过 `is_component` 布尔字段）
2. THE Template_Designer SHALL 提供 Component_Template 的独立管理界面，展示所有可复用组件的列表（支持按名称搜索、按标签筛选和分页）
3. WHEN 用户将 Component_Template 添加到 Composite_Template 时，THE Document_Generation_System SHALL 以引用方式关联（非复制），多个 Composite_Template 共享同一个 Component_Template 实例
4. WHEN Component_Template 被修改并保存时，THE Document_Generation_System SHALL 通过 Dependency_Graph 查询所有引用该 Component_Template 的 Composite_Template 列表
5. THE Template_Designer SHALL 在 Component_Template 详情页展示引用关系列表（哪些 Composite_Template 正在使用该组件），包含引用数量统计和各引用方的状态
6. THE Template_Designer SHALL 支持将普通 Segment 提升为 Component_Template（设置 `is_component = true`）
7. THE Template_Designer SHALL 支持将 Component_Template 降级为普通 Segment（仅当该 Component_Template 被 0 个或 1 个 Composite_Template 引用时允许降级）
8. IF 用户尝试将被 2 个及以上 Composite_Template 引用的 Component_Template 降级为普通 Segment，THEN THE Document_Generation_System SHALL 拒绝操作并返回包含引用方列表的错误响应（ErrorCode: `COMPONENT_REFERENCED`，HTTP 409 Conflict）
9. THE Document_Generation_System SHALL 确保 Component_Template 的引用关系仅在同一租户范围内生效，禁止跨租户引用
10. THE Template_Designer SHALL 支持在 Component_Template 管理界面中按引用数量排序，快速识别高复用组件

### 需求 4: 段落独立版本控制

**用户故事:** 作为模板设计者，我希望每个段落都有独立的版本历史，以便追踪段落级别的变更并支持独立回滚。

#### 验收标准

1. WHEN Segment 被修改并保存时，THE Document_Generation_System SHALL 创建新的 Segment_Version，包含版本号（严格递增，复用现有 TemplateVersion 的版本号递增模式）、.docx 文件快照（在 MinIO 中路径格式: `segment-versions/{tenantId}/{segmentId}/{versionNumber}_{uuid}.docx`）、修改者 ID 和修改时间
2. THE Template_Designer SHALL 提供查看单个 Segment 版本历史列表的功能（按版本号降序排列，分页展示）
3. THE Template_Designer SHALL 提供将 Segment 回滚到指定历史版本的功能
4. WHEN Segment 回滚时，THE Document_Generation_System SHALL 基于历史版本创建新的 Segment_Version（非覆盖历史记录，版本号继续递增，复用现有 TemplateService.rollbackToVersion 的模式）
5. THE Template_Designer SHALL 提供对比 Segment 两个版本之间文本内容差异的功能（复用现有 VersionDiffService 的差异检测逻辑）
6. WHEN Component_Template 创建新版本时，THE Document_Generation_System SHALL 向所有引用该 Component_Template 的 Composite_Template 的设计者发送变更通知（通过系统内通知机制）
7. THE Template_Designer SHALL 支持在 Composite_Template 的 Assembly_Config 中为每个 Segment 引用锁定到特定版本号（默认使用最新版本）
8. WHEN Assembly_Config 中锁定了特定版本的 Segment，IF 该版本的 .docx 文件在 MinIO 中不存在，THEN THE Document_Generation_System SHALL 在文档生成时返回包含缺失版本信息的错误响应（ErrorCode: `SEGMENT_VERSION_FILE_MISSING`）
9. THE Document_Generation_System SHALL 在 Segment 版本创建和回滚时通过 AuditLogService 记录审计日志

### 需求 5: 段落数据作用域

**用户故事:** 作为模板设计者，我希望能够为每个段落定义独立的数据作用域，以便在组合模板中精确控制每个段落可访问的数据变量，避免变量命名冲突。

#### 验收标准

1. THE Template_Designer SHALL 支持为 Composite_Template 中的每个 Segment 配置 Segment_Data_Scope（数据作用域映射），存储在 Assembly_Config 的 JSONB 中
2. THE Segment_Data_Scope SHALL 支持从 Composite_Template 的全局数据上下文中选择子集映射到 Segment 的局部变量（格式: `{"localVar": "global.path.to.field"}`）
3. WHEN 未配置 Segment_Data_Scope 时，THE Document_Generator SHALL 将 Composite_Template 的完整数据上下文传递给该 Segment（向后兼容默认行为）
4. WHEN 配置了 Segment_Data_Scope 时，THE Document_Generator SHALL 仅将映射的数据子集传递给该 Segment 进行渲染
5. THE Template_Designer SHALL 提供可视化的数据映射配置界面，展示全局变量列表（从 Composite_Template 关联的数据源和表达式中获取）和 Segment 局部变量的对应关系
6. IF Segment_Data_Scope 中映射的全局变量在文档生成时不存在，THEN THE Document_Generator SHALL 记录警告日志并将该变量设置为 null
7. THE Template_Designer SHALL 支持 Segment_Data_Scope 的快捷配置：一键将 Segment 中扫描到的所有 Docxtemplater 变量自动映射到同名全局变量

### 需求 6: 段落级变量扫描与覆盖率

**用户故事:** 作为模板设计者，我希望能够扫描每个段落中使用的模板变量，并查看组合模板的整体数据绑定覆盖率，以便确保所有段落的变量都已正确绑定。

#### 验收标准

1. WHEN Segment 的 .docx 文件保存时，THE Document_Generation_System SHALL 自动扫描该 Segment 中所有 Docxtemplater 变量（复用现有 TemplateVariableService 的 `extractVariableNames` 逻辑，从 .docx ZIP 中提取 word/*.xml 内容并匹配变量模式）
2. THE Template_Designer SHALL 提供单个 Segment 的变量列表查看功能，展示变量名称、类型、绑定状态
3. THE Document_Generation_System SHALL 为 Composite_Template 提供聚合覆盖率检查功能：汇总所有 Segment 的变量，计算整体绑定覆盖率（已绑定变量数 / 总变量数 × 100%）
4. THE Template_Designer SHALL 在 Composite_Template 编辑界面中展示每个 Segment 的独立覆盖率指标和整体覆盖率指标
5. IF Composite_Template 的整体覆盖率低于配置阈值（默认 100%），THEN THE Document_Generation_System SHALL 在激活时显示警告（复用现有 CoverageCheckService 的阈值检查逻辑）
6. THE Template_Designer SHALL 在 Segment 变量列表中标注"未绑定"变量，并提供快捷绑定入口

### 需求 7: 增量渲染与预览

**用户故事:** 作为模板设计者，我希望能够按段落单独渲染和预览，以便在编辑长模板时快速验证单个段落的效果而无需渲染整个文档。

#### 验收标准

1. THE Template_Designer SHALL 提供单个 Segment 的独立预览功能
2. WHEN 用户触发单个 Segment 预览时，THE Segment_Renderer SHALL 通过 RestTemplate + CircuitBreaker 调用 Docxtemplater_Service 的 `/render` 端点，仅渲染该 Segment 的 .docx 文件并返回预览结果
3. THE Template_Designer SHALL 支持为单个 Segment 预览提供测试数据（JSON 格式，通过 Monaco Editor 编辑）
4. THE Template_Designer SHALL 提供 Composite_Template 的选择性预览功能，用户可勾选需要预览的 Segment 子集
5. WHEN 用户触发 Composite_Template 完整预览时，THE Assembly_Engine SHALL 按照 Assembly_Config 的顺序依次渲染各 Segment 并合并为完整文档
6. WHEN Composite_Template 包含条件渲染的 Segment 时，THE Assembly_Engine SHALL 根据提供的测试数据通过 ExpressionEngine 计算条件表达式，仅渲染条件为 true 的 Segment
7. THE Document_Generator SHALL 在 Composite_Template 的文档生成流程中按段落顺序渲染，IF 某个 Segment 渲染失败，THEN THE Document_Generator SHALL 在响应中标注失败的 Segment 名称、Segment ID 和错误原因，并继续渲染后续 Segment（部分失败模式）
8. THE Template_Designer SHALL 支持在 OnlyOffice_Editor 中直接预览渲染后的 Segment 文档（无需下载，复用现有 OnlyOfficeEditor.vue 的 viewOnly 模式）
9. THE Template_Designer SHALL 支持保存 Segment 的测试数据集（JSON 格式），以便重复使用


### 需求 8: 组合模板文档生成

**用户故事:** 作为 API 调用者，我希望基于组合模板生成完整文档时，系统能够自动将各段落渲染并合并为一个完整的文档文件。

#### 验收标准

1. WHEN 文档生成请求指定 Composite_Template 时，THE Document_Generator SHALL 按照 Assembly_Config 定义的顺序依次渲染各 Segment（复用现有 DocumentGeneratorService 的数据管道执行流程：数据获取 → 表达式计算 → 渲染）
2. THE Assembly_Engine SHALL 将所有已渲染的 Segment 合并为一个完整的 .docx 文件（通过 Docxtemplater_Service 的新端点 `POST /merge-segments` 实现）
3. WHEN Assembly_Config 中配置了分页符时，THE Assembly_Engine SHALL 在相邻 Segment 之间插入分页符
4. THE Document_Generator SHALL 支持 Composite_Template 的异步文档生成（复用现有 AsyncDocumentService，任务类型标记为 `COMPOSITE_GENERATE`）
5. THE Document_Generator SHALL 支持 Composite_Template 的批量文档生成（复用现有 BatchDocumentService 和 Spring Batch 框架）
6. WHEN Composite_Template 中的 Segment 引用了 Component_Template 时，THE Document_Generator SHALL 使用 Component_Template 的当前版本（或 Assembly_Config 中锁定的特定版本号）进行渲染
7. THE Generated_API SHALL 对 Composite_Template 和传统单文件 Template 使用统一的 API 端点（`POST /api/generate/{templateId}`），通过 Template 实体的 `template_type` 字段自动选择渲染策略
8. WHEN Composite_Template 中所有 Segment 均被条件渲染跳过时，THE Document_Generator SHALL 返回包含说明信息的响应（ErrorCode: `GENERATE_ALL_SEGMENTS_SKIPPED`，HTTP 200，body 中标注无内容原因和被跳过的 Segment 列表）
9. THE Document_Generator SHALL 在 Composite_Template 文档生成完成后，在响应元数据中包含各 Segment 的渲染耗时统计（segmentId、segmentName、renderTimeMs），用于性能分析
10. THE Document_Generator SHALL 支持 Composite_Template 生成 Word (.docx) 和 PDF 两种格式（复用现有 `convertToPdf` 方法和 Docxtemplater_Service 的 `/convert-pdf` 端点）
11. THE Document_Generator SHALL 对 Composite_Template 的渲染过程使用 CircuitBreaker 保护（复用现有 Resilience4j 配置），单个 Segment 渲染超时不影响其他 Segment
12. WHEN Composite_Template 配置了 Webhook 时，THE Document_Generation_System SHALL 在文档生成完成或失败时发送 Webhook 通知（复用现有 WebhookService），通知 payload 中包含各 Segment 的渲染状态

### 需求 9: 传统模板兼容与迁移

**用户故事:** 作为模板设计者，我希望现有的单文件模板能够继续正常使用，并且可以按需将其转换为组合模板格式。

#### 验收标准

1. THE Document_Generation_System SHALL 保持对传统单文件 Template 的完全兼容，所有现有模板无需修改即可继续使用
2. THE Document_Generation_System SHALL 在 Template 实体中新增 `template_type` 字段（VARCHAR(20)，值为 `SINGLE` 或 `COMPOSITE`），默认值为 `SINGLE` 以确保所有现有模板自动兼容（通过 Flyway 迁移 V29 添加）
3. THE Template_Designer SHALL 提供将传统单文件 Template 转换为 Composite_Template 的迁移工具（前端提供"转换为组合模板"按钮）
4. WHEN 用户使用迁移工具时，THE Document_Generation_System SHALL 将原始 .docx 文件作为单个 Segment 导入到新创建的 Composite_Template 中，并自动创建 Assembly_Config
5. THE Template_Designer SHALL 支持在迁移后手动将单个 Segment 拆分为多个 Segment（通过在 OnlyOffice_Editor 中选择内容范围并提取为新 Segment）
6. THE Document_Generation_System SHALL 在迁移过程中保留原始模板的所有配置（数据源、表达式、验证规则、变量绑定），将这些配置关联到新创建的 Composite_Template
7. WHEN 迁移完成后，THE Document_Generation_System SHALL 保留原始单文件模板作为归档副本（通过 TemplateStateMachineService 将状态设置为 ARCHIVED），不自动删除
8. IF 迁移过程中原始模板的 .docx 文件无法从 MinIO 读取，THEN THE Document_Generation_System SHALL 终止迁移并返回包含文件访问错误详情的响应（ErrorCode: `MIGRATION_FILE_ACCESS_FAILED`）
9. THE Document_Generation_System SHALL 在迁移操作时通过 AuditLogService 记录审计日志（包含源模板 ID、目标组合模板 ID、迁移时间和操作者）

### 需求 10: 段落级权限控制

**用户故事:** 作为团队管理员，我希望能够为不同的段落分配不同的编辑权限，以便在团队协作中控制谁可以编辑哪些段落。

#### 验收标准

1. THE Document_Generation_System SHALL 支持为 Segment 分配独立的访问权限（VIEW、EDIT），复用现有 PermissionService 的权限模型和 Permission 实体（通过 `resource_type = 'SEGMENT'` 和 `resource_id = segmentId` 区分）
2. WHEN 用户尝试编辑 Segment 时，THE Document_Generation_System SHALL 通过 PermissionService 验证用户是否具有该 Segment 的 EDIT 权限
3. IF 用户无权编辑 Segment，THEN THE Document_Generation_System SHALL 返回 403 禁止访问错误（ErrorCode: `AUTH_ACCESS_DENIED`）
4. THE Template_Designer SHALL 在 Composite_Template 的段落列表中标注当前用户对每个 Segment 的权限状态（可编辑/只读图标）
5. THE Document_Generation_System SHALL 确保段落权限控制在租户隔离的范围内生效（PermissionService 查询自动附加 tenant_id 过滤）
6. THE Template_Designer SHALL 提供 Segment 权限管理界面，支持按用户或按团队分配 VIEW/EDIT 权限（复用现有权限管理 UI 模式）

### 需求 11: 段落级审查支持

**用户故事:** 作为团队管理员，我希望能够对组合模板中的各个段落进行独立审查，以便不同审查人负责审查不同的段落内容。

#### 验收标准

1. THE Template_Designer SHALL 支持对 Composite_Template 发起审查时，为每个 Segment 指定独立的审查人（复用现有 TemplateReviewService 的审查工作流）
2. THE Template_Designer SHALL 支持审查人在 OnlyOffice_Editor 中对单个 Segment 添加批注和修改建议（复用现有 TemplateReviewService.getOnlyOfficeReviewUrl 模式）
3. WHEN 所有 Segment 的审查均通过时，THE Document_Generation_System SHALL 自动将 Composite_Template 的整体审查状态更新为通过
4. IF 任一 Segment 的审查被驳回，THEN THE Document_Generation_System SHALL 将 Composite_Template 的整体审查状态标记为驳回，并在响应中标注被驳回的 Segment 名称和驳回原因
5. THE Template_Designer SHALL 在 Composite_Template 审查界面中展示各 Segment 的独立审查状态（待审查/已通过/条件通过/已驳回）

### 需求 12: 组合模板导入导出

**用户故事:** 作为模板设计者，我希望能够导入和导出组合模板及其所有段落，以便在不同环境之间迁移或备份。

#### 验收标准

1. THE Template_Designer SHALL 支持将 Composite_Template 的完整配置导出为 JSON 文件，包含 Assembly_Config、所有 Segment 的元数据和变量绑定信息（复用现有 TemplateImportExportService 的导出模式）
2. THE Template_Designer SHALL 支持将 Composite_Template 的所有 Segment .docx 文件和配置 JSON 打包为 ZIP 文件导出
3. THE Template_Designer SHALL 支持通过导入 ZIP 文件还原 Composite_Template 的完整配置和所有 Segment 文件
4. WHEN 导入 ZIP 文件时，THE Document_Generation_System SHALL 验证 ZIP 包结构的完整性（必须包含 config.json 和至少一个 .docx 文件），IF 验证失败，THEN THE Document_Generation_System SHALL 返回包含具体错误位置的验证失败响应（ErrorCode: `IMPORT_INVALID_FILE`）
5. WHEN 导入的 Composite_Template 引用了 Component_Template 时，THE Document_Generation_System SHALL 检查目标环境中是否存在同名 Component_Template，IF 存在则自动关联，IF 不存在则将其作为普通 Segment 导入

### 需求 13: 组合模板定时任务与 Webhook 集成

**用户故事:** 作为模板设计者，我希望组合模板能够与现有的定时任务和 Webhook 功能无缝集成，以便实现自动化文档生成和通知。

#### 验收标准

1. THE Document_Generation_System SHALL 支持为 Composite_Template 配置定时生成任务（复用现有 ScheduledTaskService，通过 Cron 表达式配置调度规则）
2. WHEN 定时任务触发 Composite_Template 的文档生成时，THE Document_Generation_System SHALL 按照 Assembly_Config 执行完整的段落渲染和合并流程
3. THE Document_Generation_System SHALL 支持为 Composite_Template 配置 Webhook 通知（复用现有 WebhookService），在文档生成完成或失败时发送通知
4. THE Webhook 通知 payload SHALL 包含 Composite_Template 特有的信息：各 Segment 的渲染状态（成功/失败/跳过）和渲染耗时
5. THE Document_Generation_System SHALL 在定时任务执行历史中记录 Composite_Template 的段落级执行详情（各 Segment 的渲染结果）


### 需求 14: 组合模板测试套件

**用户故事:** 作为模板设计者，我希望能够为组合模板创建测试用例并支持段落级回归测试，以便确保段落修改不会影响整体文档输出。

#### 验收标准

1. THE Template_Designer SHALL 支持为 Composite_Template 创建测试用例（复用现有 TemplateTestService 的测试用例模型：测试数据 + 预期结果 + 比对方式）
2. THE Template_Designer SHALL 支持为单个 Segment 创建独立的测试用例，验证段落级渲染结果
3. WHEN 运行 Composite_Template 的测试用例时，THE Document_Generation_System SHALL 执行完整的段落渲染和合并流程，并将最终文档与预期结果进行比对
4. THE Template_Designer SHALL 支持一键运行 Composite_Template 的所有测试用例（包含段落级和整体级）
5. THE Document_Generation_System SHALL 在测试报告中展示各 Segment 的独立渲染结果和整体合并结果
6. WHEN Segment 被修改并保存时，THE Document_Generation_System SHALL 提供自动运行该 Segment 关联的所有测试用例的选项
7. THE Document_Generation_System SHALL 支持三种预期结果比对方式（复用现有 ComparisonType：变量值比对、文本内容比对、文件快照比对）

### 需求 15: 组合模板与模板市场集成

**用户故事:** 作为模板设计者，我希望能够将组合模板分享到模板市场，也能从市场获取组合模板，以便复用优秀的组合模板设计。

#### 验收标准

1. THE Template_Market SHALL 支持展示和搜索 Composite_Template 类型的市场模板（复用现有 TemplateMarketService 的搜索逻辑，通过 template_type 筛选）
2. WHEN 用户从市场复制 Composite_Template 时，THE Document_Generation_System SHALL 创建该模板的完整独立副本，包含所有 Segment 的 .docx 文件副本和 Assembly_Config
3. WHEN 市场中的 Composite_Template 包含 Component_Template 引用时，THE Document_Generation_System SHALL 将 Component_Template 作为独立 Segment 复制到用户工作区（断开原始引用关系）
4. THE Template_Designer SHALL 支持将 Composite_Template 分享到模板市场（复用现有 ShareScope: TENANT_INTERNAL 或 GLOBAL）
5. THE Template_Market SHALL 在 Composite_Template 的市场详情页展示段落数量和组件数量信息

### 需求 16: 组合模板水印与安全

**用户故事:** 作为模板设计者，我希望组合模板生成的文档也能应用水印和安全保护，以便与传统模板保持一致的安全能力。

#### 验收标准

1. THE Document_Generator SHALL 支持在 Composite_Template 文档生成完成（所有 Segment 合并后）应用文字水印和图片水印（复用现有 WatermarkService）
2. THE Document_Generator SHALL 支持 Composite_Template 的动态水印内容（使用全局数据上下文中的模板变量作为水印文本，复用现有 WatermarkService.resolveTemplateVariables 逻辑）
3. THE Document_Generator SHALL 支持 Composite_Template 生成的 PDF 文档设置打开密码和权限控制（复用现有 PDF 转换流程）
4. THE Template_Designer SHALL 在 Composite_Template 配置界面中提供水印和安全选项的配置入口（复用现有水印配置 UI）

### 需求 17: 组合模板 API 注册与限流

**用户故事:** 作为 API 调用者，我希望组合模板激活后也能自动注册 API 端点，并受到与传统模板相同的限流保护。

#### 验收标准

1. WHEN Composite_Template 激活时，THE Document_Generation_System SHALL 通过 DynamicApiService 自动注册对应的 RESTful API 端点（`POST /api/generate/{templateId}`），与传统模板共用统一端点
2. THE DynamicApiService SHALL 支持 Composite_Template 的版本选择（通过 `?version={versionNumber}` 查询参数），版本号对应 Composite_Template 的整体版本而非单个 Segment 版本
3. THE RateLimitService SHALL 对 Composite_Template 的 API 调用执行与传统模板相同的限流策略（每秒/每分钟/每小时频率限制和月度配额）
4. THE Document_Generation_System SHALL 在 API 文档（SpringDoc OpenAPI）中自动包含 Composite_Template 的端点描述，标注其为组合模板类型

### 需求 18: 组合模板数据源与表达式管理

**用户故事:** 作为模板设计者，我希望能够为组合模板配置数据源和表达式，以便在文档生成时为各段落提供数据。

#### 验收标准

1. THE Template_Designer SHALL 支持为 Composite_Template 配置数据源（复用现有 DataSourceCrudService，数据源关联到 Composite_Template 的 template_id）
2. THE Template_Designer SHALL 支持为 Composite_Template 配置表达式（复用现有 ExpressionCrudService，表达式关联到 Composite_Template 的 template_id）
3. WHEN Composite_Template 文档生成时，THE Document_Generator SHALL 执行 Composite_Template 级别的数据管道（复用现有 DataPipelineService：数据获取 → 转换 → 计算 → 验证），生成全局数据上下文
4. THE Document_Generator SHALL 将全局数据上下文（或通过 Segment_Data_Scope 映射的子集）传递给各 Segment 进行渲染
5. THE Template_Designer SHALL 在 Composite_Template 的数据源配置界面中展示各数据源被哪些 Segment 的变量引用（反向依赖关系）
6. THE Document_Generation_System SHALL 支持 Composite_Template 的数据源缓存（复用现有 DataSourceCacheService 和 Redis 缓存机制）

### 需求 19: 仪表板段落统计

**用户故事:** 作为系统管理员，我希望在仪表板中看到段落和组合模板的统计信息，以便全面了解系统使用情况。

#### 验收标准

1. THE DashboardService SHALL 在系统概览中包含段落相关统计：Segment 总数、Component_Template 总数、Composite_Template 总数（扩展现有 SystemOverviewDTO）
2. THE DashboardService SHALL 在文档生成统计中区分传统模板和组合模板的生成量和成功率
3. THE DashboardService SHALL 提供 Component_Template 复用率排行（按引用数量降序排列的 Top 10 组件）
4. THE DashboardService SHALL 在 API 调用监控中标注 Composite_Template 的调用量占比


### 需求 20: 组合模板编辑器交互增强

**用户故事:** 作为模板设计者，我希望组合模板的编辑体验流畅直观，以便高效地组装和调整段落结构。

#### 验收标准

1. THE Template_Designer SHALL 在 Composite_Template 编辑界面中提供段落快速搜索面板，支持按名称实时搜索并一键添加 Segment 到当前组合模板（无需离开编辑界面）
2. THE Template_Designer SHALL 在拖拽排序界面中提供实时拖拽视觉反馈（拖拽占位符、插入位置指示线、拖拽中的段落半透明预览）
3. THE Template_Designer SHALL 支持通过键盘快捷键调整段落顺序（Alt+↑ 上移、Alt+↓ 下移），满足无障碍访问要求
4. THE Template_Designer SHALL 在 Composite_Template 编辑界面中提供大纲导航视图（左侧面板展示所有段落的树形结构），点击段落名称可快速定位到对应配置区域
5. THE Template_Designer SHALL 支持对段落的批量操作：批量启用、批量禁用、批量移除（通过复选框多选后执行）
6. THE Template_Designer SHALL 在 Composite_Template 编辑界面中实时显示预估总页数（基于各 Segment 的 .docx 文件页数累加计算）
7. THE Template_Designer SHALL 在首次创建 Composite_Template 时提供引导流程（空状态页面展示"添加第一个段落"的操作提示和快速入口）
8. THE Template_Designer SHALL 支持 Assembly_Config 变更的撤销/重做操作（Ctrl+Z / Ctrl+Shift+Z），至少支持最近 20 步操作历史

### 需求 21: 段落模板与智能推荐

**用户故事:** 作为模板设计者，我希望系统能够提供段落模板和智能推荐，以便快速创建常用类型的段落并发现可复用的内容。

#### 验收标准

1. THE Template_Designer SHALL 提供预置的段落模板库（如：封面页、目录页、章节标题、表格数据页、签名页、法律声明页、附录页），用户可从模板库一键创建新 Segment
2. THE Template_Designer SHALL 支持用户将自己创建的 Segment 保存为自定义段落模板，以便在后续项目中复用
3. THE Template_Designer SHALL 在 Composite_Template 编辑界面中提供"推荐段落"功能：基于当前已添加的段落类型和标签，推荐可能需要的其他段落（如已添加"合同正文"则推荐"签名页"和"法律声明"）
4. THE Document_Generation_System SHALL 提供段落复用分析功能：扫描同一租户下所有 Composite_Template，识别内容相似度高（文本内容重复率 > 80%）的非组件 Segment，建议用户将其提取为 Component_Template
5. THE Template_Designer SHALL 支持为 Segment 添加类型标签（如 COVER、TOC、CHAPTER、TABLE、SIGNATURE、LEGAL、APPENDIX），用于分类管理和智能推荐

### 需求 22: 实时协作与冲突管理

**用户故事:** 作为团队成员，我希望在多人同时编辑组合模板时能够感知其他人的操作，以便避免编辑冲突。

#### 验收标准

1. THE Template_Designer SHALL 在 Composite_Template 编辑界面中展示当前正在编辑各 Segment 的用户列表（用户头像/名称 + 正在编辑的 Segment 名称）
2. WHEN 用户尝试编辑一个已被其他用户正在编辑的 Segment 时，THE Template_Designer SHALL 显示警告提示（"用户 XXX 正在编辑此段落"），并提供"仍然编辑"和"查看只读"两个选项
3. THE Document_Generation_System SHALL 通过 Redis 维护 Segment 编辑锁状态（key 格式: `segment-lock:{segmentId}`，value: userId，TTL: 30 分钟，用户活跃时自动续期）
4. WHEN 用户关闭 Segment 编辑器或超过 30 分钟无操作时，THE Document_Generation_System SHALL 自动释放该 Segment 的编辑锁
5. THE Template_Designer SHALL 在 Composite_Template 的段落列表中用视觉标识（如彩色边框或锁图标）标注当前被其他用户编辑中的 Segment

### 需求 23: 组合模板分类与搜索增强

**用户故事:** 作为模板设计者，我希望能够对段落和组合模板进行分类管理和高效搜索，以便在大量段落中快速找到需要的内容。

#### 验收标准

1. THE Template_Designer SHALL 支持为 Segment 分配到现有分类体系中（复用现有 CategoryService 的树形分类）
2. THE Template_Designer SHALL 支持 Composite_Template 的全文搜索，搜索范围包含组合模板名称、描述以及其包含的所有 Segment 名称
3. THE Template_Designer SHALL 在 Segment 列表中提供多维度筛选：按分类、按标签、按类型（普通/组件）、按创建时间范围、按创建者
4. THE Template_Designer SHALL 支持将常用 Segment 标记为收藏，并提供收藏列表快速访问入口
5. THE Template_Designer SHALL 在 Composite_Template 列表中展示段落数量和组件数量的统计信息，支持按段落数量排序

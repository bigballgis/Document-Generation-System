# Implementation Plan: 工作台导出/导入与设置标签页 (Workspace Export/Import & Settings)

## Overview

Phase 4（最终阶段）将 P1 骨架中最后 2 个占位标签页替换为真实实现，按依赖顺序编排：后端变更（CompositeImportExportService 扩展 ZIP 导出/导入内容）→ Store 扩展（versions、permissions）→ 前端组件（ExportImportTab、SettingsTab、VersionHistoryPanel、VersionDiffPanel、PermissionPanel、GrantPermissionDialog）→ Index.vue 集成 → i18n → Property-Based Tests → 单元测试。后端核心变更为 CompositeImportExportService 扩展导出内容（新增 data-sources.json 含凭据脱敏、expressions.json、test-data.json、coverage-report.json）及导入时解析扩展文件（含旧格式向后兼容）。前端核心工作为 ExportImportTab 和 SettingsTab 两个标签页组件及其子组件的实现。

## Tasks

- [x] 1. 后端 — CompositeImportExportService 扩展 ZIP 导出/导入
  - [x] 1.1 扩展 `CompositeImportExportService` 构造器注入新依赖
    - 修改文件: `backend/src/main/java/com/docgen/service/CompositeImportExportService.java`
    - 新增构造器参数: `DataSourceRepository dataSourceRepository`、`ExpressionRepository expressionRepository`、`TestCaseRepository testCaseRepository`、`CompositeCoverageService compositeCoverageService`
    - 新增对应的 `private final` 字段赋值
    - _Requirements: 7.5_
  - [x] 1.2 实现 `exportAsZip` 方法扩展 — 新增 4 个文件到 ZIP
    - 在现有 `config.json` + `segments/*.docx` 写入之后，新增：
      - `data-sources.json`：调用 `dataSourceRepository.findByTemplateId(templateId)` 获取数据源列表，通过 `toMaskedDataSourceMap()` 脱敏后序列化写入
      - `expressions.json`：调用 `expressionRepository.findByTemplateId(templateId)` 获取表达式列表，通过 `toExpressionExportMap()` 序列化写入
      - `test-data.json`：调用 `testCaseRepository.findByTemplateId(templateId)` 获取测试数据列表，通过 `toTestCaseExportMap()` 序列化写入
      - `coverage-report.json`：调用 `compositeCoverageService.checkCoverage(templateId)` 获取覆盖率报告序列化写入；失败时 WARN 日志并跳过，不影响导出
    - _Requirements: 7.1, 7.5, 7.6_
  - [x] 1.3 实现凭据脱敏辅助方法
    - 新增常量 `CREDENTIAL_PLACEHOLDER = "__CREDENTIAL_PLACEHOLDER__"`
    - 新增 `toMaskedDataSourceMap(DataSource ds)` 方法：将 DataSource 转为 Map，解析 `configJson` 并调用 `maskCredentialFields` 脱敏
    - 新增 `maskCredentialFields(Map<String, Object> config, String type)` 方法：DATABASE 类型脱敏 `password`；所有类型脱敏顶层 `apiKey`、`clientSecret`；递归处理嵌套 `auth` 对象中的 `apiKey`、`clientSecret`、`password`
    - 新增 `toExpressionExportMap(Expression expr)` 方法：导出 name、expressionType、expressionText、description、executionOrder
    - 新增 `toTestCaseExportMap(TestCase tc)` 方法：导出 name、testDataJson、expectedResultJson、comparisonType
    - _Requirements: 7.2_
  - [x] 1.4 扩展 `importFromZip` 方法 — 解析扩展文件并导入
    - 在 ZIP 解析循环中新增对 `data-sources.json`、`expressions.json`、`test-data.json` 的识别（`coverage-report.json` 被忽略）
    - 在现有 segment 和 template 创建逻辑之后，按条件调用：
      - `importDataSources(bytes, templateId, tenantId)` — 解析 JSON 数组，逐个创建 DataSource 记录
      - `importExpressions(bytes, templateId, tenantId)` — 解析 JSON 数组，逐个创建 Expression 记录
      - `importTestData(bytes, templateId)` — 解析 JSON 数组，逐个创建 TestCase 记录
    - 扩展文件不存在时跳过（向后兼容旧格式 ZIP）
    - 扩展文件解析失败时 WARN 日志，不影响基础导入
    - _Requirements: 7.3, 7.4_
  - [x] 1.5 编写后端单元测试 — CompositeImportExportService 扩展
    - 创建或扩展测试文件: `backend/src/test/java/com/docgen/service/CompositeImportExportServiceTest.java`
    - 测试 exportAsZip 导出 ZIP 包含 config.json + segments/*.docx + data-sources.json + expressions.json + test-data.json + coverage-report.json
    - 测试 data-sources.json 中 DATABASE 类型的 password 被替换为 `__CREDENTIAL_PLACEHOLDER__`
    - 测试 data-sources.json 中 apiKey 和 clientSecret 被替换为 `__CREDENTIAL_PLACEHOLDER__`
    - 测试非敏感字段保持原值
    - 测试无数据源/表达式/测试数据时对应 JSON 文件为空数组 `[]`
    - 测试覆盖率报告生成失败时 ZIP 仍然成功（无 coverage-report.json）
    - 测试 importFromZip 含扩展文件的 ZIP → 创建数据源 + 表达式 + 测试数据记录
    - 测试 importFromZip 旧格式 ZIP（无扩展文件）→ 仅导入 config.json + segments，无错误
    - 测试 data-sources.json 解析失败 → WARN 日志，基础导入不受影响
    - 测试 maskCredentialFields 各种场景（DATABASE password、HTTP_API apiKey、嵌套 auth 对象、无敏感字段）
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6_

- [x] 2. Checkpoint — 确保后端编译通过、单元测试通过
  - 运行 `mvn compile` 确认无编译错误
  - 运行后端单元测试确认通过
  - 确认 CompositeImportExportService 扩展完成且新增依赖注入正确

- [x] 3. Store 扩展 — 新增 versions / permissions 状态与 actions
  - [x] 3.1 修改 `frontend/src/stores/templateWorkspace.ts`
    - 新增 import: `getTemplateVersions` from `@/api/templates`、`getTemplatePermissions` from `@/api/admin`
    - 新增 import 类型: `TemplateVersionDTO`（from `@/api/templates`）、`PermissionDTO`（from `@/api/admin`）
    - 新增 state: `const versions = ref<TemplateVersionDTO[]>([])`、`const permissions = ref<PermissionDTO[]>([])`
    - 注意：versions 和 permissions 不在 `initWorkspace` 中加载（按需加载，避免无效 API 调用）
    - 新增 `refreshVersions()` action：调用 `getTemplateVersions(templateId.value)`，成功时更新 `versions.value` 并删除 `warnings.value.versions`，失败时设置 `warnings.value.versions`
    - 新增 `refreshPermissions()` action：调用 `getTemplatePermissions(templateId.value)`，成功时更新 `permissions.value` 并删除 `warnings.value.permissions`，失败时设置 `warnings.value.permissions`
    - 在 `$reset` 中新增 `versions.value = []`、`permissions.value = []`
    - 在 return 中导出 `versions, permissions, refreshVersions, refreshPermissions`
    - _Requirements: 8.4, 8.5, 8.6_


- [x] 4. 前端组件 — ExportImportTab.vue
  - [x] 4.1 创建 `frontend/src/views/template-workspace/components/ExportImportTab.vue`
    - 从 store 读取数据，无 props
    - 两个区域垂直排列，`el-divider` 分隔：Cross_Environment_Export（上）→ Cross_Environment_Import（下）
    - **导出区域**：
      - 导出摘要卡片：`el-row` + 4 个 `el-col`，分别显示 segment count（`store.assemblyConfig.segments.length`）、data source count（`store.dataSources.length`）、expression count（`store.expressions.length`）、test data count（`store.testCases.length`），使用 styled number-label pair
      - "Export Complete Package" 按钮（`el-button` type="primary"）：点击 → `exportCompositeAsZip(store.templateId)` → `triggerDownload(blob, 'composite-template-{id}.zip')`
      - "Export Config Only" 按钮（`el-button`）：点击 → `exportCompositeConfig(store.templateId)` → `triggerDownload(blob, 'composite-config-{id}.json')`
      - DRAFT 状态 → "Export Complete Package" 按钮禁用 + `el-tooltip` 显示 i18n key `workspace.exportImport.draftExportDisabled`
      - 导出中 → 两个按钮均禁用 + loading 状态
      - 导出失败 → `ElMessage.error` + 重新启用按钮
    - **导入区域**：
      - ZIP 导入：`el-upload` 组件 accept=".zip"、drag 属性支持拖拽、文件大小提示
      - ZIP 上传 → `importCompositeFromZip(file)` → 成功 → 打开 ImportSuccessDialog
      - ZIP 上传 → 409 冲突 → 打开 ConflictResolutionDialog
      - ZIP 上传 → 400 格式错误 → `ElMessage.error(t('workspace.exportImport.invalidPackage'))`
      - JSON Config 导入：`el-upload` accept=".json" + `el-button` → `importConfig(file)` → 成功 → `ElMessage.success` + ImportSuccessDialog
      - 导入中 → loading overlay
    - **ConflictResolutionDialog**（内联 `el-dialog`）：
      - `el-radio-group` 三选项：Rename（append "-imported"）、Overwrite、Cancel
      - 确认 → 根据选择重试导入或取消
    - **ConfigureCredentialsDialog**（内联 `el-dialog`）：
      - 列出含 `__CREDENTIAL_PLACEHOLDER__` 的数据源，每个提供凭据输入字段
      - 提交 → 逐个调用 `PUT /api/data-sources/{id}` 更新凭据
    - **ImportSuccessDialog**（内联 `el-dialog`）：
      - 显示导入模板名称、摘要信息
      - "Go to Workspace" 按钮 → `router.push('/templates/{importedId}/workspace')`
    - `triggerDownload(blob, filename)` 辅助函数：创建临时 `<a>` 元素触发浏览器下载
    - 首次加载：`onMounted` → `ensureTestCasesLoaded()`（检查 `store.testCases` 是否已加载，未加载则调用 `store.refreshTestCases()`）
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 8.8_

- [x] 5. 前端组件 — SettingsTab.vue
  - [x] 5.1 创建 `frontend/src/views/template-workspace/components/SettingsTab.vue`
    - 从 store 读取数据，无 props
    - 使用 `el-collapse` 渲染 6 个可折叠面板：
      1. "版本历史" (`versionHistory`) → `<VersionHistoryPanel />`
      2. "版本对比" (`versionDiff`) → `<VersionDiffPanel />`
      3. "Webhook 配置" (`webhooks`) → `<WebhookPanel :template-id="store.templateId" />`
      4. "水印与安全" (`watermarkSecurity`) → `<WatermarkSecurityConfig :template-id="store.templateId" />`
      5. "定时任务" (`scheduledTasks`) → `<ScheduledTaskManagement :template-id="store.templateId" />`
      6. "权限管理" (`permissions`) → `<PermissionPanel />`
    - 默认展开 `versionHistory` 面板
    - 首次加载：`onMounted` → `loadSettingsData()`（调用 `store.refreshVersions()` + `store.refreshPermissions()`，使用 `Promise.allSettled` 并行加载，`dataLoaded` 标志避免重复加载）
    - _Requirements: 3.1, 5.1, 5.2, 5.3, 5.4_

- [x] 6. 前端组件 — VersionHistoryPanel.vue
  - [x] 6.1 创建 `frontend/src/views/template-workspace/components/VersionHistoryPanel.vue`
    - 从 store 读取 `store.versions`，无 props
    - `el-table` 展示版本列表（columns: versionNumber, createdAt, createdBy, comment, actions: Rollback 按钮）
    - 点击 Rollback → `ElMessageBox.confirm` 确认对话框（i18n key `workspace.settings.rollbackConfirm`，含 `{versionNumber}` 插值）
    - 确认回滚 → `rollingBackId = version.id` → `rollbackVersion(store.templateId, version.id)` → `Promise.all([store.refreshTemplate(), store.refreshAssemblyConfig(), store.refreshSegments(), store.refreshCoverage(), store.refreshVersions()])` → `ElMessage.success(t('workspace.settings.rollbackSuccess'))`
    - 回滚失败 → `ElMessage.error` + `rollingBackId = null`
    - 回滚中 → 当前按钮 loading + 其他 Rollback 按钮禁用（`rollingBackId !== null && rollingBackId !== row.id`）
    - _Requirements: 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_

- [x] 7. 前端组件 — VersionDiffPanel.vue
  - [x] 7.1 创建 `frontend/src/views/template-workspace/components/VersionDiffPanel.vue`
    - 从 store 读取 `store.versions`，无 props
    - 两个 `el-select` 版本选择器（versionA、versionB），从 `store.versions` 填充选项
    - "Compare" 按钮（`el-button` type="primary"）
    - `isCompareDisabled` computed：versionA === null || versionB === null || versionA === versionB → 按钮禁用
    - 点击 Compare → `getVersionDiff(store.templateId, versionA, versionB)` → 渲染 diff 结果
    - Diff 结果展示：摘要标签（added/removed/modified 各一个 `el-tag`）+ diff 详情表格（field, type tag with color mapping, oldValue, newValue）
    - `diffTypeTag(type)` 辅助函数：ADDED → success、REMOVED → danger、MODIFIED → warning、default → info
    - Compare 失败 → `ElMessage.error`
    - Compare 中 → 按钮 loading 状态
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6_

- [x] 8. 前端组件 — PermissionPanel.vue
  - [x] 8.1 创建 `frontend/src/views/template-workspace/components/PermissionPanel.vue`
    - 从 store 读取 `store.permissions`，无 props
    - toolbar 区域：`el-button` "Grant Permission" → 打开 GrantPermissionDialog
    - 空状态：`el-empty` + i18n key `workspace.settings.noPermissions`
    - `el-table` 展示权限列表（columns: granteeName, granteeType colored `el-tag`（USER=primary, TEAM=success）, permissionType `el-tag`, createdAt, actions: Revoke 按钮 type="danger"）
    - 点击 Revoke → `ElMessageBox.confirm`（i18n key `workspace.settings.revokeConfirm`）→ `revokePermission(store.templateId, permission.id)` → `store.refreshPermissions()` → `ElMessage.success`
    - 撤销失败 → `ElMessage.error`
    - 撤销中 → `revokingId` 控制按钮 loading 状态
    - GrantPermissionDialog saved 事件 → `store.refreshPermissions()` + `ElMessage.success`
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

- [x] 9. 前端组件 — GrantPermissionDialog.vue
  - [x] 9.1 创建 `frontend/src/views/template-workspace/components/GrantPermissionDialog.vue`
    - Props: `visible: boolean`、`templateId: number`
    - Emits: `update:visible`、`saved`
    - 表单字段：
      - granteeType（`el-radio-group`：USER / TEAM）
      - granteeId（`el-select` filterable：USER 时加载 `GET /api/users`，TEAM 时加载 `listTeams(tenantId)`）
      - permissionType（`el-select`：VIEW / EDIT / DELETE / CALL_API）
    - watch `form.granteeType`：切换时清空 `granteeId` + 重新加载对应列表（`loadingGrantees` 状态）
    - watch `visible`：打开时重置表单（granteeType=USER, granteeId=null, permissionType=VIEW）
    - 提交校验：未选择 grantee → `ElMessage.warning(t('workspace.settings.selectGrantee'))`
    - 提交 → `grantPermission(templateId, { granteeId, granteeType, permissionType })` → emit `saved`
    - 提交失败 → `ElMessage.error` + 对话框保持打开
    - _Requirements: 6.3, 6.4_


- [x] 10. Checkpoint — 确保前端组件无 TypeScript 错误
  - 运行 TypeScript 类型检查确认无错误
  - 确认 ExportImportTab.vue、SettingsTab.vue、VersionHistoryPanel.vue、VersionDiffPanel.vue、PermissionPanel.vue、GrantPermissionDialog.vue 文件创建完成
  - 确认 store 扩展（versions, permissions, refreshVersions, refreshPermissions）无类型错误
  - 确认所有组件间 props/emits 类型匹配

- [x] 11. Index.vue 集成 — 替换最后 2 个占位标签页
  - [x] 11.1 修改 `frontend/src/views/template-workspace/Index.vue`
    - 新增 import: `ExportImportTab` from `./components/ExportImportTab.vue`、`SettingsTab` from `./components/SettingsTab.vue`
    - 移除 `PlaceholderTab` 组件 import 和 `placeholderTabs` 数组定义（所有 7 个标签页均为真实组件）
    - 将 `<el-tabs>` 中 P4 占位标签页替换为：
      - `<el-tab-pane :label="$t('workspace.tabExportImport')" name="exportImport"><ExportImportTab /></el-tab-pane>`
      - `<el-tab-pane :label="$t('workspace.tabSettings')" name="settings"><SettingsTab /></el-tab-pane>`
    - 移除 `v-for="tab in placeholderTabs"` 的 PlaceholderTab 渲染逻辑
    - _Requirements: 8.1, 8.2, 8.3, 8.7_

- [x] 12. 国际化 — 添加 P4 新增 i18n key
  - [x] 12.1 扩展 `frontend/src/i18n/en-US.json`
    - 添加 `workspace.exportImport.*` 前缀 key（exportTitle, importTitle, exportPackage, exportConfig, importPackage, importConfig, draftExportDisabled, invalidPackage, importSuccess, goToWorkspace, conflictTitle, conflictRename, conflictOverwrite, conflictCancel, configureCredentials, segmentCount, dataSourceCount, expressionCount, testDataCount）
    - 添加 `workspace.settings.*` 前缀 key（versionHistory, versionDiff, webhooks, watermarkSecurity, scheduledTasks, permissions, rollbackConfirm, rollbackSuccess, rollback, compareVersions, versionA, versionB, grantPermission, revokeConfirm, noPermissions, granteeType, permissionType, selectGrantee）
    - _Requirements: 9.1, 9.3, 9.4, 9.5, 9.6_
  - [x] 12.2 扩展 `frontend/src/i18n/zh-CN.json`
    - 添加与 en-US 对应的所有中文简体翻译
    - _Requirements: 9.2_
  - [x] 12.3 扩展 `frontend/src/i18n/zh-TW.json`
    - 添加与 en-US 对应的所有中文繁体翻译
    - _Requirements: 9.2_

- [x] 13. Checkpoint — 确保前端编译通过、i18n 完整
  - 运行 TypeScript 类型检查确认无错误
  - 确认 en-US、zh-CN、zh-TW 三个语言文件中所有 P4 新增 key 完整
  - 确认 ExportImportTab 和 SettingsTab 已替换占位组件
  - 确认 store 扩展（versions, permissions, refreshVersions, refreshPermissions）已正确导出
  - 确认 PlaceholderTab 不再被渲染

- [x] 14. 前端测试 — Property-Based Tests
  - [x] 14.1 编写 Property Test — Export button disabled state based on template status (Property 1)
    - 创建测试文件: `frontend/src/__tests__/workspace-export-settings-property.test.ts`
    - **Property 1: Export button disabled state based on template status**
    - 使用 fast-check 从 `['DRAFT', 'PENDING_REVIEW', 'REVIEWED', 'ACTIVE', 'ARCHIVED']` 随机选择 status
    - 断言：`isExportPackageDisabled` = (status === 'DRAFT')；非 DRAFT 状态时按钮启用
    - **Validates: Requirements 1.4**
  - [x] 14.2 编写 Property Test — Export summary counts match store state (Property 2)
    - **Property 2: Export summary counts match store state**
    - 使用 fast-check 生成随机 assemblyConfig.segments 数组（长度 0-100）、随机 dataSources 数组（长度 0-50）、随机 expressions 数组（长度 0-50）、随机 testCases 数组（长度 0-200）
    - 断言：exportSummary.segmentCount = segments.length；exportSummary.dataSourceCount = dataSources.length；exportSummary.expressionCount = expressions.length；exportSummary.testDataCount = testCases.length
    - **Validates: Requirements 1.5**
  - [x] 14.3 编写 Property Test — Credential placeholder detection filters correctly (Property 3)
    - **Property 3: Credential placeholder detection filters correctly**
    - 使用 fast-check 生成随机数据源数组（长度 0-20），每个数据源的 config 随机包含或不包含 `__CREDENTIAL_PLACEHOLDER__` 值
    - 断言：过滤结果 = 仅包含 config 中含有 `__CREDENTIAL_PLACEHOLDER__` 的数据源；无 placeholder 的数据源不出现在列表中
    - **Validates: Requirements 2.5**
  - [x] 14.4 编写 Property Test — Version compare button disabled when same version selected (Property 4)
    - **Property 4: Version compare button disabled when same version selected**
    - 使用 fast-check 生成随机 versionA（null 或 1-100）、随机 versionB（null 或 1-100）
    - 断言：`isCompareDisabled` = (versionA === null || versionB === null || versionA === versionB)
    - **Validates: Requirements 4.4**
  - [x] 14.5 编写后端 Property Test — ZIP export contains all required files with credential masking (Property 5)
    - 创建测试文件: `backend/src/test/java/com/docgen/property/CompositeExportPropertyTest.java`
    - **Property 5: ZIP export contains all required files with credential masking**
    - 使用 jqwik 生成随机 DataSource 列表（0-10 个，每个含随机 configJson 包含 password/apiKey/clientSecret 字段）、随机 Expression 列表（0-10 个）、随机 TestCase 列表（0-10 个），mock 覆盖率服务
    - 断言：导出的 ZIP 包含 `config.json`、`data-sources.json`、`expressions.json`、`test-data.json`、`coverage-report.json`；`data-sources.json` 中所有 credential 字段值为 `__CREDENTIAL_PLACEHOLDER__`；非 credential 字段保持原值；entry 数量匹配
    - **Validates: Requirements 7.1, 7.2**

- [x] 15. 前端测试 — 单元测试
  - [x] 15.1 编写单元测试 — ExportImportTab.vue
    - 创建测试文件: `frontend/src/__tests__/ExportImportTab.test.ts`
    - 测试：两区域渲染（导出 + 导入）+ 分隔线、导出摘要卡片显示 4 个统计数字、DRAFT 状态 → Export Complete Package 按钮禁用 + tooltip、ACTIVE 状态 → Export Complete Package 按钮启用、点击 Export Complete Package → API 调用 → 触发下载、点击 Export Config Only → API 调用 → 触发下载、导出中 → 两个按钮均禁用 + loading、导出失败 → ElMessage.error + 按钮重新启用、ZIP 上传 → 成功 → 显示成功对话框、ZIP 上传 → 409 冲突 → 显示冲突解决对话框、ZIP 上传 → 400 格式错误 → ElMessage.error、冲突解决 Rename/Overwrite/Cancel 三种路径、凭据配置对话框提交 → 逐个更新数据源、JSON Config 上传成功/失败、首次挂载调用 store.refreshTestCases()、"Go to Workspace" 按钮导航
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 8.8_
  - [x] 15.2 编写单元测试 — SettingsTab.vue
    - 创建测试文件: `frontend/src/__tests__/SettingsTab.test.ts`
    - 测试：渲染 6 个 el-collapse-item、首次挂载调用 store.refreshVersions() + store.refreshPermissions()、WebhookPanel 嵌入并传入正确 templateId prop、WatermarkSecurityConfig 嵌入并传入正确 templateId prop、ScheduledTaskManagement 嵌入并传入正确 templateId prop
    - _Requirements: 3.1, 5.1, 5.2, 5.3, 5.4_
  - [x] 15.3 编写单元测试 — VersionHistoryPanel.vue
    - 创建测试文件: `frontend/src/__tests__/VersionHistoryPanel.test.ts`
    - 测试：版本表格列渲染（versionNumber, createdAt, createdBy, comment, actions）、点击 Rollback → 确认对话框、确认回滚 → API 调用 → store 多个 refresh → ElMessage.success、回滚失败 → ElMessage.error + 按钮重新启用、回滚中 → 当前按钮 loading + 其他按钮禁用
    - _Requirements: 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_
  - [x] 15.4 编写单元测试 — VersionDiffPanel.vue
    - 创建测试文件: `frontend/src/__tests__/VersionDiffPanel.test.ts`
    - 测试：两个版本选择器 + Compare 按钮、选择相同版本 → Compare 按钮禁用、未选择版本 → Compare 按钮禁用、点击 Compare → API 调用 → 渲染 diff 结果、diff 结果摘要（added/removed/modified 标签）、diff 表格渲染（field, type tag, oldValue, newValue）、Compare 失败 → ElMessage.error
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6_
  - [x] 15.5 编写单元测试 — PermissionPanel.vue
    - 创建测试文件: `frontend/src/__tests__/PermissionPanel.test.ts`
    - 测试：权限表格列渲染（granteeName, granteeType tag, permissionType tag, createdAt, actions）、空状态显示 noPermissions 提示、点击 Grant Permission → 打开 GrantPermissionDialog、点击 Revoke → 确认 → API 调用 → store.refreshPermissions()、撤销失败 → ElMessage.error
    - _Requirements: 6.1, 6.2, 6.3, 6.5, 6.6_
  - [x] 15.6 编写单元测试 — GrantPermissionDialog.vue
    - 创建测试文件: `frontend/src/__tests__/GrantPermissionDialog.test.ts`
    - 测试：打开时重置表单、granteeType = USER → 加载用户列表、granteeType = TEAM → 加载团队列表、切换 granteeType → 清空 granteeId + 重新加载列表、未选择 grantee → 警告提示、提交 → API 调用 → emit saved、提交失败 → ElMessage.error + 对话框保持打开
    - _Requirements: 6.3, 6.4_
  - [x] 15.7 编写单元测试 — Index.vue 集成（P4 部分）
    - 扩展测试文件: `frontend/src/__tests__/views/TemplateWorkspaceIndex.test.ts`
    - 测试：exportImport 标签页渲染 ExportImportTab（非 PlaceholderTab）、settings 标签页渲染 SettingsTab（非 PlaceholderTab）、所有 7 个标签页均为真实组件（无 PlaceholderTab 渲染）
    - _Requirements: 8.1, 8.2, 8.3_

- [x] 16. Final checkpoint — 确保所有测试通过、i18n 完整、组件集成正确
  - 运行前端测试 `npx vitest --run` 确认所有测试通过
  - 运行后端测试确认通过
  - 确认 en-US、zh-CN、zh-TW 三个语言文件中所有 P4 新增 key 完整
  - 确认 ExportImportTab 和 SettingsTab 已替换占位组件
  - 确认 store 扩展（versions, permissions, refreshVersions, refreshPermissions）已集成
  - 确认 CompositeImportExportService 扩展完成（导出含 4 个新文件 + 凭据脱敏 + 导入含扩展文件解析 + 旧格式向后兼容）
  - 确认 PlaceholderTab 不再被渲染，所有 7 个标签页均为真实组件
  - 确认 WorkflowStepIndicator 能正确反映步骤完成状态

## Notes

- 所有任务均为必需任务，无可选标记
- 后端变更范围：扩展 CompositeImportExportService（无数据库迁移，无新增 Entity/Controller）
- 前端核心工作：ExportImportTab（含 ConflictResolutionDialog + ConfigureCredentialsDialog + ImportSuccessDialog）+ SettingsTab（含 VersionHistoryPanel + VersionDiffPanel + PermissionPanel + GrantPermissionDialog + 3 个嵌入已有组件）
- versions 和 permissions 按需加载（不在 initWorkspace 中），在 SettingsTab 激活时加载
- testCases 在 ExportImportTab 激活时按需检查并加载（用于导出摘要统计）
- Property tests 覆盖设计文档中定义的 5 个正确性属性（4 个前端 fast-check + 1 个后端 jqwik）
- 前端 PBT 使用 fast-check，后端 PBT 使用 jqwik
- 每个任务引用具体需求编号以确保可追溯性
- 任务顺序遵循 P2/P3 模式：后端 → Store → 前端组件 → Index 集成 → i18n → PBT → 单元测试

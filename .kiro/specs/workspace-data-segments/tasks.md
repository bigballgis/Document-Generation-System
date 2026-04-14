# Implementation Plan: 工作台数据与片段标签页 (Workspace Data & Segments)

## Overview

Phase 2 将 P1 骨架中的 3 个占位标签页替换为真实实现，按依赖顺序编排：后端变更 → 前端 API 层 → Store 扩展 → 三个标签页组件 → Index.vue 集成 → i18n → 测试。后端仅涉及 SegmentController file 参数改为可选 + SegmentService 空 .docx 创建逻辑。前端核心工作为 DataStructureTab、SegmentArrangementTab、VisualEditorTab 三个组件的实现。

## Tasks

- [x] 1. 后端 — SegmentController file 参数改为可选 + SegmentService 空 .docx 创建
  - [x] 1.1 修改 `SegmentController.createSegment` 的 `@RequestPart("file")` 为 `@RequestPart(value = "file", required = false)`
    - 修改文件: `backend/src/main/java/com/docgen/controller/SegmentController.java`
    - 将 `createSegment` 方法的 `file` 参数注解改为 `@RequestPart(value = "file", required = false) MultipartFile file`
    - _Requirements: 7.1_
  - [x] 1.2 修改 `SegmentService.createSegment` 支持 file=null 时创建空 .docx
    - 修改文件: `backend/src/main/java/com/docgen/service/SegmentService.java`
    - 在 `createSegment` 方法中判断 `file != null && !file.isEmpty()` 时走现有上传逻辑，否则调用新方法 `createEmptyDocxSegment(tenantId, request.getName())`
    - 新增 `createEmptyDocxSegment(Long tenantId, String segmentName)` 私有方法：生成空 .docx ZIP（含 `[Content_Types].xml`、`_rels/.rels`、`word/document.xml`），上传到 MinIO 路径 `segments/{tenantId}/{uuid}_{sanitizedName}.docx`
    - 新增 `generateEmptyDocx()` 私有方法：构建最小 Open XML ZIP 结构
    - 新增 `addZipEntry(ZipOutputStream, String, String)` 辅助方法
    - 失败时抛出 `BusinessException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR)`
    - _Requirements: 7.2, 7.3, 7.4_
  - [x] 1.3 编写后端单元测试 — SegmentService 空 .docx 创建
    - 创建或扩展测试文件: `backend/src/test/java/com/docgen/service/SegmentServiceTest.java`
    - 测试 file=null 时调用 `createEmptyDocxSegment`
    - 测试 `generateEmptyDocx()` 生成的 byte[] 是有效 ZIP 且包含 3 个 entry
    - 测试 MinIO 上传失败时抛出 BusinessException
    - _Requirements: 7.2, 7.4_

- [x] 2. Checkpoint — 确保后端编译通过、单元测试通过
  - 运行 `mvn compile` 确认无编译错误
  - 运行后端单元测试确认通过
  - 确认 SegmentController file 参数已改为 optional

- [x] 3. 前端 API 层 — segments.ts createSegment file 参数改为可选
  - [x] 3.1 修改 `frontend/src/api/segments.ts` 的 `createSegment` 函数
    - 将 `file: File` 参数改为 `file?: File`
    - 当 `file` 存在时 `formData.append('file', file)`，否则不添加 file part
    - _Requirements: 7.5_

- [x] 4. Store 扩展 — 新增 segments 状态与 refreshSegments action
  - [x] 4.1 修改 `frontend/src/stores/templateWorkspace.ts`
    - 新增 import: `import { getCompositeSegments } from '@/api/composite-templates'` 和 `import type { Segment } from '@/types/segment'`
    - 新增 state: `const segments = ref<Segment[]>([])`
    - 在 `initWorkspace` 的 `nonCriticalResults` 中新增第 5 个请求 `getCompositeSegments(id)`
    - 在 `sections` 数组中新增 `'segments'`，在 settled 处理中新增 `case 4: segments.value = result.value as Segment[]`
    - 新增 `refreshSegments()` action：调用 `getCompositeSegments(templateId.value)`，成功时更新 `segments.value` 并删除 `warnings.value.segments`，失败时设置 `warnings.value.segments`
    - 在 `$reset` 中新增 `segments.value = []`
    - 在 return 中导出 `segments, refreshSegments`
    - _Requirements: 8.6, 8.7_

- [x] 5. 前端组件 — DataStructureTab.vue
  - [x] 5.1 创建 `frontend/src/views/template-workspace/components/DataStructureTab.vue`
    - 从 store 读取 `store.dataSources` 和 `store.expressions`，不发起额外 API 调用
    - 数据源区域：section header（标题 + "添加数据源"按钮）+ `el-table`（columns: name, type el-tag, priority, cacheEnabled icon, updatedAt, actions: Edit/Test/Delete）+ 空状态 `el-empty`
    - 表达式区域：section header（标题 + "添加表达式"按钮）+ `el-table`（columns: name, expressionType el-tag, expressionText truncated+tooltip, executionOrder, createdAt, actions: Edit/Validate/Delete）+ 空状态 `el-empty`
    - 两区域之间使用 `el-divider` 分隔
    - 数据源操作：添加/编辑 → 打开 DataSourceFormDialog（传入 templateId 和 dataSource prop）；saved 事件 → `store.refreshDataSources()`；测试连接 → `testConnection(id)` → ElMessage；删除 → ElMessageBox.confirm → 禁用按钮 → `deleteDataSource(id)` → `store.refreshDataSources()` → 启用按钮；失败 → ElMessage.error + 重新启用按钮
    - 表达式操作：添加/编辑 → 打开 ExpressionFormDialog（传入 templateId 和 data prop）；saved 事件 → `store.refreshExpressions()`；验证 → `validateExpression({ expression, expressionType })` → ElMessage；删除 → ElMessageBox.confirm → `deleteExpression(id)` → `store.refreshExpressions()`；失败 → ElMessage.error + 重新启用按钮
    - 使用 `deletingIds: Set<number>` 防止重复点击删除
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7_

- [x] 6. 前端组件 — SegmentArrangementTab.vue
  - [x] 6.1 创建 `frontend/src/views/template-workspace/components/SegmentArrangementTab.vue`
    - 复用 `useAssemblyConfig` composable 管理本地编排状态
    - 复用 `useSegmentDrag` composable 实现拖拽排序
    - onMounted 时从 store.assemblyConfig 反序列化初始化
    - 合并 assemblyConfig entries 与 store.segments 详情（MergedSegmentEntry 内部类型）
    - 片段列表渲染：draggable card（position, name, segmentType tag, enabled indicator, Expand Config / Remove 按钮）
    - 拖拽排序：HTML5 drag events → useSegmentDrag.onDrop → useAssemblyConfig.setSegments
    - 键盘快捷键：Alt+↑/↓ → moveUp/moveDown，Ctrl+Z → undo，Ctrl+Shift+Z → redo
    - 保存编排：PUT assembly-config → refreshAssemblyConfig + refreshSegments + refreshCoverage → deserialize → updateSavedSnapshot → ElMessage.success
    - 保存失败：ElMessage.error + 保留本地状态
    - 未保存变更检测：lastSavedSnapshot + hasUnsavedChanges computed + `defineExpose({ hasUnsavedChanges })`（供 Index.vue 通过 ref 访问）
    - 空状态：el-empty + workspace.segment.empty
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8_
  - [x] 6.2 实现新建片段功能（SegmentArrangementTab 内联表单）
    - 新建片段表单：name（required, max 200）、segmentType（select COVER/TOC/CHAPTER/TABLE/SIGNATURE/LEGAL/APPENDIX）、description（optional）、file upload（optional，提示无文件时创建空模板）
    - 提交 → `createSegment(data, file?)` → addSegment 到本地 → 保存 assembly config → refreshAssemblyConfig + refreshSegments → deserialize
    - 片段创建成功但编排保存失败 → ElMessage.warning（workspace.segment.createdButNotAdded）
    - _Requirements: 4.1, 4.2, 4.3_
  - [x] 6.3 实现添加已有片段功能（SegmentArrangementTab 搜索对话框）
    - 搜索面板：keyword 输入 + segmentType 筛选 + 分页（size=20）
    - 调用 `getSegments(query)` 获取结果，客户端过滤已在 assemblyConfig 中的片段（existingSegmentIds computed）
    - 多选 checkbox → 确认 → 逐个 addSegment → handleSave
    - _Requirements: 4.4, 4.5_
  - [x] 6.4 实现片段内联配置与移除功能
    - 展开配置面板：enabled el-switch、pageBreakBefore el-switch、lockedVersion el-input-number + "Use Latest" checkbox（checked → null）、conditionExpression el-input、dataScope KeyValueEditor
    - 修改配置 → updateSegment(index, patch)，仅本地更新，保存时统一提交
    - 移除片段 → ElMessageBox.confirm → removeSegment(index) → 标记未保存
    - _Requirements: 5.1, 5.2, 5.3_

- [x] 7. 前端组件 — VisualEditorTab.vue
  - [x] 7.1 创建 `frontend/src/views/template-workspace/components/VisualEditorTab.vue`
    - 从 store 读取 assemblyConfig + segments，合并为 mergedSegments（同 SegmentArrangementTab 的 merge 逻辑）
    - 片段列表：el-table（columns: position, name, segmentType tag, updatedAt, lock status icon, "Open Editor" 按钮）
    - 锁状态检查：组件需 expose `checkLocks()` 方法供 Index.vue 在标签页激活时调用（通过 `watch(activeTab)` 触发），或接受 `active: boolean` prop 并 watch 变化时自动检查；调用 `Promise.allSettled([getSegmentLockInfo(id) for each segment])`，失败视为未锁定
    - 锁定中的片段显示锁图标 + lockedByUsername（workspace.editor.lockedBy）
    - Open Editor → `window.open('/segments/{segmentId}/editor', '_blank')`
    - Preview Composite 按钮 → `previewCompositeTemplate(templateId)` → `window.open(previewUrl, '_blank')`；失败 → ElMessage.error
    - Selective Preview：selectiveMode toggle → checkbox 选择片段 → `previewSelectiveSegments(templateId, { segmentIds })` → window.open；无选择时按钮禁用
    - 空状态：el-empty + workspace.editor.empty + 跳转到片段编排标签页的链接
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7_

- [x] 8. Checkpoint — 确保前端三个组件无 TypeScript 错误
  - 运行 TypeScript 类型检查确认无错误
  - 确认 DataStructureTab、SegmentArrangementTab、VisualEditorTab 三个组件文件创建完成
  - 确认 store 扩展和 API 层变更无类型错误

- [x] 9. Index.vue 集成 — 替换占位标签页 + 未保存变更守卫
  - [x] 9.1 修改 `frontend/src/views/template-workspace/Index.vue`
    - 新增 import: DataStructureTab、SegmentArrangementTab、VisualEditorTab
    - 新增 import: `onBeforeRouteLeave` from vue-router
    - 将 placeholderTabs 数组中前 3 项（dataStructure、segments、editor）从 PlaceholderTab 改为真实组件渲染
    - 保留后 4 个标签页（testing、reviewPublish、exportImport、settings）为 PlaceholderTab
    - 新增 `segmentArrangementRef` 模板引用
    - 实现 `handleTabChange(newTab)` 方法：当从 segments 标签页切换且 hasUnsavedChanges 时弹出 ElMessageBox.confirm
    - 实现 `onBeforeRouteLeave` 守卫：当 hasUnsavedChanges 时弹出确认对话框
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 3.8_

- [x] 10. 国际化 — 添加 P2 新增 i18n key
  - [x] 10.1 扩展 `frontend/src/i18n/en-US.json`
    - 添加 `workspace.dataSource.*` 前缀 key（title, add, empty, testSuccess, testFailed）
    - 添加 `workspace.expression.*` 前缀 key（title, add, empty）
    - 添加 `workspace.segment.*` 前缀 key（title, save, empty, addSegment, createNew, addExisting, unsavedConfirm, createdButNotAdded, useLatest, lockedVersion, conditionExpr, dataScope, pageBreakBefore）
    - 添加 `workspace.editor.*` 前缀 key（title, openEditor, previewComposite, selectivePreview, empty, previewFailed, lockedBy）
    - 添加 `workspace.warning.segments` key
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6_
  - [x] 10.2 扩展 `frontend/src/i18n/zh-CN.json`
    - 添加与 en-US 对应的所有中文简体翻译
    - _Requirements: 9.2_
  - [x] 10.3 扩展 `frontend/src/i18n/zh-TW.json`
    - 添加与 en-US 对应的所有中文繁体翻译
    - _Requirements: 9.2_

- [x] 11. 前端测试 — Property-Based Tests
  - [x] 11.1 编写 Property Test — Segment merge 正确性 (Property 1)
    - 创建测试文件: `frontend/src/__tests__/workspace-segments-property.test.ts`
    - **Property 1: Segment merge produces correct display names**
    - 使用 fast-check 生成随机 AssemblySegmentEntry 数组（长度 0-30）和随机 Segment 数组（长度 0-50）
    - 断言：合并结果长度 = assembly config 长度；匹配的 entry 显示正确 name；不匹配的显示 "Unknown Segment #id"
    - **Validates: Requirements 3.1, 6.1**
  - [x] 11.2 编写 Property Test — Undo/redo round-trip (Property 2)
    - **Property 2: Undo/redo round-trip restores previous state**
    - 使用 fast-check 生成随机初始 AssemblySegmentEntry 数组和随机操作
    - 断言：操作后 undo 恢复原状态；undo 后 redo 恢复操作后状态
    - **Validates: Requirements 3.6**
  - [x] 11.3 编写 Property Test — Unsaved changes detection (Property 3)
    - **Property 3: Unsaved changes detection is consistent**
    - 使用 fast-check 生成随机初始数组和修改操作序列
    - 断言：deserialize 后 hasUnsavedChanges = false；修改后 = true；再次 deserialize 后 = false
    - **Validates: Requirements 3.8**
  - [x] 11.4 编写 Property Test — Add Existing Segment 过滤 (Property 4)
    - **Property 4: Add Existing Segment filter excludes already-present segments**
    - 使用 fast-check 生成随机 segmentId 集合 S₁ 和随机 Segment 数组 S₂
    - 断言：过滤结果 = S₂ 中 id 不在 S₁ 中的子集
    - **Validates: Requirements 4.4**
  - [x] 11.5 编写后端 Property Test — Empty .docx 生成有效性 (Property 5)
    - 创建测试文件: `backend/src/test/java/com/docgen/property/SegmentEmptyDocxPropertyTest.java`
    - **Property 5: Empty .docx generation produces valid Open XML structure**
    - 使用 jqwik 生成随机 Unicode 字符串作为 segment name
    - 断言：生成的 byte[] 是有效 ZIP；包含 3 个 entry；每个 entry 为合法 XML；文件路径格式正确
    - **Validates: Requirements 7.2**

- [x] 12. 前端测试 — 单元测试
  - [x] 12.1 编写单元测试 — DataStructureTab.vue
    - 创建测试文件: `frontend/src/__tests__/DataStructureTab.test.ts`
    - 测试：两区域渲染 + 分隔线、从 store 读取数据无额外 API 调用、数据源/表达式表格列渲染、空状态卡片、添加/编辑对话框打开、saved 事件刷新 store、删除确认流程、删除失败恢复、测试连接结果显示
    - _Requirements: 1.1–1.10, 2.1–2.7_
  - [x] 12.2 编写单元测试 — SegmentArrangementTab.vue
    - 创建测试文件: `frontend/src/__tests__/SegmentArrangementTab.test.ts`
    - 测试：store 数据合并渲染、拖拽排序、保存编排流程、保存失败处理、键盘快捷键、undo/redo、空状态、未保存变更指示器、展开配置面板、新建片段、添加已有片段、移除片段
    - _Requirements: 3.1–3.8, 4.1–4.5, 5.1–5.3_
  - [x] 12.3 编写单元测试 — VisualEditorTab.vue
    - 创建测试文件: `frontend/src/__tests__/VisualEditorTab.test.ts`
    - 测试：store 数据合并渲染、锁状态检查（Promise.allSettled）、锁查询失败视为未锁定、Open Editor window.open、Preview Composite API + window.open、Selective Preview 选择与禁用、空状态 + 跳转链接
    - _Requirements: 6.1–6.7_
  - [x] 12.4 编写单元测试 — Index.vue 集成
    - 扩展测试文件: `frontend/src/__tests__/TemplateWorkspace.test.ts`
    - 测试：前 3 个标签页渲染真实组件、后 4 个标签页仍为 PlaceholderTab、Tab 切换未保存变更确认、onBeforeRouteLeave 守卫
    - _Requirements: 8.1–8.5, 3.8_

- [x] 13. Final checkpoint — 确保所有测试通过、i18n 完整、组件集成正确
  - 运行前端测试 `npx vitest --run` 确认所有测试通过
  - 运行后端测试确认通过
  - 确认 en-US、zh-CN、zh-TW 三个语言文件中所有 P2 新增 key 完整
  - 确认 DataStructureTab、SegmentArrangementTab、VisualEditorTab 已替换占位组件
  - 确认 store.segments + refreshSegments 已集成到 initWorkspace
  - 确认未保存变更守卫在 Tab 切换和路由离开时生效

## Notes

- 所有任务均为必需任务，无可选标记
- 后端变更范围小（仅 SegmentController + SegmentService），前端为主要工作量
- 三个标签页组件相互独立，但共享 store 状态
- Property tests 覆盖设计文档中定义的 5 个正确性属性
- 前端 PBT 使用 fast-check，后端 PBT 使用 jqwik
- 每个任务引用具体需求编号以确保可追溯性

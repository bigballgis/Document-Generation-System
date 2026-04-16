# Implementation Plan: 模板工作流阶段化 — Frontend Cleanup

## Overview

清理旧组件和代码：移除已被阶段组件替代的旧 Tab 组件和 composable，扩展 i18n 三语言文件覆盖所有新增文本，更新相关测试。

## Tasks

- [x] 1. 移除旧组件和 Composable
  - [x] 1.1 从 `Index.vue` 中移除所有旧 Tab 组件的 import 语句
    - 移除: `ParameterTableTab`、`SegmentArrangementTab`、`VisualEditorTab`、`TestingTab`、`ReviewPublishTab`、`ExportImportTab`、`SettingsTab`、`WorkflowStepIndicator` 的 import
    - 移除: `useWorkflowSteps` composable 的 import
    - 移除: `PlaceholderTab` 的 import（如存在）
    - 确认 Index.vue 中无残留的旧组件引用
    - _Requirements: 17.1, 17.2, 17.3, 17.4, 17.5_
  - [x] 1.2 删除旧组件文件
    - 删除: `frontend/src/views/template-workspace/components/WorkflowStepIndicator.vue`
    - 删除: `frontend/src/views/template-workspace/components/ExportImportTab.vue`
    - 删除: `frontend/src/views/template-workspace/components/PlaceholderTab.vue`（如存在）
    - 注意：`ParameterTableTab.vue`、`SegmentArrangementTab.vue`、`VisualEditorTab.vue`、`TestingTab.vue`、`ReviewPublishTab.vue`、`SettingsTab.vue` 的核心功能已被阶段组件复用，但文件本身可能仍被阶段子组件引用，需逐一检查后决定是否删除或保留
    - _Requirements: 17.1, 17.2, 17.3, 17.4_
  - [x] 1.3 删除或标记废弃旧 Composable
    - 删除 `frontend/src/composables/useWorkflowSteps.ts`（已被 `useStageAvailability` 替代）
    - _Requirements: 1.6_
  - [x] 1.4 清理 `frontend/src/types/workspace.ts` 中的废弃类型
    - 移除 `@deprecated` 标记的 `StepKey`、`TabName`、`PlaceholderTabConfig` 类型
    - 保留 `WorkflowStep` 接口仅当其他文件仍引用（用 grepSearch 确认）
    - _Requirements: 1.6, 17.6_

- [x] 2. 扩展 i18n 三语言文件
  - [x] 2.1 在三语言文件中新增 `workspace.stage.*` 阶段名称
    - `workspace.stage.design`: "设计" / "Design" / "設計"
    - `workspace.stage.test`: "测试" / "Test" / "測試"
    - `workspace.stage.approval`: "审批" / "Approval" / "審批"
    - `workspace.stage.publish`: "发布" / "Publish" / "發布"
    - `workspace.stage.notAvailable`: "完成前置阶段后解锁" / "Complete previous stages to unlock" / "完成前置階段後解鎖"
    - `workspace.stage.readonly`: "只读模式" / "Read-only mode" / "唯讀模式"
    - _Requirements: 16.1, 16.3_
  - [x] 2.2 在三语言文件中新增覆盖率条、审批时间线、发布摘要的 i18n key
    - `workspace.coverageBar.branch`: "分支覆盖率" / "Branch Coverage" / "分支覆蓋率"
    - `workspace.coverageBar.loop`: "循环覆盖率" / "Loop Coverage" / "循環覆蓋率"
    - `workspace.coverageBar.param`: "参数覆盖率" / "Parameter Coverage" / "參數覆蓋率"
    - `workspace.approval.timeline.approved`: "通过" / "Approved" / "通過"
    - `workspace.approval.timeline.rejected`: "拒绝" / "Rejected" / "拒絕"
    - `workspace.approval.timeline.conditional`: "有条件通过" / "Conditionally Approved" / "有條件通過"
    - `workspace.approval.timeline.pending`: "待审核" / "Pending" / "待審核"
    - `workspace.approval.submitReview`: "提交审核" / "Submit for Review" / "提交審核"
    - `workspace.approval.returnToEdit`: "返回修改" / "Return to Edit" / "返回修改"
    - `workspace.publish.summary.version`: "版本" / "Version" / "版本"
    - `workspace.publish.summary.params`: "参数数量" / "Parameters" / "參數數量"
    - `workspace.publish.summary.coverage`: "覆盖率" / "Coverage" / "覆蓋率"
    - `workspace.publish.summary.reviewStatus`: "审核状态" / "Review Status" / "審核狀態"
    - `workspace.publish.activate`: "激活" / "Activate" / "啟用"
    - `workspace.publish.exportZip`: "导出 ZIP 包" / "Export ZIP" / "匯出 ZIP 包"
    - `workspace.publish.newVersion`: "编辑为新版本" / "Edit as New Version" / "編輯為新版本"
    - `workspace.testForm.viewJson`: "查看 JSON" / "View JSON" / "查看 JSON"
    - `workspace.testForm.viewForm`: "表单模式" / "Form Mode" / "表單模式"
    - _Requirements: 16.2_
  - [x] 2.3 在三语言文件中新增 API 管理页面的 i18n key
    - `workspace.api.title`: "API 管理" / "API Management" / "API 管理"
    - `workspace.api.versions`: "版本列表" / "Version List" / "版本列表"
    - `workspace.api.endpoint`: "端点信息" / "Endpoint Info" / "端點資訊"
    - `workspace.api.keys`: "API Key 管理" / "API Key Management" / "API Key 管理"
    - `workspace.api.stats`: "调用统计" / "Call Statistics" / "呼叫統計"
    - _Requirements: 16.4_
  - [x] 2.4 移除三语言文件中已废弃的旧 workspace key
    - 移除 `workspace.step1` ~ `workspace.step8`（旧八步指示器）
    - 移除 `workspace.tabDataStructure`、`workspace.tabSegments`、`workspace.tabEditor`、`workspace.tabTesting`、`workspace.tabReviewPublish`、`workspace.tabExportImport`、`workspace.tabSettings`（旧 Tab 标签）
    - 移除 `workspace.placeholder.*`（旧占位符文本）
    - _Requirements: 17.1, 17.2, 17.3, 17.4, 17.5_

- [x] 3. Checkpoint — 清理后编译验证
  - 确保 `npx vue-tsc --noEmit` 类型检查通过
  - 确保无残留的旧组件引用（grepSearch 验证）
  - ask the user if questions arise.

- [x] 4. 更新测试
  - [x] 4.1 移除或更新引用旧组件的测试文件
    - 检查 `frontend/src/__tests__/` 中是否有引用 `WorkflowStepIndicator`、`ExportImportTab`、`useWorkflowSteps` 的测试
    - 移除或更新这些测试以适配新架构
    - _Requirements: 1.6, 17.1_
  - [x] 4.2 验证所有现有测试无回归
    - 运行 `npx vitest --run` 确认全部通过
    - 修复因组件移除导致的测试失败
    - _Requirements: 全部_

- [x] 5. Final checkpoint — 全部清理完成
  - 确保 `npx vue-tsc --noEmit` 类型检查通过
  - 确保 `npx vitest --run` 所有测试通过
  - ask the user if questions arise.

## Notes

- 依赖 frontend-stages 子 Spec 完成后执行（阶段组件必须先就绪，才能安全移除旧组件）
- 移除旧组件前必须用 grepSearch 确认无其他文件引用
- i18n key 的移除需同步更新三个语言文件（en-US.json、zh-CN.json、zh-TW.json）

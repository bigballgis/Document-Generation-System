# 实施计划：前端核心层 — 类型 + Composables + API + i18n

## 概述

实现前端核心基础设施：TypeScript 类型扩展、新增 composables（useDesignStep、useCanvasNodes）、API 调用层扩展、i18n 三语言新增。这些是后续三个前端子 spec 的共享依赖。

## Tasks

- [x] 1. 扩展前端 TypeScript 类型
  - [x] 1.1 扩展 `frontend/src/types/segment.ts`
    - 新增 `PageNumberFormat` 类型：`'ARABIC' | 'ROMAN' | 'ALPHA'`
    - 扩展 `AssemblySegmentEntry` 接口，新增 4 个可选字段：`headerFilePath`、`footerFilePath`、`pageNumberFormat`、`pageNumberStart`
    - _Requirements: 4.6, 4.7, 4.8_
  - [x] 1.2 扩展 `frontend/src/types/workspace.ts`
    - 新增 `DesignStepName` 类型：`'parameter-table' | 'segment-canvas' | 'segment-detail'`
    - 新增 `ParameterBreadcrumbItem` 接口：`{ id: number | null, name: string, tableType: 'main' | 'sub' | 'related' }`
    - _Requirements: 1.1, 2.4_

- [x] 2. 实现 useDesignStep composable
  - [x] 2.1 创建 `frontend/src/composables/useDesignStep.ts`
    - 管理 `currentStep` 状态（DesignStepName）
    - 实现步骤完成状态计算（stepStatuses）：参数表完成 = 参数数量 ≥ 1，片段编排完成 = 片段数量 ≥ 1，片段详细设计完成 = 所有已启用片段 filePath 非空
    - 实现各步骤编辑状态缓存（stepStates）：面包屑路径、选中片段索引
    - 实现 goToStep / goNext / goPrev 方法
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

- [x] 3. 实现 useCanvasNodes composable
  - [x] 3.1 创建 `frontend/src/composables/useCanvasNodes.ts`
    - 定义 `CanvasNode` 接口：`{ id, type, segmentIndex?, headerFilePath?, footerFilePath?, pageNumberFormat?, pageNumberStart? }`
    - 实现 `fromSegments(segments)` — 从 AssemblySegmentEntry[] 反序列化为 CanvasNode[]（根据扩展字段还原控制节点）
    - 实现 `toSegments(nodes)` — 将 CanvasNode[] 序列化回 AssemblySegmentEntry[]（控制节点属性写入后方相邻内容片段）
    - 实现 `getAffectedRange(nodeIndex)` — 计算控制节点影响范围（从当前节点到下一个同类节点）
    - 实现控制节点插入/删除时的属性传播逻辑（pageBreakBefore、headerFilePath、footerFilePath）
    - _Requirements: 4.5, 4.6, 4.7, 4.8, 4.15, 4.16_

- [x] 4. 扩展前端 API 调用层
  - [x] 4.1 在 `frontend/src/api/composite-templates.ts` 中新增 3 个 API 函数
    - `createBlankSegment(templateId, name, segmentType?)` → POST `/composite-templates/{id}/create-blank-segment`
    - `createBlankHeaderFooter(templateId, type)` → POST `/composite-templates/{id}/create-blank-header-footer`
    - `getSegmentOnlyOfficeUrl(templateId, segmentIndex)` → GET `/composite-templates/{id}/segments/{segmentIndex}/onlyoffice-url`
    - _Requirements: 4.4, 4.6, 4.7, 5.1_

- [x] 5. 新增 i18n 三语言翻译
  - [x] 5.1 在 `frontend/src/i18n/zh-CN.json` 的 `workspace.design` 下新增所有 i18n key
    - 包含：step.*、table.*、canvas.*、sidebar.*、overview.*、isolation.*、segmentType.*、prev、next、parameterOverview
    - 按 design.md 中定义的 i18n Key 设计完整添加
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6_
  - [x] 5.2 在 `frontend/src/i18n/en-US.json` 的 `workspace.design` 下新增对应英文翻译
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6_
  - [x] 5.3 在 `frontend/src/i18n/zh-TW.json` 的 `workspace.design` 下新增对应繁体中文翻译
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6_

- [x] 6. Checkpoint — 确保前端编译通过
  - 执行 `npx vue-tsc --noEmit` 确认类型检查通过
  - 确保所有 tests pass，ask the user if questions arise.

## Notes

- useDesignStep 和 useCanvasNodes 是纯逻辑 composable，不依赖 DOM
- i18n key 命名遵循 `workspace.design.step.{stepName}` 规范
- API 函数遵循现有 `composite-templates.ts` 的风格（使用 request 实例）

# 实施计划：前端 Step 2 — 片段编排画布

## 概述

实现设计阶段第二步"片段编排"视图：SegmentCanvas 主视图、ComponentPanel 组件面板、CanvasArea 画布区域、ControlNodeEditor 迷你编辑器。支持拖拽创建片段、控制节点管理、画布排序。

## Tasks

- [x] 1. 实现 ComponentPanel 组件面板
  - [x] 1.1 创建 `frontend/src/views/template-workspace/components/ComponentPanel.vue`
    - 显示两组可拖拽组件，以视觉分隔线区分
    - 内容片段类型：COVER、TOC、CHAPTER、TABLE、SIGNATURE、LEGAL、APPENDIX（使用 i18n key `workspace.design.segmentType.*`）
    - 控制节点：分页符、页眉、页脚、页码规则（使用 i18n key `workspace.design.canvas.*`）
    - 每个组件项支持 HTML5 drag 或 sortablejs 的 pull/clone 模式
    - _Requirements: 4.2, 10.2, 10.3_

- [x] 2. 实现 CanvasArea 画布区域
  - [x] 2.1 创建 `frontend/src/views/template-workspace/components/CanvasArea.vue`
    - 渲染 CanvasNode[] 混合列表（内容片段 + 控制节点）
    - Content_Segment_Card：白色背景 + 实线边框 + 左侧彩色类型条，高度较大
    - Control_Node_Card：浅灰背景 + 虚线边框 + 小图标，高度约内容片段的 1/3
    - 空状态：显示 i18n key `workspace.design.canvas.emptyHint`
    - _Requirements: 4.3, 4.14_
  - [x] 2.2 实现拖拽接收逻辑（从 ComponentPanel 拖入）
    - 接收内容片段类型 → 在放置位置创建 Content_Segment_Card，弹出命名输入框
    - 命名校验：空名称显示错误提示，重复名称显示错误提示
    - 确认后调用 `createBlankSegment` API 创建空白 .docx，将返回的 entry 添加到 useAssemblyConfig
    - 接收控制节点 → 在放置位置插入对应 Control_Node_Card
    - _Requirements: 4.4, 4.5, 4.6, 4.7, 4.8_
  - [x] 2.3 实现画布内拖拽排序
    - 使用 sortablejs 实现片段卡片和控制节点的拖拽排序
    - 统一拖拽视觉反馈：drag-source-active + drop-indicator 蓝色插入线
    - 排序后通过 useCanvasNodes.toSegments 更新 segments 数组
    - _Requirements: 4.10, 8.1, 8.3, 8.5_
  - [x] 2.4 实现控制节点影响范围标记
    - 受 Header_Node 影响的片段右上角显示页眉小图标
    - 受 Footer_Node 影响的片段右下角显示页脚小图标
    - 受 Page_Number_Node 影响的片段显示页码格式标记
    - 影响范围通过 useCanvasNodes.getAffectedRange 计算
    - _Requirements: 4.16_
  - [x] 2.5 实现内容片段卡片配置面板
    - 点击 Content_Segment_Card → 展开配置面板
    - 显示：片段名称编辑、启用/禁用开关、条件表达式输入、数据作用域配置
    - _Requirements: 4.11_
  - [x] 2.6 实现控制节点删除逻辑
    - 点击 Control_Node_Card 删除图标 → 移除节点
    - 删除 Page_Break_Node → 恢复后方片段 pageBreakBefore 为 false
    - 删除 Header_Node/Footer_Node → 后续片段恢复为上一个同类控制节点的设置
    - _Requirements: 4.15_
  - [x] 2.7 实现 Page_Number_Node 配置
    - 提供页码格式选择（ARABIC/ROMAN/ALPHA）和起始值设置
    - 可选"重新从 1 开始"
    - _Requirements: 4.8_

- [x] 3. 实现 ControlNodeEditor 迷你编辑器
  - [x] 3.1 创建 `frontend/src/views/template-workspace/components/ControlNodeEditor.vue`
    - Props: `visible`、`nodeType`('header'|'footer')、`filePath`、`templateId`、`readonly`
    - 使用 el-dialog 包裹 OnlyOfficeEditor 组件
    - 编辑器上方显示内容隔离提示条（header → `workspace.design.isolation.headerHint`，footer → `workspace.design.isolation.footerHint`）
    - callbackUrl 指向片段级回调端点，附带 contentType 查询参数
    - _Requirements: 4.9, 6.2, 6.3_

- [x] 4. 实现 SegmentCanvas 主视图组件
  - [x] 4.1 创建 `frontend/src/views/template-workspace/components/SegmentCanvas.vue`
    - Props: `readonly: boolean`
    - 左右分栏布局：ComponentPanel（约 25%）+ CanvasArea（约 75%）
    - 画布顶部显示"保存"按钮（调用 `updateAssemblyConfig` API）和撤销/重做按钮（复用 useAssemblyConfig undo/redo）
    - 集成 useAssemblyConfig + useCanvasNodes + useSegmentDrag
    - _Requirements: 4.1, 4.12, 4.13_
  - [x] 4.2 实现只读模式
    - readonly 为 true 时禁用拖拽、隐藏保存/删除按钮
    - _Requirements: 4.17_

- [x] 5. Checkpoint — 确保片段编排组件编译通过
  - 确保所有 tests pass，ask the user if questions arise.

## Notes

- CanvasArea 中内容片段和控制节点使用不同的 CSS 类实现视觉区分
- 控制节点不创建 AssemblySegmentEntry，而是通过 useCanvasNodes 管理虚拟节点
- Page_Break_Node 通过修改后方片段的 pageBreakBefore 属性实现
- Header_Node/Footer_Node 点击"编辑"按钮打开 ControlNodeEditor

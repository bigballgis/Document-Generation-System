# 实施计划：属性测试 + 单元测试

## 概述

为设计阶段重构编写属性测试（PBT）和单元测试。前端使用 Vitest + fast-check，后端使用 JUnit 5 + jqwik。每个 Property 对应 design.md 中定义的正确性属性。

## Tasks

- [x] 1. 前端属性测试 — useDesignStep
  - [x] 1.1 编写 Property 1: 步骤完成状态计算正确性
    - 文件：`frontend/src/__tests__/composables/useDesignStep.property.test.ts`
    - **Property 1: 步骤完成状态计算正确性**
    - **Validates: Requirements 1.3**
    - 使用 fast-check 生成任意参数数量、片段数量、已编辑片段标志组合
    - 验证：参数表完成 ⟺ 参数数量 ≥ 1，片段编排完成 ⟺ 片段数量 ≥ 1，片段详细设计完成 ⟺ 所有已启用片段 filePath 非空
  - [x] 1.2 编写 Property 3: 面包屑深度等于参数树深度
    - 文件：`frontend/src/__tests__/composables/useDesignStep.property.test.ts`
    - **Property 3: 面包屑深度等于参数树深度**
    - **Validates: Requirements 2.4**
    - 使用 fast-check 生成任意参数树结构和导航路径
    - 验证：面包屑项数量等于当前参数在树中的深度

- [x] 2. 前端属性测试 — ParameterTableView
  - [x] 2.1 编写 Property 2: 参数类型到视图类型的映射同构
    - 文件：`frontend/src/__tests__/components/ParameterTableView.property.test.ts`
    - **Property 2: 参数类型到视图类型的映射同构**
    - **Validates: Requirements 2.1**
    - 使用 fast-check 生成任意 ParameterDTO 列表（随机 DataType）
    - 验证：STRING/NUMBER/DATE/BOOLEAN → 字段行，ARRAY → 子表链接，OBJECT → 关联表链接

- [x] 3. 前端属性测试 — useCanvasNodes
  - [x] 3.1 编写 Property 5: 控制节点属性传播正确性
    - 文件：`frontend/src/__tests__/composables/useCanvasNodes.property.test.ts`
    - **Property 5: 控制节点属性传播正确性**
    - **Validates: Requirements 4.5, 4.15**
    - 使用 fast-check 生成任意画布状态（内容片段 + 控制节点混合列表）
    - 验证：插入分页符 → 后方片段 pageBreakBefore 为 true，删除 → 恢复为 false
  - [x] 3.2 编写 Property 6: 控制节点影响范围计算
    - 文件：`frontend/src/__tests__/composables/useCanvasNodes.property.test.ts`
    - **Property 6: 控制节点影响范围计算**
    - **Validates: Requirements 4.16**
    - 使用 fast-check 生成包含多个同类控制节点的画布
    - 验证：每个控制节点影响范围从当前位置到下一个同类节点位置

- [x] 4. 前端属性测试 — 排序索引连续性
  - [x] 4.1 编写 Property 4: 拖拽排序后索引连续性
    - 文件：`frontend/src/__tests__/composables/useSegmentDrag.property.test.ts`
    - **Property 4: 拖拽排序后索引连续性**
    - **Validates: Requirements 2.9, 4.10**
    - 使用 fast-check 生成任意长度列表和合法的 reorder 操作
    - 验证：排序后 position 值为从 0 开始的连续整数序列

- [x] 5. 前端属性测试 — ParameterSidebar
  - [x] 5.1 编写 Property 7: 参数标签插入文本正确性
    - 文件：`frontend/src/__tests__/components/ParameterSidebar.property.test.ts`
    - **Property 7: 参数标签插入文本正确性**
    - **Validates: Requirements 5.5, 5.7, 5.9**
    - 使用 fast-check 生成任意参数（含 parameterPath）
    - 验证：参数列表插入 `{parameterPath}`，循环块插入 `{#name}\n\n{/name}`，条件块插入 `{#if expr}\n\n{/if}`
  - [x] 5.2 编写 Property 8: 循环块区域仅包含 ARRAY 类型参数
    - 文件：`frontend/src/__tests__/components/ParameterSidebar.property.test.ts`
    - **Property 8: 循环块区域仅包含 ARRAY 类型参数**
    - **Validates: Requirements 5.6**
    - 使用 fast-check 生成任意参数集合
    - 验证：循环块区域参数全部为 ARRAY 类型，且所有 ARRAY 参数都出现
  - [x] 5.3 编写 Property 10: 参数搜索与过滤正确性
    - 文件：`frontend/src/__tests__/components/ParameterSidebar.property.test.ts`
    - **Property 10: 参数搜索与过滤正确性**
    - **Validates: Requirements 9.2, 9.3**
    - 使用 fast-check 生成任意参数列表、搜索文本和类型过滤器
    - 验证：过滤结果恰好包含名称含搜索文本且类型匹配的参数

- [x] 6. 后端属性测试 — ContentIsolationValidator
  - [x] 6.1 编写 Property 9: 内容隔离校验完备性
    - 文件：`backend/src/test/java/com/docgen/property/ContentIsolationValidatorPropertyTest.java`
    - **Property 9: 内容隔离校验完备性**
    - **Validates: Requirements 6.4, 6.5, 6.6**
    - 使用 jqwik 生成具有受控内容的 .docx 文件（控制 header/footer/body 是否有内容）
    - 验证：body 类型 → header/footer 为空时通过，否则拒绝；header 类型 → body/footer 为空时通过；footer 类型 → body/header 为空时通过
    - 最小迭代次数 100

- [x] 7. 前端单元测试
  - [x] 7.1 编写 useDesignStep 单元测试
    - 文件：`frontend/src/__tests__/composables/useDesignStep.test.ts`
    - 测试步骤切换、状态保持（CP-4）、边界条件
    - _Requirements: 1.1, 1.4, 1.5_
  - [x] 7.2 编写 useCanvasNodes 单元测试
    - 文件：`frontend/src/__tests__/composables/useCanvasNodes.test.ts`
    - 测试 fromSegments/toSegments 序列化往返、空列表、控制节点插入删除
    - _Requirements: 4.5, 4.15, 4.16_

- [x] 8. Final checkpoint — 确保所有测试通过
  - 执行 `cd frontend && npx vitest run` 确认前端测试通过
  - 执行 `cd backend && mvn test -q` 确认后端测试通过
  - 确保所有 tests pass，ask the user if questions arise.

## Notes

- 每个属性测试必须包含注释标签：`// Feature: design-stage-layout, Property N: {title}`
- 前端 PBT 使用 Vitest + fast-check，后端 PBT 使用 JUnit 5 + jqwik
- Property 4 可复用现有 `useSegmentDrag.test.ts` 的测试结构
- 所有属性测试最小迭代次数 100

# Implementation Plan: Array Aggregation & Row-Level Derived Parameters

## Overview

Implement a 5-step computation pipeline (REQUEST validation → nested DERIVED → aggregation → root DERIVED → Docxtemplater) with new `AggregationResolver` service, extended `ParameterValidationService`, scope-aware expression validation, template scan integration, Node.js render preprocessing, and frontend sidebar/editor enhancements. No database schema changes required.

## Tasks

- [x] 1. Add ErrorCode constant and new DTOs
  - [x] 1.1 Add `PARAMETER_EXPRESSION_INVALID_SCOPE` constant to `ErrorCode.java`
    - Add to the `// ── PARAMETER` section
    - _Requirements: 3.3, 3.4_
  - [x] 1.2 Create `AggregationPropertyDTO.java` record
    - Fields: `name`, `placeholderPath`, `resultDataType`, `description`
    - Path: `backend/src/main/java/com/docgen/dto/AggregationPropertyDTO.java`
    - _Requirements: 2.1_
  - [x] 1.3 Create `AggregationSchemaDTO.java` record
    - Fields: `arrayName`, `arrayPath`, `properties` (List of AggregationPropertyDTO)
    - Path: `backend/src/main/java/com/docgen/dto/AggregationSchemaDTO.java`
    - _Requirements: 2.1_

- [x] 2. Implement AggregationResolver service
  - [x] 2.1 Create `AggregationResolver.java` with `computeAggregations` method
    - Path: `backend/src/main/java/com/docgen/service/AggregationResolver.java`
    - Implement `computeAggregations(context, rootParams, childrenMap)`: recursively find ARRAY params, compute `$count`, `$sum_*`, `$avg_*`, `$min_*`, `$max_*`, `$join_*`, `$first`, `$last` and inject into context as flat keys (`arrayName.$propName`)
    - Handle empty arrays (safe defaults), null NUMBER values (exclude from aggregates), `$avg` with HALF_UP 2 decimal places, nested ARRAY recursion (inner before outer)
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10_
  - [x] 2.2 Write property test: aggregation math correctness (Property 1)
    - **Property 1: 聚合计算数学正确性**
    - Generate random ARRAY data (0–50 elements, NUMBER fields with nulls, STRING fields)
    - Assert `$count == array.length`, `$sum == sum of non-nulls`, `$avg == sum/nonNullCount rounded HALF_UP 2dp`, `$min`/`$max` correct, `$join` correct, `$first`/`$last` correct
    - Tag: `// Feature: array-aggregation-and-row-derived, Property 1: 聚合计算数学正确性`
    - **Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.6, 1.7, 1.10**
  - [x] 2.3 Write property test: nested ARRAY aggregation independence (Property 2)
    - **Property 2: 嵌套 ARRAY 聚合独立性**
    - Generate random 2-level nested ARRAY structures; mutate one inner array and verify sibling/outer aggregations unchanged
    - Tag: `// Feature: array-aggregation-and-row-derived, Property 2: 嵌套 ARRAY 聚合独立性`
    - **Validates: Requirements 1.9**
  - [x] 2.4 Write unit tests for AggregationResolver
    - Test edge cases: empty array, single element, all-null NUMBER fields, mixed types, nested arrays
    - _Requirements: 1.5, 1.6, 1.7_

- [x] 3. Implement AggregationResolver.getAggregationSchema and controller endpoint
  - [x] 3.1 Add `getAggregationSchema(templateId)` method to `AggregationResolver`
    - Query parameter tree, find all ARRAY params, generate schema including `$count`, `$first`, `$last` unconditionally; `$sum_*`/`$avg_*`/`$min_*`/`$max_*` for NUMBER children (including Row_Level_Derived NUMBER); `$join_*` for STRING children; correct placeholder paths using full parameter path prefix
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_
  - [x] 3.2 Add `GET /api/templates/{templateId}/aggregation-schema` endpoint to `ParameterController`
    - Inject `AggregationResolver`, delegate to `getAggregationSchema`, return `List<AggregationSchemaDTO>`
    - _Requirements: 2.1_
  - [x] 3.3 Write property test: aggregation schema completeness (Property 3)
    - **Property 3: 聚合 Schema 完整性**
    - Generate random ParameterDefinition trees with ARRAY + various child types; verify schema includes correct properties per child data type and correct placeholder paths
    - Tag: `// Feature: array-aggregation-and-row-derived, Property 3: 聚合 Schema 完整性`
    - **Validates: Requirements 2.2, 2.3, 2.5, 2.6, 5.5**

- [x] 4. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Extend ParameterService with expression scope validation and circular dependency detection for nested DERIVED
  - [x] 5.1 Add `validateExpressionScope` method to `ParameterService`
    - When DERIVED parameter has non-null `parentId`, extract referenced names from expression, compare against sibling field names under same parent; if any non-sibling references found, throw `PARAMETER_EXPRESSION_INVALID_SCOPE` with invalid refs and valid sibling names
    - _Requirements: 3.3, 3.4_
  - [x] 5.2 Integrate scope validation into `createParameter` and `updateParameter`
    - Call `validateExpressionScope` when `parameterType=DERIVED && parentId != null`
    - _Requirements: 3.3, 3.4, 3.6_
  - [x] 5.3 Extend `detectCircularDependency` for same-parent DERIVED parameters
    - When checking a non-root DERIVED parameter, scope the dependency graph to DERIVED parameters under the same parent only
    - _Requirements: 3.5_
  - [x] 5.4 Write property test: expression scope validation (Property 4)
    - **Property 4: 表达式作用域验证**
    - Generate random expression text + random sibling/non-sibling parameter name sets; verify rejection when non-sibling referenced, acceptance otherwise
    - Tag: `// Feature: array-aggregation-and-row-derived, Property 4: 表达式作用域验证`
    - **Validates: Requirements 3.3, 3.4**
  - [x] 5.5 Write property test: sibling circular dependency detection (Property 5)
    - **Property 5: 同级循环依赖检测**
    - Generate random directed graphs among same-parent DERIVED params (with/without cycles); verify cycle detection/acceptance
    - Tag: `// Feature: array-aggregation-and-row-derived, Property 5: 同级循环依赖检测`
    - **Validates: Requirements 3.5**

- [x] 6. Extend ParameterValidationService with nested DERIVED evaluation and 5-step pipeline
  - [x] 6.1 Add `evaluateNestedDerivedParameters` method to `ParameterValidationService`
    - Recursively traverse data context: for each ARRAY, iterate rows and evaluate Row_Level_Derived children in sort_order with row-scoped context (sibling fields only); for each OBJECT, evaluate Nested_Derived children similarly; process inner nesting levels before outer
    - Handle empty arrays (skip without error), evaluation failures (throw with array path, row index, param name)
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.9, 4.10_
  - [x] 6.2 Refactor `validateAndBuildContext` into 5-step pipeline
    - Step 1: validate REQUEST params (existing logic)
    - Step 2: `evaluateNestedDerivedParameters(context, rootParams, childrenByParentId)`
    - Step 3: `aggregationResolver.computeAggregations(context, rootParams, childrenByParentId)`
    - Step 4: `evaluateDerivedParameters(templateId, context, rootParams, childrenByParentId)` (existing root DERIVED)
    - Step 5: return complete context
    - Inject `AggregationResolver` dependency
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 4.8, 9.1, 9.2, 9.3, 9.4_
  - [x] 6.3 Write property test: DERIVED scope isolation (Property 6)
    - **Property 6: DERIVED 作用域隔离**
    - Generate random ARRAY data with Row_Level_Derived; modify row j fields, verify row i DERIVED result unchanged
    - Tag: `// Feature: array-aggregation-and-row-derived, Property 6: DERIVED 作用域隔离`
    - **Validates: Requirements 4.2, 4.3**
  - [x] 6.4 Write property test: sort-order evaluation dependency (Property 7)
    - **Property 7: Sort-Order 求值依赖**
    - Generate random DERIVED param sets with dependency chains under same parent; verify evaluation in sort_order with accumulated context
    - Tag: `// Feature: array-aggregation-and-row-derived, Property 7: Sort-Order 求值依赖`
    - **Validates: Requirements 4.4**
  - [x] 6.5 Write property test: recursive nested DERIVED evaluation (Property 8)
    - **Property 8: 递归嵌套 DERIVED 求值**
    - Generate nested ARRAY structures with Row_Level_Derived at multiple levels; verify inner-level evaluated before outer-level
    - Tag: `// Feature: array-aggregation-and-row-derived, Property 8: 递归嵌套 DERIVED 求值`
    - **Validates: Requirements 4.10**
  - [x] 6.6 Write unit tests for 5-step pipeline
    - Test end-to-end pipeline: REQUEST validation → nested DERIVED → aggregation → root DERIVED referencing aggregation values
    - _Requirements: 7.1, 7.2, 7.3_

- [x] 7. Checkpoint — Ensure all backend tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Extend TemplateScanService for AGGREGATION placeholder type
  - [x] 8.1 Update `PLACEHOLDER_PATTERN` regex and `buildSimplePlaceholder` in `TemplateScanService`
    - Extend regex to match `$` prefix segments in placeholder paths
    - In `buildSimplePlaceholder`, classify placeholders containing `$` prefix segments as type `"AGGREGATION"`
    - _Requirements: 8.1_
  - [x] 8.2 Extend `ParameterService.scanPlaceholders` to match AGGREGATION placeholders
    - For `AGGREGATION` type placeholders, call `AggregationResolver.getAggregationSchema` to get valid aggregation properties; match valid ones, classify invalid ones as unmatched with descriptive reason
    - _Requirements: 8.2, 8.3_
  - [x] 8.3 Write property test: aggregation placeholder scan correctness (Property 9)
    - **Property 9: 聚合占位符扫描正确性**
    - Generate random placeholder strings (with/without `$` prefix); verify AGGREGATION classification and matched/unmatched status
    - Tag: `// Feature: array-aggregation-and-row-derived, Property 9: 聚合占位符扫描正确性`
    - **Validates: Requirements 8.1, 8.2, 8.3**
  - [x] 8.4 Write property test: namespace isolation (Property 10)
    - **Property 10: 命名空间隔离**
    - Generate random strings starting with `$`; verify NAME_PATTERN regex rejects all of them
    - Tag: `// Feature: array-aggregation-and-row-derived, Property 10: 命名空间隔离`
    - **Validates: Requirements 9.5**

- [x] 9. Extend Node.js render service with aggregation property preprocessing
  - [x] 9.1 Add `injectAggregationProperties` function to `docxtemplater-service/src/routes/render.js`
    - Before `doc.render(renderData)`, call `injectAggregationProperties(renderData)` to convert flat `"arrayName.$propName"` keys into JavaScript array properties
    - Recursively process nested array elements for nested aggregation properties
    - _Requirements: 1.8, 7.1 (Step 5)_
  - [x] 9.2 Write unit tests for `injectAggregationProperties`
    - Test flat key conversion, nested array recursion, empty data, no aggregation keys
    - _Requirements: 1.8_

- [x] 10. Checkpoint — Ensure all backend + Node.js tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. Frontend type and API extensions
  - [x] 11.1 Extend `frontend/src/types/parameter.ts`
    - Add `AggregationPropertyDTO` interface, `AggregationSchemaDTO` interface
    - Add `'AGGREGATION'` to `PlaceholderType` union
    - _Requirements: 2.1, 8.1_
  - [x] 11.2 Add `getAggregationSchema` API function to `frontend/src/api/parameters.ts`
    - `GET /templates/${templateId}/aggregation-schema` returning `AggregationSchemaDTO[]`
    - _Requirements: 2.1_

- [x] 12. Extend ParameterSidebar.vue with aggregation properties section
  - [x] 12.1 Add "聚合属性" collapse section to `ParameterSidebar.vue`
    - Extract all ARRAY parameters from store's parameter tree
    - Generate aggregation property tags client-side based on child field data types: `$count`/`$first`/`$last` unconditionally; `$sum_*`/`$avg_*`/`$min_*`/`$max_*` for NUMBER children; `$join_*` for STRING children
    - Use purple/distinct tag styling to differentiate from regular parameter tags
    - Click tag emits `insert-variable` with full placeholder path (e.g., `items.$sum_price`)
    - Search box filters aggregation tags by name or placeholder path
    - Refresh on parameter changes (computed from local store, no API call)
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_

- [x] 13. Extend DerivedExpressionEditor.vue with scope-aware variable filtering
  - [x] 13.1 Add `scopeLevel` prop to `DerivedExpressionEditor.vue`
    - Accept `scopeLevel: 'root' | 'row' | 'object'` prop
    - Filter `availableParameters` based on scope: `root` = all root REQUEST + root DERIVED + aggregation properties; `row`/`object` = sibling children only (excluding self)
    - Display scope indicator label: "行级表达式 — 仅可引用当前行字段" for `row`, "对象级表达式 — 仅可引用同级字段" for `object`
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_
  - [x] 13.2 Update `ParameterTreeTable.vue` to pass `scopeLevel` to expression editor
    - Determine scope level based on parent parameter's data type: ARRAY parent → `'row'`, OBJECT parent → `'object'`, no parent → `'root'`
    - Pass filtered `availableParameters` list accordingly
    - _Requirements: 6.1, 6.2, 6.3_
  - [x] 13.3 Write property test: scope-aware variable list (Property 11, fast-check)
    - **Property 11: 作用域感知变量列表**
    - Generate random parameter trees with DERIVED at different levels; verify variable list contains only correct scope parameters
    - Tag: `// Feature: array-aggregation-and-row-derived, Property 11: 作用域感知变量列表`
    - **Validates: Requirements 6.1, 6.2, 6.3**

- [x] 14. Add i18n keys for new UI elements
  - [x] 14.1 Add i18n entries for aggregation sidebar section and scope labels
    - Add keys for "聚合属性" section title, scope indicator labels, aggregation property descriptions
    - Add to all three locales: en-US, zh-CN, zh-TW
    - _Requirements: 5.1, 6.4_

- [x] 15. Final checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- All tasks are required (no optional tasks)
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties (jqwik for backend, fast-check for frontend)
- Unit tests validate specific examples and edge cases
- No database schema changes — all features use existing `template_parameters` table structure
- Backward compatibility maintained: existing templates with no ARRAY/nested DERIVED continue to work unchanged

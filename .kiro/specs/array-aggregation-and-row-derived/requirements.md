# Requirements Document

## Introduction

本功能为参数系统增加两个核心能力，以支持更丰富的模板数据处理场景：

1. **数组内置聚合属性（Array Built-in Aggregation Properties）**：系统自动为每个 ARRAY 类型参数生成一组虚拟聚合属性（如 `$count`、`$sum_字段`、`$avg_字段` 等），用户无需手动创建 DERIVED 参数或编写表达式，即可在模板中直接使用这些聚合值。聚合属性在文档生成时自动计算并注入数据上下文，Docxtemplater 渲染前即可使用。

2. **行级 DERIVED 参数（Row-Level Derived Parameters）**：扩展现有 DERIVED 参数机制，允许 ARRAY 子参数标记为 DERIVED 类型，在数组每一行内执行表达式计算。行级 DERIVED 的表达式上下文仅包含当前行的兄弟字段，实现如 `subtotal = price * quantity` 的行内运算。同时支持 OBJECT 子参数标记为 DERIVED，表达式上下文为同级兄弟字段。

3. **前端交互优化**：ParameterSidebar 新增"聚合属性"分组，自动列出所有 ARRAY 参数的可用聚合属性 tag，点击即插入模板；表达式编辑器根据参数层级动态调整可选变量列表。

### 与现有系统的关系

- 基于 template-parameter-redesign spec 定义的参数表数据模型（Parameter_Table、ParameterDefinition entity）
- 复用现有 ExpressionEngine 基础设施（JavaScript / Excel Formula，委托 Node.js 服务执行）
- 扩展 ParameterValidationService 的 `evaluateDerivedParameters` 方法以支持行级和嵌套级计算
- 扩展 ParameterValidationService 的 `validateAndBuildContext` 方法以注入聚合属性
- 扩展 TemplateScanService 以识别聚合属性占位符（`$` 前缀）
- 完全向后兼容：不修改现有参数定义的行为，不影响已有模板渲染
- 注意：现有 NAME_PATTERN (`^[a-zA-Z_][a-zA-Z0-9_-]*$`) 已天然阻止用户创建以 `$` 开头的参数名，无需新增校验逻辑

## Glossary

- **Parameter_Table**: 模板的参数定义集合，支持通过 parent_id 自引用构建多级嵌套树形结构（最大深度 5 层）
- **Parameter_Validation_Service**: 在文档生成时验证传入参数、计算衍生参数、构建数据上下文的后端服务
- **Expression_Engine**: 表达式引擎，支持 JavaScript 和 Excel 公式，委托 Node.js 服务在 isolated-vm 沙箱中执行
- **Aggregation_Property**: 系统自动为 ARRAY 类型参数生成的虚拟聚合属性，以 `$` 前缀命名（如 `$count`、`$sum_price`），在文档生成时动态计算并注入数据上下文
- **Aggregation_Resolver**: 后端服务组件，负责扫描参数树中的 ARRAY 参数，根据子字段定义生成所有可用聚合属性，并在数据上下文中计算和注入聚合值
- **Row_Level_Derived**: ARRAY 子参数中 parameter_type 为 DERIVED 的参数，表达式在数组每一行的上下文中独立执行，上下文仅包含当前行的兄弟字段值
- **Nested_Derived**: OBJECT 子参数中 parameter_type 为 DERIVED 的参数，表达式上下文为同一 OBJECT 下的兄弟字段值
- **Root_Level_Derived**: 根级别的 DERIVED 参数，表达式上下文包含所有根级 REQUEST 参数和之前已计算的 DERIVED 参数（现有行为）
- **Aggregation_Schema_API**: 返回指定模板所有 ARRAY 参数可用聚合属性列表的 REST API 端点
- **Parameter_Sidebar**: 模板工作区右侧的参数侧边栏组件，展示可插入模板的参数变量、循环块和条件块
- **Docxtemplater**: 模板渲染引擎，处理 `{variable}`、`{object.property}`、`{#array}...{/array}` 语法
- **Expression_Context**: 表达式执行时可访问的变量集合，根级 DERIVED 可访问所有根级参数，行级/嵌套级 DERIVED 仅可访问当前作用域的兄弟字段

## Requirements

### Requirement 1: 数组聚合属性自动生成与计算

**User Story:** As a 模板设计者, I want 系统自动为每个 ARRAY 参数生成聚合属性（如求和、计数、平均值等）, so that 我无需手动创建 DERIVED 参数即可在模板中使用常见的数组统计值。

#### Acceptance Criteria

1. WHEN the Aggregation_Resolver processes an ARRAY parameter during document generation, THE Aggregation_Resolver SHALL compute and inject a `$count` property into the data context at the ARRAY parameter level, representing the number of elements in the array
2. WHEN the Aggregation_Resolver processes an ARRAY parameter that has child parameters with data_type NUMBER, THE Aggregation_Resolver SHALL compute and inject `$sum_{fieldName}`, `$avg_{fieldName}`, `$min_{fieldName}`, and `$max_{fieldName}` properties for each NUMBER child field into the data context at the ARRAY parameter level
3. WHEN the Aggregation_Resolver processes an ARRAY parameter that has child parameters with data_type STRING, THE Aggregation_Resolver SHALL compute and inject a `$join_{fieldName}` property for each STRING child field, concatenating all element values with the separator ", " (comma followed by a space)
4. WHEN the Aggregation_Resolver processes an ARRAY parameter with at least one element, THE Aggregation_Resolver SHALL inject `$first` and `$last` properties into the data context, where `$first` contains the first element object and `$last` contains the last element object of the array
5. WHEN the ARRAY parameter contains zero elements, THE Aggregation_Resolver SHALL set `$count` to 0, all `$sum_` properties to 0, all `$avg_` properties to 0, all `$min_` and `$max_` properties to null, `$first` and `$last` to null, and all `$join_` properties to an empty string
6. WHEN a NUMBER child field contains null values in some array elements, THE Aggregation_Resolver SHALL exclude null values from `$sum_`, `$avg_`, `$min_`, and `$max_` calculations, computing aggregates only over non-null values
7. WHEN all values of a NUMBER child field are null across all array elements, THE Aggregation_Resolver SHALL set `$sum_{fieldName}` to 0, `$avg_{fieldName}` to 0, `$min_{fieldName}` to null, and `$max_{fieldName}` to null
8. THE Aggregation_Resolver SHALL compute and inject all aggregation properties into the data context BEFORE the context is passed to Docxtemplater for template rendering, and AFTER all Row_Level_Derived parameters have been evaluated
9. WHEN the data context contains nested ARRAY parameters (e.g., `orders` containing `items` ARRAY), THE Aggregation_Resolver SHALL compute aggregation properties at each ARRAY level independently (e.g., `orders.$count` and within each order element `items.$count`, `items.$sum_price`)
10. WHEN the Aggregation_Resolver computes `$avg_{fieldName}`, THE Aggregation_Resolver SHALL use the count of non-null values as the divisor (not the total array length), and SHALL round the result to 2 decimal places using HALF_UP rounding mode

### Requirement 2: 聚合属性 Schema API

**User Story:** As a 前端开发者, I want 通过 API 获取指定模板所有 ARRAY 参数的可用聚合属性列表, so that 前端侧边栏可以自动展示这些属性供用户点击插入。

#### Acceptance Criteria

1. WHEN a GET request is sent to `/api/templates/{templateId}/aggregation-schema`, THE Aggregation_Schema_API SHALL return a JSON array, where each element contains the ARRAY parameter name, the ARRAY parameter path, and a list of available aggregation property objects (each with property name, placeholder path, data type of the result, and a human-readable description in Chinese)
2. WHEN the Aggregation_Schema_API generates the aggregation property list for an ARRAY parameter, THE Aggregation_Schema_API SHALL include `$count` unconditionally, `$sum_{fieldName}`, `$avg_{fieldName}`, `$min_{fieldName}`, `$max_{fieldName}` for each NUMBER child field, `$join_{fieldName}` for each STRING child field, and `$first`, `$last` unconditionally
3. WHEN the Aggregation_Schema_API generates placeholder paths, THE Aggregation_Schema_API SHALL use the full parameter path prefix (e.g., for ARRAY parameter `items` with NUMBER child `price`, the placeholder path for sum SHALL be `items.$sum_price`)
4. IF the template has no ARRAY parameters, THEN THE Aggregation_Schema_API SHALL return an empty array
5. WHEN the Aggregation_Schema_API processes nested ARRAY parameters, THE Aggregation_Schema_API SHALL include aggregation properties for each ARRAY at its respective nesting level with correct path prefixes
6. WHEN the Aggregation_Schema_API generates Row_Level_Derived child parameters under an ARRAY, THE Aggregation_Schema_API SHALL also generate aggregation properties for those DERIVED fields if their data_type is NUMBER or STRING (e.g., if `subtotal` is a Row_Level_Derived NUMBER field under `items`, then `items.$sum_subtotal` SHALL be included)

### Requirement 3: 行级与嵌套级 DERIVED 参数定义与验证

**User Story:** As a 模板设计者, I want 在 ARRAY 或 OBJECT 子参数中定义 DERIVED 类型参数, so that 我可以在数组每一行内或对象内部执行计算（如 subtotal = price * quantity 或 fullAddress = province + city）。

#### Acceptance Criteria

1. WHEN a POST request creates a parameter with parameter_type DERIVED under an ARRAY parent, THE Parameter_API SHALL accept the request and create the Row_Level_Derived parameter with expression_text and expression_type stored in the Parameter_Table
2. WHEN a POST request creates a parameter with parameter_type DERIVED under an OBJECT parent, THE Parameter_API SHALL accept the request and create the Nested_Derived parameter with expression_text and expression_type stored in the Parameter_Table
3. WHEN a Row_Level_Derived or Nested_Derived parameter is created, THE Parameter_API SHALL validate that the expression_text only references sibling field names (other child parameters under the same parent), and IF the expression references a non-sibling parameter name, THEN THE Parameter_API SHALL return HTTP 400 with error code PARAMETER_EXPRESSION_INVALID_SCOPE listing the invalid references
4. WHEN a Row_Level_Derived or Nested_Derived parameter is updated, THE Parameter_API SHALL re-validate the expression scope against current sibling fields under the same parent
5. WHEN multiple DERIVED parameters exist under the same parent, THE Parameter_API SHALL validate that no circular dependencies exist among them (based on sort_order evaluation sequence), and IF a cycle is detected, THEN THE Parameter_API SHALL return HTTP 400 with error code PARAMETER_CIRCULAR_DEPENDENCY listing the cycle path
6. THE Parameter_API SHALL allow DERIVED parameters as children of ARRAY and OBJECT parameters while continuing to enforce the existing constraint that only OBJECT and ARRAY data_type parameters can have children (Row_Level_Derived and Nested_Derived parameters are leaf nodes with data_type STRING, NUMBER, DATE, or BOOLEAN)

### Requirement 4: 行级与嵌套级 DERIVED 参数运行时计算

**User Story:** As a API 调用者, I want 文档生成时系统自动计算数组每一行和对象内部的 DERIVED 字段, so that 模板中可以直接使用行级和嵌套级计算结果。

#### Acceptance Criteria

1. WHEN the Parameter_Validation_Service processes an ARRAY parameter during document generation, THE Parameter_Validation_Service SHALL identify all Row_Level_Derived child parameters (parameter_type = DERIVED) under the ARRAY parent and evaluate the expression for each array element row
2. WHEN evaluating a Row_Level_Derived parameter for a specific array element, THE Expression_Engine SHALL receive an Expression_Context containing only the sibling field values from that specific row (not root-level parameters or other rows)
3. WHEN the Parameter_Validation_Service processes an OBJECT parameter that has Nested_Derived children, THE Parameter_Validation_Service SHALL evaluate the Nested_Derived expressions with an Expression_Context containing only the sibling field values within that OBJECT
4. WHEN multiple DERIVED parameters exist under the same parent (ARRAY or OBJECT), THE Parameter_Validation_Service SHALL evaluate them in sort_order, making earlier computed values available to later expressions in the same scope
5. WHEN a DERIVED parameter evaluation succeeds, THE Parameter_Validation_Service SHALL inject the computed value into the corresponding data map (row map for ARRAY, object map for OBJECT) using the DERIVED parameter name as the key
6. IF a Row_Level_Derived parameter expression evaluation fails for any row, THEN THE Parameter_Validation_Service SHALL return HTTP 400 with error code PARAMETER_EXPRESSION_EVALUATION_FAILED, including the ARRAY parameter path, the row index, and the DERIVED parameter name in the error message
7. IF a Nested_Derived parameter expression evaluation fails, THEN THE Parameter_Validation_Service SHALL return HTTP 400 with error code PARAMETER_EXPRESSION_EVALUATION_FAILED, including the OBJECT parameter path and the DERIVED parameter name in the error message
8. THE Parameter_Validation_Service SHALL evaluate all nested-level DERIVED parameters (both Row_Level_Derived and Nested_Derived) BEFORE evaluating Root_Level_Derived parameters, so that root-level expressions can reference the computed nested values
9. WHEN the ARRAY parameter contains zero elements, THE Parameter_Validation_Service SHALL skip Row_Level_Derived evaluation for that ARRAY parameter without error
10. WHEN nested ARRAY parameters contain Row_Level_Derived parameters, THE Parameter_Validation_Service SHALL recursively evaluate row-level DERIVED parameters at each nesting level, processing inner arrays before outer arrays

### Requirement 5: 前端聚合属性侧边栏分组

**User Story:** As a 模板设计者, I want 在参数侧边栏看到一个"聚合属性"分组，自动列出所有 ARRAY 参数的可用聚合属性, so that 我可以点击 tag 直接将聚合占位符插入模板，零配置。

#### Acceptance Criteria

1. WHEN the Parameter_Sidebar is rendered and the template has ARRAY parameters with child fields, THE Parameter_Sidebar SHALL display an "聚合属性" collapse section listing all available aggregation property tags grouped by ARRAY parameter name
2. WHEN the user clicks an aggregation property tag in the Parameter_Sidebar, THE Parameter_Sidebar SHALL emit an insert-variable event with the full placeholder path (e.g., `items.$sum_price`), inserting the placeholder into the template editor at the cursor position
3. WHEN the template parameters change (add, edit, or delete), THE Parameter_Sidebar SHALL refresh the aggregation property list by re-computing from the local parameter store (no API call needed for the aggregation tag list, since it can be derived from the parameter tree structure client-side); the Aggregation_Schema_API is used only for initial load and for the preview panel
4. WHEN the user types in the search box of the Parameter_Sidebar, THE Parameter_Sidebar SHALL filter aggregation property tags by matching the search text against the property name or the full placeholder path
5. WHEN an ARRAY parameter has no NUMBER or STRING child fields (only BOOLEAN or DATE children), THE Parameter_Sidebar SHALL display only `$count`, `$first`, and `$last` aggregation tags for that ARRAY parameter
6. THE aggregation property tags SHALL use distinct visual styling (e.g., a different tag color or icon) to differentiate them from regular parameter tags and loop block tags

### Requirement 6: 前端表达式编辑器层级感知

**User Story:** As a 模板设计者, I want 表达式编辑器根据参数所在层级自动调整可选变量列表, so that 行级/嵌套级 DERIVED 参数只能选择同级兄弟字段，根级 DERIVED 参数可以选择所有根级参数和聚合属性。

#### Acceptance Criteria

1. WHEN the expression editor is opened for a Root_Level_Derived parameter, THE expression editor SHALL populate the variable dropdown and autocomplete list with all root-level REQUEST parameters, previously defined root-level DERIVED parameters (by sort_order), and all available aggregation properties (e.g., `items.$count`, `items.$sum_price`)
2. WHEN the expression editor is opened for a Row_Level_Derived parameter (DERIVED child under an ARRAY parent), THE expression editor SHALL populate the variable dropdown and autocomplete list with only the sibling child parameters under the same ARRAY parent (excluding the current parameter being edited)
3. WHEN the expression editor is opened for a Nested_Derived parameter (DERIVED child under an OBJECT parent), THE expression editor SHALL populate the variable dropdown and autocomplete list with only the sibling child parameters under the same OBJECT parent (excluding the current parameter being edited)
4. WHEN the expression editor is opened for a Row_Level_Derived or Nested_Derived parameter, THE expression editor SHALL display a visual indicator (label or badge) stating "行级表达式 — 仅可引用当前行字段" or "对象级表达式 — 仅可引用同级字段" respectively
5. WHEN the user constructs an expression in the Visual_Expression_Builder for a non-root DERIVED parameter, THE Visual_Expression_Builder SHALL restrict the source parameter dropdown to sibling fields only, preventing selection of root-level or other-scope parameters
6. WHEN the user types a parameter name in the advanced mode code editor for a non-root DERIVED parameter, THE expression editor SHALL display autocomplete suggestions filtered to sibling field names only

### Requirement 7: 聚合属性与行级 DERIVED 的协同计算顺序

**User Story:** As a API 调用者, I want 系统按正确顺序执行行级计算、聚合属性计算和根级计算, so that 所有参数值在模板渲染时均已正确计算。

#### Acceptance Criteria

1. THE Parameter_Validation_Service SHALL execute the computation pipeline in the following strict order during document generation: Step 1 validate all REQUEST parameters → Step 2 evaluate nested-level DERIVED parameters (Row_Level_Derived within each ARRAY row, Nested_Derived within each OBJECT; inner nesting levels first) → Step 3 compute Aggregation_Properties for each ARRAY → Step 4 evaluate Root_Level_Derived parameters → Step 5 pass complete context to Docxtemplater
2. WHEN a Root_Level_Derived parameter expression references an aggregation property (e.g., `items.$sum_price`), THE Expression_Engine SHALL resolve the aggregation property value from the data context, since aggregation properties are computed in Step 3 before root-level DERIVED evaluation in Step 4
3. WHEN a Root_Level_Derived parameter expression references a Row_Level_Derived field via array access (e.g., `items.reduce((sum, item) => sum + item.subtotal, 0)`), THE Expression_Engine SHALL resolve the row-level computed values from the data context, since row-level DERIVED parameters are evaluated in Step 2 before root-level evaluation in Step 4
4. IF the computation pipeline encounters an error at any step, THEN THE Parameter_Validation_Service SHALL halt execution and return the error with the step context (step name, parameter name, and error details)

### Requirement 8: 聚合属性与模板扫描集成

**User Story:** As a 模板设计者, I want 模板扫描功能能识别聚合属性占位符, so that 使用了聚合属性的占位符不会被误报为"未匹配"。

#### Acceptance Criteria

1. WHEN the TemplateScanService scans a template and encounters a placeholder containing a `$` prefix segment (e.g., `{items.$count}`, `{items.$sum_price}`), THE TemplateScanService SHALL recognize it as an aggregation property placeholder and classify it as type AGGREGATION
2. WHEN the scan result compares placeholders against parameter definitions, THE scan SHALL match aggregation property placeholders against the Aggregation_Schema (not against Parameter_Definitions), and IF the aggregation property is valid for the corresponding ARRAY parameter, THEN THE scan SHALL classify it as "matched"
3. IF a template placeholder references an aggregation property for a non-existent ARRAY parameter or an invalid aggregation function, THEN THE scan SHALL classify it as "unmatched_placeholder" with a descriptive reason

### Requirement 9: 向后兼容性保障

**User Story:** As a 系统管理员, I want 新功能完全向后兼容现有参数定义和模板渲染, so that 升级后所有现有模板继续正常工作。

#### Acceptance Criteria

1. WHEN a template has no ARRAY parameters, THE Aggregation_Resolver SHALL skip aggregation computation entirely and produce no side effects on the data context
2. WHEN a template has ARRAY parameters but no Row_Level_Derived children, THE Parameter_Validation_Service SHALL skip row-level DERIVED evaluation for those ARRAY parameters and proceed directly to aggregation computation
3. WHEN a template has only Root_Level_Derived parameters (no nested-level DERIVED), THE Parameter_Validation_Service SHALL maintain the existing evaluation behavior (evaluate root DERIVED in sort_order with full root context)
4. THE Parameter_Validation_Service SHALL NOT modify the behavior of existing REQUEST parameter validation, default value application, or type checking
5. THE aggregation property names (prefixed with `$`) SHALL NOT conflict with user-defined parameter names; the existing NAME_PATTERN validation (`^[a-zA-Z_][a-zA-Z0-9_-]*$`) already prevents users from creating parameter names starting with `$`, so no additional validation logic is needed

## Correctness Properties

### CP-1: 聚合计算数学正确性 (Req 1)
对于任意非空 ARRAY 参数，`$count` 必须等于数组元素数量；`$sum_{field}` 必须等于该字段所有非 null 值之和；`$avg_{field}` 必须等于 `$sum_{field}` 除以非 null 值数量（保留 2 位小数 HALF_UP）；`$min_{field}` 和 `$max_{field}` 必须分别等于非 null 值中的最小值和最大值。

### CP-2: 行级 DERIVED 隔离性 (Req 4)
对于 ARRAY 中任意两行 i 和 j (i ≠ j)，行 i 的 Row_Level_Derived 计算结果不受行 j 的字段值影响。即：修改行 j 的任意 REQUEST 字段值，行 i 的 DERIVED 计算结果不变。

### CP-3: 计算顺序依赖正确性 (Req 7)
在五步计算管道中，Step N 的输出必须在 Step N+1 的输入上下文中可用。特别地：Root_Level_Derived 表达式引用 `items.$sum_subtotal` 时，该值必须已在 Step 3 中计算完成。

### CP-4: 聚合属性命名空间隔离 (Req 9)
聚合属性名称（`$` 前缀）与用户定义的参数名称不存在冲突的可能性，因为 NAME_PATTERN 不允许 `$` 开头。

### CP-5: 空数组安全性 (Req 1, Req 4)
当 ARRAY 包含零个元素时，所有聚合属性必须返回安全默认值（`$count` = 0, `$sum_*` = 0, `$avg_*` = 0, `$min_*`/`$max_*` = null），且不触发任何 Row_Level_Derived 计算错误。

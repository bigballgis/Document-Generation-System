# Requirements Document

## Introduction

本功能将模板的参数管理系统从当前的"数据源 (DataSource) + 表达式 (Expression) + 模板变量 (TemplateVariable) 手动绑定"三层架构，重新设计为统一的"参数表 (Parameter Table)"概念，并彻底移除外部数据源 (DataSource) 功能。

当前系统的核心问题：
- DataSource 概念过重（HTTP_API、DATABASE、INTERNAL_SYSTEM），破坏系统独立性，用户只需简单定义参数
- Expression 和 DataSource 概念分离，但本质都是"为模板提供数据"
- TemplateVariable 需要手动绑定到 DataSource 或 Expression，流程繁琐
- API 调用者无法知道模板需要哪些参数（缺少参数 Schema）
- 覆盖率检查仅验证变量是否绑定，不验证测试用例是否覆盖了模板的所有分支、循环和参数
- 参数缺少类型验证、默认值、必填标记等元数据
- 参数缺少校验规则（非空、长度、范围、正则、枚举等），无法在 API 层面保证数据安全

新设计将：
1. 彻底移除外部数据源功能（HTTP_API、DATABASE、INTERNAL_SYSTEM），系统通过 AI + 参数表生成测试数据
2. 将参数分为两类：
   - 请求参数 (Request Parameter)：调用 API 时直接传入
   - 衍生参数 (Derived Parameter)：通过表达式从请求参数计算得出
3. 简化文档生成流程为：参数验证 → 衍生参数计算 → 模板渲染

模板中的 `{variable}` 直接对应参数表中的参数名，无需手动绑定。

参数表支持多级嵌套的树形结构，以匹配 Docxtemplater 的对象属性访问（`{company.name}`）和数组循环（`{#items}{name}{/items}`）语法：
- OBJECT 类型参数可包含子参数（父子关系），如 `company` → `company.name`、`company.address` → `company.address.city`
- ARRAY 类型参数可包含元素字段（一对多关系），如 `items` → `items[].name`、`items[].price`
- 通过 parent_id 自引用外键实现多级嵌套层级（最大深度 5 层）

## Glossary

- **Parameter_Table**: 模板的参数定义集合，描述该模板 API 接受的所有参数及其元数据，支持通过 parent_id 自引用构建多级嵌套树形结构（最大深度 5 层）
- **Request_Parameter**: 调用文档生成 API 时由调用者直接传入的参数
- **Derived_Parameter**: 通过表达式从其他参数（请求参数或其他衍生参数）计算得出的参数
- **Parameter_Definition**: 参数表中的单条参数定义，包含名称、类型、是否必填、默认值、描述、parent_id 等元数据；根参数的 parent_id 为 NULL，子参数通过 parent_id 指向父参数
- **Parameter_Path**: 参数的完整路径，通过父子链从根到叶拼接而成（如 `company.address.city`），用于与模板占位符匹配
- **Template_Placeholder**: 模板 .docx 文件中的 Docxtemplater 变量占位符，包括简单变量 `{variableName}`、对象属性访问 `{object.property}`、数组循环 `{#array}{field}{/array}` 等语法
- **Coverage_Report**: 基于测试用例执行结果的模板覆盖率报告，包含分支覆盖率、循环覆盖率、参数覆盖率三个维度
- **Branch_Coverage**: 模板中 `{#if condition}...{/if}` 条件分支在测试用例执行中被 true 和 false 两种情况触发的比例
- **Loop_Coverage**: 模板中 `{#loop array}...{/loop}` 循环在测试用例执行中被空数组和非空数组两种情况触发的比例
- **Parameter_Coverage**: 参数表中所有参数在至少一个测试用例中被赋值使用的比例
- **Test_Case**: 模板的测试用例，包含一组输入参数（testDataJson），用于验证模板渲染结果
- **Inline_Editing**: 直接在树表格行内点击字段即可编辑的交互模式，无需打开对话框，支持 name、data_type、required、default_value、description 等字段
- **Preview_Panel**: 参数表右侧的实时预览面板，同步显示当前参数结构对应的 JSON Schema、示例请求体 JSON 和模板占位符匹配状态
- **Visual_Expression_Builder**: 衍生参数的可视化表达式构建器，通过下拉选择参数、运算符和函数来组装表达式，无需手写代码
- **Parameter_Template**: 预定义的常用参数结构模板（如"地址"、"联系人"、"商品列表"），可一键插入到参数表中，自动创建完整的嵌套层级
- **Context_Menu**: 参数行上的右键菜单，提供复制、粘贴、删除、添加同级、添加子参数等快捷操作
- **Parameter_Schema**: 参数表的结构化描述，用于自动生成 API 文档
- **Expression_Engine**: 现有的表达式引擎（支持 JavaScript 和 Excel 公式），用于计算衍生参数
- **Migration_Service**: 负责将现有模板的旧数据模型（Expression + TemplateVariable 绑定）迁移到新参数表模型，并彻底移除 DataSource 相关数据和代码的服务
- **Parameter_Validation_Service**: 在文档生成时验证传入参数是否符合参数表定义的服务
- **Validation_Rules**: 参数的校验规则配置，以 JSONB 格式存储在 Parameter_Definition 中，支持 not_null、not_blank、min_length、max_length、min、max、pattern、enum_values、min_items、max_items 和 custom_message 等规则类型，用于在文档生成时对传入参数值进行逐条校验
- **Workspace**: 模板工作区，用户管理模板的主界面，包含多个功能标签页

## Requirements

### Requirement 1: 参数表数据模型

**User Story:** As a 模板设计者, I want 为模板定义一个统一的参数表, so that 所有参数（请求参数和衍生参数）在一个地方集中管理。

#### Acceptance Criteria

1. THE Parameter_Table SHALL store each Parameter_Definition with the following attributes: name (VARCHAR 100), parameter_type (REQUEST or DERIVED), data_type (STRING, NUMBER, DATE, BOOLEAN, ARRAY, OBJECT), required (BOOLEAN), default_value (TEXT), description (TEXT), sort_order (INT), expression_text (TEXT, only for DERIVED type), expression_type (VARCHAR 20, only for DERIVED type), validation_rules (JSONB, nullable), parent_id (BIGINT, nullable, self-referencing foreign key to the same table), and version (INT, for optimistic locking, default 0)
2. THE Parameter_Table SHALL enforce a unique constraint on (template_id, parent_id, name) to prevent duplicate parameter names within the same parent scope, where parent_id NULL represents root-level parameters
3. WHEN a Parameter_Definition has parameter_type set to DERIVED, THE Parameter_Table SHALL require expression_text to be non-empty
4. WHEN a Parameter_Definition has parameter_type set to REQUEST, THE Parameter_Table SHALL store expression_text as NULL
5. THE Parameter_Table SHALL support expression_type (JAVASCRIPT or EXCEL_FORMULA) for DERIVED parameters to specify the expression language
6. THE Parameter_Table SHALL store a template_id foreign key referencing the templates table with ON DELETE CASCADE
7. THE Parameter_Table SHALL store validation_rules as a JSONB column supporting the following rule keys: not_null (BOOLEAN), not_blank (BOOLEAN), min_length (INTEGER, for STRING type), max_length (INTEGER, for STRING type), min (NUMBER, for NUMBER type), max (NUMBER, for NUMBER type), pattern (STRING, regular expression for STRING type), enum_values (ARRAY of STRING, restricting allowed values), min_items (INTEGER, for ARRAY type), max_items (INTEGER, for ARRAY type), and custom_message (STRING, user-defined error message override)
8. WHEN a Parameter_Definition has validation_rules containing min_length and max_length, THE Parameter_Table SHALL enforce that min_length is less than or equal to max_length
9. WHEN a Parameter_Definition has validation_rules containing min and max, THE Parameter_Table SHALL enforce that min is less than or equal to max
10. WHEN a Parameter_Definition has validation_rules containing min_items and max_items, THE Parameter_Table SHALL enforce that min_items is less than or equal to max_items
11. THE Parameter_Table SHALL store parent_id as a self-referencing foreign key with ON DELETE CASCADE, so that deleting a parent parameter cascades deletion to all descendant parameters
12. WHEN a Parameter_Definition has parent_id set to NULL, THE Parameter_Table SHALL treat the parameter as a root-level parameter
13. WHEN a Parameter_Definition has data_type OBJECT, THE Parameter_Table SHALL allow child Parameter_Definitions to reference the OBJECT parameter via parent_id, representing nested object properties
14. WHEN a Parameter_Definition has data_type ARRAY, THE Parameter_Table SHALL allow child Parameter_Definitions to reference the ARRAY parameter via parent_id, representing the element fields of the array
15. THE Parameter_Table SHALL derive the full Parameter_Path of a parameter by traversing the parent chain from root to leaf and joining names with dot notation (e.g., parent name "company" with child name "address" with grandchild name "city" produces path "company.address.city")
16. THE Parameter_Table SHALL enforce a maximum nesting depth of 5 levels (root level counts as level 1), and IF a POST request attempts to create a parameter that would exceed this depth, THEN THE Parameter_API SHALL return HTTP 400 with error code PARAMETER_MAX_DEPTH_EXCEEDED
17. THE Parameter_Table SHALL enforce that parameter names contain only alphanumeric characters, underscores, and hyphens (pattern: `^[a-zA-Z_][a-zA-Z0-9_-]*$`), and IF a POST or PUT request contains a name with invalid characters, THEN THE Parameter_API SHALL return HTTP 400 with error code PARAMETER_INVALID_NAME

### Requirement 2: 参数表 CRUD API

**User Story:** As a 模板设计者, I want 通过 API 创建、读取、更新和删除参数定义, so that 我可以灵活管理模板的参数表。

#### Acceptance Criteria

1. WHEN a POST request is sent to /api/templates/{templateId}/parameters with a valid Parameter_Definition including an optional parent_id, THE Parameter_API SHALL create the parameter and return HTTP 201 with the created ParameterDTO
2. WHEN a GET request is sent to /api/templates/{templateId}/parameters, THE Parameter_API SHALL return all Parameter_Definitions for the template as a nested tree structure, where each ParameterDTO includes a children array containing its child parameters, ordered by sort_order ascending at each level
3. WHEN a PUT request is sent to /api/parameters/{id} with updated fields, THE Parameter_API SHALL update the Parameter_Definition and return the updated ParameterDTO
4. WHEN a DELETE request is sent to /api/parameters/{id}, THE Parameter_API SHALL delete the Parameter_Definition and all its descendant parameters (via ON DELETE CASCADE) and return HTTP 204
5. IF a POST or PUT request contains a duplicate parameter name within the same parent scope (same template_id and parent_id), THEN THE Parameter_API SHALL return HTTP 409 with error code PARAMETER_DUPLICATE_NAME
6. IF a POST or PUT request sets parameter_type to DERIVED but expression_text is empty, THEN THE Parameter_API SHALL return HTTP 400 with error code PARAMETER_EXPRESSION_REQUIRED
7. WHEN a Parameter_Definition of type DERIVED is created or updated, THE Parameter_API SHALL validate the expression syntax via the Expression_Engine before saving
8. WHEN a POST or PUT request includes validation_rules, THE Parameter_API SHALL validate the validation_rules JSON structure: each key must be a recognized rule type (not_null, not_blank, min_length, max_length, min, max, pattern, enum_values, min_items, max_items, custom_message), and each value must match the expected data type for that rule
9. IF a POST or PUT request includes validation_rules with rule types incompatible with the parameter data_type (e.g., min_length for NUMBER, min for STRING), THEN THE Parameter_API SHALL return HTTP 400 with error code PARAMETER_VALIDATION_RULE_INCOMPATIBLE and specify the conflicting rule and data_type
10. IF a POST or PUT request includes validation_rules containing a pattern rule with an invalid regular expression, THEN THE Parameter_API SHALL return HTTP 400 with error code PARAMETER_INVALID_PATTERN and the regex syntax error message
11. IF a POST request specifies a parent_id that does not exist or belongs to a different template, THEN THE Parameter_API SHALL return HTTP 400 with error code PARAMETER_INVALID_PARENT
12. IF a POST request specifies a parent_id referencing a parameter whose data_type is not OBJECT or ARRAY, THEN THE Parameter_API SHALL return HTTP 400 with error code PARAMETER_PARENT_TYPE_INVALID, indicating that only OBJECT and ARRAY parameters can have children
13. WHEN a GET request is sent to /api/templates/{templateId}/parameters?flat=true, THE Parameter_API SHALL return all Parameter_Definitions as a flat list with each ParameterDTO including parent_id and the computed full Parameter_Path
14. WHEN a PUT request updates a parameter that has been concurrently modified by another user, THE Parameter_API SHALL detect the conflict via optimistic locking (version column) and return HTTP 409 with error code PARAMETER_CONCURRENT_MODIFICATION

### Requirement 3: 参数与模板占位符自动关联

**User Story:** As a 模板设计者, I want 系统自动扫描模板文件中的占位符并与参数表对比, so that 我能清楚看到哪些占位符缺少参数定义、哪些参数未被模板使用。

#### Acceptance Criteria

1. WHEN a POST request is sent to /api/templates/{templateId}/parameters/scan, THE Parameter_API SHALL scan the template .docx file for all Docxtemplater placeholders including simple variables, dot-notation object paths, and loop constructs, and return the list of placeholder names with their detected structure types
2. WHEN the scan completes, THE Parameter_API SHALL compare scanned placeholders against existing Parameter_Definitions (matching by full Parameter_Path) and return three categories: matched (placeholder has corresponding parameter at the correct tree path), unmatched_placeholders (placeholder exists in template but no parameter defined at that path), and unused_parameters (parameter defined but its Parameter_Path not used in template)
3. WHEN a POST request is sent to /api/templates/{templateId}/parameters/auto-create, THE Parameter_API SHALL create the full parameter tree hierarchy for all unmatched placeholders: for dot-notation paths (e.g., `company.name`), the API SHALL create intermediate OBJECT parent parameters as needed and the leaf parameter as STRING; for loop constructs (e.g., `{#items}{name}{/items}`), the API SHALL create the loop variable as ARRAY type and each inner field as a STRING child parameter
4. THE Parameter_API SHALL reuse the existing Docxtemplater variable scanning logic (currently in TemplateVariableService.extractVariableNames) by extracting it into a new TemplateScanService or into the ParameterService, extended to parse dot-notation paths and loop block syntax (`{#name}...{/name}`), before TemplateVariableService is removed
5. IF the template has no uploaded .docx file, THEN THE Parameter_API SHALL return an empty scan result with zero placeholders instead of an error
6. WHEN the scan encounters a nested dot-notation placeholder (e.g., `company.address.city`), THE Parameter_API SHALL parse the path segments and map each segment to the corresponding level in the parameter tree (root `company` as OBJECT → child `address` as OBJECT → leaf `city` as STRING)
7. WHEN the scan encounters a loop construct (e.g., `{#items}{name}{price}{/items}`), THE Parameter_API SHALL identify `items` as an ARRAY parameter and `name`, `price` as child parameters of `items`
8. WHEN the scan encounters nested loops (e.g., `{#orders}{#items}{name}{/items}{/orders}`), THE Parameter_API SHALL create the corresponding multi-level tree: `orders` as ARRAY → `items` as ARRAY child → `name` as STRING grandchild

### Requirement 4: 参数表低代码管理界面

**User Story:** As a 模板设计者, I want 一个低代码、所见即所得的可视化界面来管理参数表, so that 我无需编写任何代码、JSON 或技术表达式即可通过拖拽、点击和下拉选择高效地构建和维护多级嵌套的模板参数。

#### Acceptance Criteria

##### 4.1 基础布局与树表格

1. THE Workspace SHALL display a "参数表" tab that replaces the current "数据结构" tab as the primary parameter management interface
2. THE 参数表 tab SHALL adopt a two-panel layout: the left panel displays the parameter tree table editor, and the right panel displays the Preview_Panel for real-time JSON Schema, sample request body, and placeholder matching status
3. THE 参数表 tab SHALL display all Parameter_Definitions in a tree table (Tree Table) with columns: name (with indentation reflecting hierarchy depth), parameter_type (tag badge), data_type (dropdown selector), required (toggle switch), default_value (inline editable), description (inline editable), validation_rules (summary badge), and actions, referencing the schema editor patterns used in Swagger/OpenAPI Editor and Postman request body editor
4. WHEN a Parameter_Definition has data_type OBJECT, THE tree table SHALL display an expand/collapse toggle that reveals the child parameters nested under the OBJECT parameter with visual indentation
5. WHEN a Parameter_Definition has data_type ARRAY, THE tree table SHALL display an expand/collapse toggle that reveals the element field definitions nested under the ARRAY parameter with visual indentation
6. THE tree table SHALL display a connector line or indentation guide between parent and child parameters to visually communicate the hierarchy depth at each level
7. THE tree table SHALL display the full Parameter_Path (e.g., "company.address.city") as a tooltip or secondary label on each parameter row to help the user understand the mapping to template placeholders
8. THE 参数表 tab SHALL support "全部展开" and "全部折叠" toolbar buttons to expand or collapse all tree nodes at once

##### 4.2 内联编辑（零对话框操作）

9. WHEN the user clicks on the name cell of a parameter row, THE tree table SHALL activate Inline_Editing mode for that cell, allowing the user to type a new name and confirm with Enter or cancel with Esc
10. WHEN the user clicks on the data_type cell of a parameter row, THE tree table SHALL display an inline dropdown selector with options STRING, NUMBER, DATE, BOOLEAN, ARRAY, and OBJECT, applying the selection immediately without a dialog
11. WHEN the user clicks on the required cell of a parameter row, THE tree table SHALL toggle the required value via an inline switch control (on/off) without opening a dialog
12. WHEN the user clicks on the default_value cell of a parameter row, THE tree table SHALL activate Inline_Editing mode for that cell, displaying an input control appropriate to the data_type (text input for STRING, number input for NUMBER, date picker for DATE, toggle for BOOLEAN)
13. WHEN the user clicks on the description cell of a parameter row, THE tree table SHALL activate Inline_Editing mode for that cell, allowing the user to type a description and confirm with Enter or cancel with Esc
14. WHEN the user changes a parameter data_type from OBJECT or ARRAY to a leaf type (STRING, NUMBER, DATE, BOOLEAN), THE 参数表 tab SHALL display a confirmation dialog warning that all child parameters will be deleted

##### 4.3 智能引导与自动推荐

15. WHEN the user clicks "从模板扫描" button, THE 参数表 tab SHALL invoke the scan API and display a dialog showing matched, unmatched, and unused parameters in a tree structure view with an option to auto-create the full missing parameter hierarchy
16. WHEN auto-creating parameters from template scan results, THE 参数表 tab SHALL recommend data_type based on placeholder context: NUMBER for placeholders containing keywords such as "price", "amount", "count", "total", "qty", "quantity"; DATE for placeholders containing "date", "time", "created", "updated"; BOOLEAN for placeholders containing "is", "has", "enable", "active", "flag"; and STRING as the default for all other placeholders
17. WHEN the user clicks an "添加子参数" action button on an OBJECT or ARRAY parameter row, THE 参数表 tab SHALL show an inline form row nested under that parent parameter with parent_id automatically set, without requiring the user to understand or manually specify the parent_id value
18. WHEN the user clicks "添加参数" button at root level, THE 参数表 tab SHALL show an inline form row at the bottom of the root-level parameters for entering a new root parameter
19. THE 参数表 tab toolbar SHALL provide a "插入参数模板" dropdown menu containing predefined Parameter_Template options (including "地址" with OBJECT containing province, city, district, street, zipCode children; "联系人" with OBJECT containing name, phone, email children; "商品列表" with ARRAY containing name, price, quantity, subtotal children where subtotal is a DERIVED parameter)
20. WHEN the user selects a Parameter_Template from the dropdown, THE 参数表 tab SHALL insert the complete parameter tree structure at the current scope (root level or under the selected parent) with all child parameters, data_types, and default descriptions pre-filled

##### 4.4 实时预览面板

21. THE Preview_Panel SHALL display three tabs: "JSON Schema" showing the current parameter structure as a formatted JSON Schema document, "示例请求体" showing a sample JSON request body with example values for each parameter, and "占位符匹配" showing the matching status between parameters and template placeholders
22. WHEN the user adds, edits, or deletes any Parameter_Definition in the tree table, THE Preview_Panel SHALL update all three tabs within 500 milliseconds to reflect the current parameter structure
23. THE "JSON Schema" tab in the Preview_Panel SHALL render the parameter tree as a standard JSON Schema with type, required, properties (for OBJECT), items (for ARRAY), description, and validation constraints mapped from validation_rules
24. THE "示例请求体" tab in the Preview_Panel SHALL generate example values based on data_type (e.g., "string" for STRING, 0 for NUMBER, "2024-01-01" for DATE, false for BOOLEAN) and use default_value when defined, displaying the full nested JSON structure
25. THE "占位符匹配" tab in the Preview_Panel SHALL display a list of all template placeholders with status icons: green checkmark for matched (parameter exists), red cross for unmatched (no parameter defined), and orange warning for unused parameters (parameter defined but not in template)

##### 4.5 拖拽与排序

26. THE 参数表 tab SHALL support drag-and-drop reordering of parameters to adjust sort_order within the same parent scope (siblings only), with a visual drag handle icon on each row and a drop-target highlight indicator
27. WHEN the user drags a parameter row, THE tree table SHALL display a visual insertion line at the valid drop position and prevent cross-level drag operations by disabling drop targets outside the current parent scope

##### 4.6 右键菜单与快捷键

28. WHEN the user right-clicks on a parameter row, THE tree table SHALL display a Context_Menu with options: "复制参数" (copy the parameter and its children), "粘贴参数" (paste copied parameter as a sibling), "删除参数" (delete with confirmation), "添加同级参数" (add sibling), and "添加子参数" (add child, enabled only for OBJECT and ARRAY types)
29. WHEN the user presses Tab while a parameter row is focused, THE tree table SHALL create a new sibling parameter row below the current row in Inline_Editing mode
30. WHEN the user presses Enter while editing an inline field, THE tree table SHALL confirm the edit and move focus to the next editable field in the same row
31. WHEN the user presses Esc while editing an inline field, THE tree table SHALL cancel the edit and restore the previous value
32. THE 参数表 tab toolbar SHALL provide batch operation controls: a "全选" checkbox to select all visible parameters, a "批量删除" button to delete all selected parameters with a single confirmation dialog, and a "批量设置必填" toggle to set the required flag on all selected parameters at once
33. WHEN the user selects multiple parameters via checkboxes and clicks "批量删除", THE 参数表 tab SHALL display a confirmation dialog showing the count of parameters to be deleted (including descendant parameters) before executing the deletion

##### 4.7 校验规则可视化配置

34. WHEN the user clicks the validation_rules column or an "编辑校验规则" action button for a parameter, THE 参数表 tab SHALL display a structured rule configuration popover panel (not a full dialog) anchored to the parameter row, with form controls: toggles for not_null and not_blank, number inputs for min_length, max_length, min, max, min_items, max_items, text input for pattern, tag input for enum_values, and text input for custom_message
35. THE validation_rules configuration popover SHALL dynamically show only the rule types applicable to the parameter data_type: min_length, max_length, and pattern for STRING; min and max for NUMBER; min_items and max_items for ARRAY; not_null and not_blank for all types; enum_values for STRING and NUMBER
36. WHEN the user saves validation_rules from the configuration popover, THE 参数表 tab SHALL display a summary badge on the parameter row showing the count of active rules (e.g., "3 条规则")

##### 4.8 衍生参数低代码表达式编辑器

37. WHEN a parameter has parameter_type DERIVED, THE 参数表 tab SHALL display an expandable expression editor panel below the parameter row with two modes: "可视化模式" (Visual_Expression_Builder) and "高级模式" (code editor with syntax highlighting)
38. THE Visual_Expression_Builder SHALL provide a step-by-step expression construction interface: Step 1 select a source parameter from a dropdown listing all available REQUEST and DERIVED parameters, Step 2 select an operator from a categorized dropdown (arithmetic: +, -, *, /; comparison: >, <, ==, !=, >=, <=; string: concat, substring, toUpperCase, toLowerCase; date: addDays, formatDate; logical: and, or, not), Step 3 select a second operand (parameter dropdown or literal value input)
39. THE Visual_Expression_Builder SHALL support chaining multiple operations by displaying an "添加步骤" button that appends additional operator-operand pairs to the expression, rendering the full expression as a visual pipeline of connected blocks
40. WHEN the user constructs an expression in the Visual_Expression_Builder, THE expression editor SHALL generate the corresponding expression_text (JavaScript or Excel formula based on expression_type) and display the generated code in a read-only preview area below the visual builder
41. WHEN the user types a parameter name reference in the "高级模式" code editor, THE expression editor SHALL display an autocomplete dropdown listing all available parameter names (REQUEST and DERIVED) filtered by the typed prefix, allowing selection via arrow keys and Enter
42. THE expression editor SHALL provide a "测试" button that evaluates the expression with sample input values (from default_value or auto-generated examples) and displays the computed result inline

##### 4.9 覆盖率摘要

43. THE 参数表 tab SHALL display a coverage summary bar at the top showing Branch_Coverage, Loop_Coverage, Parameter_Coverage, and overall coverage percentage based on the latest test case execution results

### Requirement 5: 衍生参数表达式集成

**User Story:** As a 模板设计者, I want 在参数表中直接定义衍生参数的计算表达式, so that 我不需要在单独的"表达式"管理界面中操作。

#### Acceptance Criteria

1. WHEN a Parameter_Definition has parameter_type DERIVED, THE Expression_Engine SHALL evaluate the expression_text using the specified expression_type (JAVASCRIPT or EXCEL_FORMULA) during document generation
2. WHEN evaluating DERIVED parameters, THE Expression_Engine SHALL resolve dependencies by evaluating parameters in sort_order, making earlier parameters available as context for later expressions
3. IF a DERIVED parameter expression references a parameter name that does not exist in the Parameter_Table, THEN THE Parameter_Validation_Service SHALL return an error identifying the missing dependency
4. WHEN a user edits a DERIVED parameter expression in the 参数表 tab, THE Workspace SHALL provide a "测试" button that evaluates the expression with sample input values and displays the result
5. THE Expression_Engine SHALL support referencing both REQUEST and other DERIVED parameters by name within expression_text (e.g., `totalPrice * taxRate` where totalPrice is a REQUEST parameter and taxRate is another DERIVED parameter)
6. IF a DERIVED parameter expression creates a circular dependency (e.g., parameter A references parameter B which references parameter A), THEN THE Parameter_API SHALL detect the cycle during create or update and return HTTP 400 with error code PARAMETER_CIRCULAR_DEPENDENCY, listing the cycle path

### Requirement 6: 文档生成时参数验证

**User Story:** As a API 调用者, I want 文档生成 API 在执行前验证传入参数, so that 我能在生成前得到明确的参数错误提示。

#### Acceptance Criteria

1. WHEN a document generation request is received, THE Parameter_Validation_Service SHALL validate all provided parameters against the Parameter_Table definitions before rendering the template
2. IF a required REQUEST parameter is missing from the request and has no default_value, THEN THE Parameter_Validation_Service SHALL return HTTP 400 with error code PARAMETER_MISSING_REQUIRED and the parameter name
3. IF a parameter value does not match the expected data_type, THEN THE Parameter_Validation_Service SHALL return HTTP 400 with error code PARAMETER_TYPE_MISMATCH with the parameter name and expected type
4. WHEN a required REQUEST parameter is missing but has a default_value defined, THE Parameter_Validation_Service SHALL use the default_value as the parameter value
5. WHEN the request contains parameters not defined in the Parameter_Table, THE Parameter_Validation_Service SHALL ignore the extra parameters and proceed with generation
6. WHEN all REQUEST parameters pass validation, THE Parameter_Validation_Service SHALL evaluate all DERIVED parameters in sort_order and merge the results into the data context
7. IF a template has zero Parameter_Definitions, THEN THE Parameter_Validation_Service SHALL skip parameter validation and pass the raw request parameters directly to the template rendering step
8. IF a DERIVED parameter expression evaluation fails at generation time, THEN THE Parameter_Validation_Service SHALL return HTTP 400 with error code PARAMETER_EXPRESSION_EVALUATION_FAILED and the parameter name
9. THE DocumentGeneratorService SHALL execute a simplified three-step pipeline: validate parameters → evaluate DERIVED parameters → render template, without invoking any external data source aggregation
10. WHEN a Parameter_Definition has validation_rules defined, THE Parameter_Validation_Service SHALL validate the parameter value against each rule in the validation_rules configuration after data_type validation passes
11. IF a parameter value violates a not_null rule (value is null), THEN THE Parameter_Validation_Service SHALL return HTTP 400 with error code PARAMETER_VALIDATION_FAILED, the parameter name, and the rule type "not_null"
12. IF a parameter value violates a not_blank rule (value is empty or whitespace-only string), THEN THE Parameter_Validation_Service SHALL return HTTP 400 with error code PARAMETER_VALIDATION_FAILED, the parameter name, and the rule type "not_blank"
13. IF a STRING parameter value violates min_length or max_length rules, THEN THE Parameter_Validation_Service SHALL return HTTP 400 with error code PARAMETER_VALIDATION_FAILED, the parameter name, the rule type, the constraint value, and the actual length
14. IF a NUMBER parameter value violates min or max rules, THEN THE Parameter_Validation_Service SHALL return HTTP 400 with error code PARAMETER_VALIDATION_FAILED, the parameter name, the rule type, the constraint value, and the actual value
15. IF a STRING parameter value violates a pattern rule (does not match the regular expression), THEN THE Parameter_Validation_Service SHALL return HTTP 400 with error code PARAMETER_VALIDATION_FAILED, the parameter name, the rule type "pattern", and the expected pattern
16. IF a parameter value violates an enum_values rule (value not in the allowed list), THEN THE Parameter_Validation_Service SHALL return HTTP 400 with error code PARAMETER_VALIDATION_FAILED, the parameter name, the rule type "enum_values", and the list of allowed values
17. IF an ARRAY parameter value violates min_items or max_items rules, THEN THE Parameter_Validation_Service SHALL return HTTP 400 with error code PARAMETER_VALIDATION_FAILED, the parameter name, the rule type, the constraint value, and the actual item count
18. WHEN a validation_rules violation has a custom_message defined, THE Parameter_Validation_Service SHALL use the custom_message as the error detail instead of the default error message
19. WHEN multiple parameter values fail validation_rules checks, THE Parameter_Validation_Service SHALL collect all validation errors and return them in a single HTTP 400 response with error code PARAMETER_VALIDATION_FAILED and an errors array containing each violation detail
20. WHEN validating a parameter of data_type OBJECT, THE Parameter_Validation_Service SHALL recursively validate each child parameter defined under the OBJECT against the corresponding nested field in the request JSON object
21. WHEN validating a parameter of data_type ARRAY, THE Parameter_Validation_Service SHALL validate each element in the request JSON array against the child parameter definitions of the ARRAY parameter, applying validation_rules to every element
22. WHEN a nested parameter validation fails, THE Parameter_Validation_Service SHALL include the full Parameter_Path (e.g., "company.address.city" or "items[2].price") in the error detail to precisely identify the failing field
23. WHEN validating an ARRAY parameter whose child parameters include an OBJECT type, THE Parameter_Validation_Service SHALL recursively validate the nested OBJECT structure within each array element, supporting arbitrary nesting depth up to the configured maximum

### Requirement 7: API 参数文档自动生成

**User Story:** As a API 调用者, I want 查看模板的参数文档, so that 我知道调用该模板 API 时需要传入哪些参数。

#### Acceptance Criteria

1. WHEN a GET request is sent to /api/templates/{templateId}/parameter-schema, THE Parameter_API SHALL return a JSON object describing all REQUEST parameters in a nested tree structure with their name, data_type, required, default_value, description, validation_rules, and children (for OBJECT and ARRAY types)
2. THE Parameter_Schema response SHALL include a sample request body showing the expected nested JSON structure with example values for each parameter, reflecting the full hierarchy (e.g., `{"company": {"name": "Acme", "address": {"city": "Beijing"}}, "items": [{"name": "Widget", "price": 9.99}]}`)
3. WHEN the template status is ACTIVE, THE Parameter_Schema SHALL be accessible via API Key authentication, consistent with the existing generate API access control mechanism
4. THE Parameter_Schema SHALL include metadata: template name, template version, total parameter count (including nested), and required parameter count
5. WHEN a REQUEST parameter has validation_rules defined, THE Parameter_Schema SHALL include a constraints section for that parameter listing each active rule with its configuration (e.g., min_length: 1, max_length: 100, pattern: "^[A-Z].*", enum_values: ["A", "B", "C"])
6. WHEN a parameter has data_type OBJECT, THE Parameter_Schema SHALL nest its child parameters under a "properties" key, consistent with JSON Schema conventions
7. WHEN a parameter has data_type ARRAY, THE Parameter_Schema SHALL describe the element structure under an "items" key containing the child parameter definitions, consistent with JSON Schema conventions

### Requirement 8: 基于测试用例的覆盖率检查

**User Story:** As a 模板设计者, I want 覆盖率检查基于测试用例的实际执行结果来衡量分支、循环和参数的覆盖情况, so that 我能确保模板经过全量测试后再提交审核发布。

#### Acceptance Criteria

1. WHEN a coverage check is performed, THE Coverage_Report SHALL execute all Test_Case records associated with the template and track which template structures each test case exercises
2. WHEN executing test cases for coverage analysis, THE Coverage_Report SHALL compute Branch_Coverage as: (conditional branches triggered in both true and false paths across all test cases) / (total conditional branches in template × 2) × 100%, where each `{#if condition}...{/if}` block counts as two branches (true path and false path)
3. WHEN executing test cases for coverage analysis, THE Coverage_Report SHALL compute Loop_Coverage as: (loop constructs exercised with both empty-array and non-empty-array inputs across all test cases) / (total loop constructs in template × 2) × 100%, where each `{#loop array}...{/loop}` block counts as two scenarios (empty array and non-empty array)
4. WHEN executing test cases for coverage analysis, THE Coverage_Report SHALL compute Parameter_Coverage as: (Parameter_Definitions that receive a non-null value in at least one test case) / (total Parameter_Definitions) × 100%
5. THE Coverage_Report SHALL compute an overall coverage percentage as the weighted average of Branch_Coverage, Loop_Coverage, and Parameter_Coverage, with equal weight for each applicable dimension
6. THE Coverage_Report SHALL include an uncovered_items list that explicitly enumerates: uncovered conditional branches (condition name and missing path true or false), uncovered loop scenarios (loop variable name and missing scenario empty or non-empty), and uncovered parameters (parameter names not used in any test case)
7. IF the template contains zero conditional branches and zero loop constructs, THEN THE Coverage_Report SHALL compute overall coverage based solely on Parameter_Coverage
8. WHEN the coverage check detects a DERIVED parameter whose expression references undefined parameters, THE Coverage_Report SHALL include a warning for expression dependency errors
9. THE Coverage_Report SHALL retain the existing threshold mechanism and belowThreshold flag for template activation gating
10. WHEN a template review is submitted, THE TemplateReviewService SHALL include the Coverage_Report in the review context, displaying Branch_Coverage, Loop_Coverage, Parameter_Coverage, and overall coverage percentage to the reviewer
11. WHEN a reviewer evaluates a template with overall coverage below 100%, THE Workspace SHALL display a warning indicating incomplete test coverage, and the reviewer SHALL have the authority to reject the template based on insufficient coverage

### Requirement 9: 数据源彻底移除与数据迁移

**User Story:** As a 系统管理员, I want 现有模板的表达式和变量配置自动迁移到新参数表，同时彻底移除外部数据源功能, so that 系统保持独立性且升级后现有模板继续正常工作。

#### Acceptance Criteria

1. THE Migration_Service SHALL provide a Flyway migration script that creates the template_parameters table and drops the data_sources table
2. THE Migration_Service SHALL provide a data migration script that converts existing TemplateVariable records into Parameter_Definitions: bound variables with binding_source "EXPRESSION" become DERIVED parameters, all other variables become REQUEST parameters
3. WHEN migrating a TemplateVariable that was bound to an Expression, THE Migration_Service SHALL copy the expression_text and expression_type from the corresponding Expression record into the new Parameter_Definition
4. THE Migration_Service SHALL drop the data_sources table via Flyway migration after data migration is complete, removing all external data source configurations permanently
5. THE Migration_Service SHALL drop the expressions table via Flyway migration after all Expression data has been migrated into the template_parameters table as DERIVED parameters, since the standalone expressions table is no longer needed
6. THE Migration_Service SHALL drop the template_variables table via Flyway migration after all TemplateVariable data has been migrated into the template_parameters table, since the standalone template_variables table is replaced by the unified parameter table
7. IF the migration encounters a TemplateVariable with no matching Expression for its binding, THEN THE Migration_Service SHALL create a REQUEST type Parameter_Definition and log a warning
8. IF a template had DataSource configurations before migration, THEN THE Migration_Service SHALL log a warning listing the removed data source names for audit purposes
9. THE Migration_Service SHALL migrate standalone Expression records (not bound to any TemplateVariable) as DERIVED parameters in the template_parameters table, preserving their expression_text, expression_type, and execution_order as sort_order

### Requirement 10: 旧架构代码彻底移除

**User Story:** As a 开发者, I want 彻底移除所有外部数据源、独立表达式管理和模板变量绑定相关的后端和前端代码, so that 代码库保持简洁且不包含被参数表替代的旧功能模块。

#### Acceptance Criteria

1. THE System SHALL remove the following backend Java files: DataSourceController, DataSourceCrudService, DataAggregationService, HttpApiDataSourceService, DatabaseDataSourceService, InternalSystemDataSourceService, DataSourceCacheService, DataSource entity, DataSourceType enum, DataSourceRepository, DataSourceDTO, DataSourceHealthDTO, CreateDataSourceRequest, and UpdateDataSourceRequest
2. THE System SHALL remove all backend test files related to data sources: DataSourceCrudServiceTest, DatabaseDataSourceServiceTest, HttpApiDataSourceServiceTest, InternalSystemDataSourceServiceTest, DataSourceCacheServiceTest, DataSourceErrorPropagationPropertyTest, DataAggregationServiceTest, and update RedisIntegrationTest to remove the DataSourceCacheService dependency
3. THE System SHALL remove the following frontend files: the data-sources API module (frontend/src/api/data-sources.ts), the data-sources view directory (frontend/src/views/data-sources/) including DataSourceFormDialog, KeyValueEditor, PipelineVisualization, TransformRulesEditor, and Index components
4. THE System SHALL remove all DataSource references from the template workspace store, including the dataSources state array, the refreshDataSources action, and the related warning field
5. THE System SHALL remove the DataSource management section from the DataStructureTab component in the template workspace
6. THE System SHALL update the DocumentGeneratorService to remove the DataAggregationService dependency and the dataSourceCb circuit breaker, simplifying executePipeline to: validate parameters → evaluate DERIVED parameters → render template
7. THE System SHALL update the CompositeGeneratorService to remove the DataAggregationService dependency and the dataSourceCb circuit breaker, applying the same simplified pipeline as DocumentGeneratorService
8. THE System SHALL update the TemplatePreviewService to remove the DataAggregationService dependency, using parameter-based data context instead of data source aggregation
9. THE System SHALL update the DataPipelineServiceImpl to remove the DataAggregationService dependency, or remove DataPipelineServiceImpl and DataPipelineService entirely if the pipeline concept is no longer needed after data source removal
10. THE System SHALL remove the dataSourceCircuitBreaker bean from Resilience4jConfig (the DATASOURCE_CB constant and the dataSourceCircuitBreaker @Bean method)
11. THE System SHALL remove the data source health endpoint from DashboardController and DashboardService
12. THE System SHALL update the CoverageCheckService to remove DataSource and DataSourceRepository dependencies, removing the computeUnusedDataSourceFields logic that references data sources
13. THE System SHALL update the CompositeImportExportService to remove DataSourceRepository dependency and the importDataSources/toMaskedDataSourceMap/maskCredentialFields methods related to data source export/import
14. THE System SHALL update the MigrationService to remove DataSourceRepository dependency and the migrateDataSources method, since data sources are no longer part of the system
15. THE System SHALL update the TemplateImportExportService to remove DataSourceRepository dependency and the data source export/import logic (toDataSourceExport, data source import loop), since data sources are no longer part of the system
16. THE System SHALL remove the frontend data-sources route from router/index.ts (path: 'data-sources', name: 'DataSources')
17. THE System SHALL remove the data-sources navigation entry from MainLayout.vue (the '/data-sources': 'nav.dataSources' mapping)
18. THE System SHALL update the ExportImportTab.vue to remove the updateDataSource import from '@/api/data-sources' and related credential configuration logic
19. THE System SHALL relocate KeyValueEditor.vue from frontend/src/views/data-sources/ to a shared location (e.g., frontend/src/components/) before removing the data-sources directory, since SegmentArrangementTab.vue depends on it
20. THE System SHALL update the useWorkflowSteps composable to replace the data-source-based step completion check (store.dataSources.length > 0) with a parameter-table-based check
21. THE System SHALL update all frontend test files that mock the data-sources API module to remove those mock declarations
22. THE System SHALL remove data source related i18n keys from all locale files (en-US, zh-CN, zh-TW), including both the top-level "dataSource" section and the workspace-level "workspace.dataSource" section and the dashboard "dataSourceHealth" key
23. THE System SHALL remove the ExpressionController, ExpressionCrudService, Expression entity, ExpressionRepository, and related DTOs (CreateExpressionRequest, UpdateExpressionRequest, ExpressionDTO) since expression management is now integrated into the parameter table CRUD API and no longer needs a standalone API
24. THE System SHALL remove the frontend expressions API module (frontend/src/api/expressions.ts) and the ExpressionFormDialog component (frontend/src/views/templates/components/ExpressionFormDialog.vue), since expression editing is now embedded in the parameter table UI
25. THE System SHALL remove the expressions state array, refreshExpressions action, and related imports from the templateWorkspace store
26. THE System SHALL remove the Expressions section from the DataStructureTab component (which will be replaced entirely by the new 参数表 tab)
27. THE System SHALL remove the ExpressionPanel component (frontend/src/views/templates/components/ExpressionPanel.vue) and its related test files, and update Detail.vue to remove the ExpressionPanel import and the "expressions" tab pane
28. THE System SHALL remove expression-related i18n keys from all locale files (en-US, zh-CN, zh-TW), including the "expression" section and the "workspace.expression" section
29. THE System SHALL remove the TemplateVariableController, TemplateVariableService, TemplateVariable entity, TemplateVariableRepository, and related DTOs (TemplateVariableDTO, BindVariableRequest) since template variables are replaced by the unified parameter table
30. THE System SHALL remove all backend test files related to expressions and template variables: ExpressionCrudServiceTest, ExpressionEngineTest (if expression-specific), and TemplateVariableService-related tests
31. THE System SHALL remove the DataAggregationServiceTest and DataPipelineServiceImplTest backend test files

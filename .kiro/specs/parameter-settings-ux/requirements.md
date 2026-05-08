# Requirements Document

## Introduction

本功能对模板参数设置页面（ParameterTableTab）进行全面的 UX 重新设计，目标是将当前传统的平铺表格+对话框编辑模式，升级为现代化、低代码、所见即所得的参数管理体验。

### 当前问题分析

基于对现有代码的深入分析，当前参数设置页面存在以下 UX 痛点：

1. **编辑体验割裂**：点击参数名、默认值、描述等字段时弹出 `el-dialog` 对话框编辑（ParameterTreeTable.vue 第 120-135 行），打断用户心流，操作步骤多
2. **参数模板功能不直观**：ParameterTemplateMenu 的"插入参数模板"按钮含义不清，用户不理解其用途，且仅创建父节点不创建子参数 — 决定移除此功能
3. **拖拽排序缺失**：虽然需求文档（Requirement 4.26-4.27）要求支持拖拽排序，但当前 ParameterTreeTable 未实现任何拖拽功能
4. **校验规则入口隐蔽**：ValidationRulesPopover 存在但未在 ParameterTreeTable 中集成（表格仅显示规则数量 badge，无法直接点击打开 popover）
5. **衍生参数表达式编辑器未集成**：DerivedExpressionEditor 组件已实现但未在 ParameterTreeTable 中展示（DERIVED 参数行下方无展开区域）
6. **预览面板更新不够实时**：ParameterPreviewPanel 的占位符匹配 tab 需要手动点击按钮触发扫描，非自动同步
7. **批量操作效率低**：批量删除逐个调用 `deleteParameter` API（ParameterTableTab.vue 第 170-175 行），无批量 API
8. **新增参数名称固定**：添加参数时硬编码名称 `new_param` / `new_child`，用户必须再次编辑改名
9. **右键菜单的复制粘贴功能不完整**：paste 操作仅触发 `addSibling`，未真正复制参数的完整属性
10. **缺少 JSON 导入功能**：用户无法从已有 JSON 数据快速生成参数结构

### 设计目标

> **与 template-parameter-redesign spec 的关系**：template-parameter-redesign spec 定义了参数表的完整数据模型、CRUD API、前端界面等需求（Req 4.1-4.43），其前端任务已标记完成，但实际代码中以下功能未完整实现：拖拽排序、ValidationRulesPopover 集成、DerivedExpressionEditor 集成、完整的右键菜单复制粘贴、智能命名等。本 spec 聚焦于补全这些 UX 缺口并新增 JSON 导入、快速添加栏、批量 API、撤销重做等增强功能。

> **移除"插入参数模板"功能**：现有的 ParameterTemplateMenu 组件（地址/联系人/商品列表下拉菜单）功能不直观、实现不完整，本 spec 将移除该组件及其工具栏按钮，用 JSON 导入功能替代其"快速创建参数结构"的场景。

- **极致低代码**：用户通过拖拽、点击、下拉选择完成 90% 以上的参数配置，几乎不需要手动输入
- **零对话框编辑**：所有字段直接在表格行内编辑，消除弹窗打断
- **智能推断**：从模板扫描、JSON 导入两种方式自动生成参数结构
- **实时反馈**：编辑即预览，所有变更立即反映在右侧预览面板

## Glossary

- **Parameter_Settings_Page**: 模板工作区中的"参数表"标签页（ParameterTableTab），用于管理模板的所有参数定义
- **Inline_Cell_Editor**: 直接在树表格单元格内激活的编辑控件，用户点击单元格即可编辑，按 Enter 确认、Esc 取消，无需打开对话框
- **Smart_Name_Generator**: 新增参数时根据上下文自动生成有意义的参数名（如在 ARRAY 类型下添加子参数时自动命名为 `item_1`，在 OBJECT 下自动命名为 `field_1`），避免硬编码 `new_param`
- **JSON_Import_Engine**: 从用户粘贴的 JSON 数据自动推断并生成完整参数树结构的引擎，包括数据类型推断和嵌套层级构建
- **Drag_Sort_Handler**: 参数行的拖拽排序处理器，支持同级参数之间的拖拽重排，通过视觉拖拽手柄和插入线指示器引导用户操作
- **Batch_API**: 支持一次请求处理多个参数操作（批量删除、批量更新）的后端 API 端点，替代当前逐个调用的方式
- **Quick_Add_Bar**: 参数表底部的快速添加栏，用户可直接输入参数名并按 Enter 创建，类似 Notion 的"新建行"体验
- **Preview_Panel**: 参数表右侧的实时预览面板，包含 JSON Schema、示例请求体、占位符匹配三个标签页
- **Keyboard_Navigation**: 键盘导航系统，支持 Tab 切换字段、Enter 确认并移动到下一行、方向键在行间移动
- **Parameter_Tree_Table**: 以树形结构展示参数层级关系的表格组件，支持展开/折叠、内联编辑、拖拽排序
- **Undo_Redo_Stack**: 参数编辑的撤销/重做栈，记录用户的每次操作，支持 Ctrl+Z 撤销和 Ctrl+Shift+Z 重做

## Requirements

### Requirement 1: 真正的内联单元格编辑（消除对话框）

**User Story:** As a 模板设计者, I want 直接在表格单元格内编辑参数的所有字段, so that 我无需在弹出对话框中操作，编辑体验流畅不被打断。

#### Acceptance Criteria

1. WHEN the user clicks on the name cell of a parameter row, THE Inline_Cell_Editor SHALL activate a text input directly within the cell (not a dialog), pre-filled with the current name value, with the text selected for immediate overwrite
2. WHEN the user clicks on the default_value cell of a parameter row, THE Inline_Cell_Editor SHALL activate a type-appropriate input control within the cell: text input for STRING, number spinner for NUMBER, date picker for DATE, toggle switch for BOOLEAN
3. WHEN the user clicks on the description cell of a parameter row, THE Inline_Cell_Editor SHALL activate a text input directly within the cell, supporting multi-line input via Shift+Enter
4. WHEN the user presses Enter while editing an inline cell, THE Inline_Cell_Editor SHALL confirm the edit, save the value via the update API, and move focus to the next editable cell in the same row (left to right: name → defaultValue → description), skipping non-text cells (dataType dropdown and required toggle are already inline controls that do not use Inline_Cell_Editor)
5. WHEN the user presses Esc while editing an inline cell, THE Inline_Cell_Editor SHALL cancel the edit and restore the previous value without calling the update API
6. WHEN the user presses Tab while editing an inline cell, THE Inline_Cell_Editor SHALL confirm the current edit and move focus to the same field in the next sibling row (vertical navigation)
7. WHEN the user clicks outside an active Inline_Cell_Editor, THE Inline_Cell_Editor SHALL confirm the edit and deactivate the editing mode
8. THE Parameter_Settings_Page SHALL NOT display any el-dialog component for editing parameter field values (name, defaultValue, description)
9. IF the update API call fails after an inline edit is confirmed, THE Inline_Cell_Editor SHALL revert the cell to its previous value and display an error toast message with the API error details

### Requirement 2: 智能参数名称生成

**User Story:** As a 模板设计者, I want 新增参数时系统自动生成有意义的名称, so that 我不需要每次都手动修改 "new_param" 这样的占位名称。

#### Acceptance Criteria

1. WHEN the user clicks "添加参数" at root level, THE Smart_Name_Generator SHALL create a parameter with name "param_1" (incrementing the number if "param_1" already exists to "param_2", "param_3", etc.) and immediately activate Inline_Cell_Editor on the name cell
2. WHEN the user clicks "添加子参数" on an OBJECT type parameter, THE Smart_Name_Generator SHALL create a child parameter with name "field_1" (incrementing if duplicates exist) and immediately activate Inline_Cell_Editor on the name cell
3. WHEN the user clicks "添加子参数" on an ARRAY type parameter, THE Smart_Name_Generator SHALL create a child parameter with name "item_1" (incrementing if duplicates exist) and immediately activate Inline_Cell_Editor on the name cell
4. WHEN the user adds a sibling parameter via context menu or Tab key, THE Smart_Name_Generator SHALL generate a name following the same pattern as the sibling's parent scope (root: "param_N", OBJECT child: "field_N", ARRAY child: "item_N")

### Requirement 3: JSON 导入自动生成参数结构

**User Story:** As a 模板设计者, I want 粘贴一段 JSON 数据即可自动生成对应的参数树结构, so that 我可以从已有的 API 响应或数据样本快速构建参数定义，无需逐个手动创建。

#### Acceptance Criteria

1. WHEN the user clicks "从 JSON 导入" button in the toolbar, THE Parameter_Settings_Page SHALL display a dialog with a JSON text area for the user to paste JSON data
2. WHEN the user pastes valid JSON and clicks "导入", THE JSON_Import_Engine SHALL parse the JSON structure and generate a complete parameter tree: top-level keys become root parameters, nested objects become OBJECT parameters with children, arrays become ARRAY parameters with children inferred from the first element
3. WHEN the JSON_Import_Engine encounters a JSON value of type string, THE JSON_Import_Engine SHALL create a STRING parameter; for number values, a NUMBER parameter; for boolean values, a BOOLEAN parameter; for null values, a STRING parameter with required=false
4. WHEN the JSON_Import_Engine encounters a JSON object, THE JSON_Import_Engine SHALL create an OBJECT parameter and recursively create child parameters for each key-value pair
5. WHEN the JSON_Import_Engine encounters a JSON array with at least one object element, THE JSON_Import_Engine SHALL create an ARRAY parameter and create child parameters based on the keys of the first element, using the union of all element keys if elements have different structures
6. IF the user pastes invalid JSON, THEN THE JSON_Import_Engine SHALL display an inline error message below the text area indicating the parse error position and reason
7. THE JSON_Import_Engine SHALL respect the maximum nesting depth of 5 levels, and IF the JSON structure exceeds 5 levels, THEN THE JSON_Import_Engine SHALL flatten deeper levels to STRING type with a warning message
8. IF the user pastes an empty JSON object `{}` or empty array `[]`, THEN THE JSON_Import_Engine SHALL display an inline warning "JSON 数据为空，无法生成参数" and not create any parameters
9. IF the JSON_Import_Engine encounters a JSON array with only primitive elements (e.g., `[1, 2, 3]`), THEN THE JSON_Import_Engine SHALL create an ARRAY parameter with no children and set a description noting the element type

### Requirement 4: 拖拽排序

> **实现注意**：el-table 不原生支持行拖拽，需要引入 `sortablejs` 或 `vuedraggable` 库。

**User Story:** As a 模板设计者, I want 通过拖拽参数行来调整参数的显示顺序, so that 我可以直观地组织参数的排列，无需手动输入排序数字。

#### Acceptance Criteria

1. THE Parameter_Tree_Table SHALL display a drag handle icon (grip dots) on the left side of each parameter row, visible on hover
2. WHEN the user drags a parameter row by the drag handle, THE Drag_Sort_Handler SHALL display a visual insertion line at the valid drop position between sibling rows within the same parent scope
3. WHEN the user drops a parameter row at a new position within the same parent scope, THE Drag_Sort_Handler SHALL update the sort_order of all affected sibling parameters via the update API and re-render the tree table to reflect the new order
4. WHEN the user attempts to drag a parameter row to a different parent scope (cross-level drag), THE Drag_Sort_Handler SHALL disable the drop target and display a "not allowed" cursor to prevent cross-level reordering
5. WHEN the user drops a parameter row, THE Drag_Sort_Handler SHALL recalculate sort_order values for all siblings in the affected scope as sequential integers (0, 1, 2, ...) and send batch update requests via the Batch_API defined in Requirement 9 (or individual update calls if batch API is not yet available)

### Requirement 5: 校验规则可视化配置集成

**User Story:** As a 模板设计者, I want 直接在参数行上点击即可打开校验规则配置面板, so that 我无需寻找隐藏的入口来配置参数的校验规则。

#### Acceptance Criteria

1. WHEN the user clicks the validation_rules badge or the "—" placeholder in the validation_rules column of a parameter row, THE Parameter_Tree_Table SHALL display the ValidationRulesPopover anchored to the clicked cell, pre-loaded with the parameter's current validation_rules and data_type
2. WHEN the user saves validation_rules from the ValidationRulesPopover, THE Parameter_Tree_Table SHALL call the update API with the new validation_rules and refresh the badge to show the updated rule count
3. THE ValidationRulesPopover SHALL dynamically show only the rule types applicable to the parameter data_type as defined in the compatibility matrix: min_length, max_length, pattern, not_blank for STRING; min, max for NUMBER; min_items, max_items for ARRAY; not_null for all types; enum_values for STRING and NUMBER
4. WHEN the parameter data_type changes while the ValidationRulesPopover is open, THE ValidationRulesPopover SHALL immediately update the visible rule fields to match the new data_type

### Requirement 6: 衍生参数表达式编辑器集成

**User Story:** As a 模板设计者, I want 在 DERIVED 类型参数行下方直接展开表达式编辑器, so that 我可以在参数表内完成表达式配置，无需切换到其他界面。

#### Acceptance Criteria

1. WHEN a parameter row has parameter_type DERIVED, THE Parameter_Tree_Table SHALL display an expandable expression editor area below the parameter row, showing the DerivedExpressionEditor component with the parameter's current expression_text and expression_type
2. WHEN the user changes a parameter's parameter_type from REQUEST to DERIVED, THE Parameter_Tree_Table SHALL automatically expand the expression editor area below the row and focus the expression input
3. WHEN the user edits the expression in the DerivedExpressionEditor (either visual mode or advanced mode), THE Parameter_Tree_Table SHALL save the expression_text and expression_type via the update API when the user clicks outside the editor or presses a save shortcut
4. THE DerivedExpressionEditor SHALL list all available REQUEST and DERIVED parameters (excluding the current parameter) in the parameter dropdown for expression construction
5. WHEN the user clicks the "测试" button in the DerivedExpressionEditor, THE DerivedExpressionEditor SHALL evaluate the expression using sample values from the parameter tree (default_value or auto-generated examples) and display the computed result inline

### Requirement 7: 实时预览面板自动同步

**User Story:** As a 模板设计者, I want 右侧预览面板在我编辑参数时自动更新, so that 我能即时看到参数变更对 JSON Schema、示例请求体和占位符匹配的影响。

#### Acceptance Criteria

1. WHEN the user adds, edits, or deletes any parameter in the tree table, THE Preview_Panel SHALL update the "JSON Schema" tab and "示例请求体" tab within 500 milliseconds to reflect the current parameter structure, without requiring manual refresh (these tabs use local computed properties from the parameter store, no API call needed)
2. THE Preview_Panel SHALL automatically trigger a placeholder scan API call when the parameter structure changes (debounced by 2 seconds after the last edit) and update the "占位符匹配" tab with the latest matching status; this is a separate API call to POST /api/templates/{templateId}/parameters/scan; IF a previous scan request is still in progress when a new debounced scan triggers, THE Preview_Panel SHALL cancel the previous request before initiating the new one
3. WHEN the Preview_Panel "占位符匹配" tab shows unmatched placeholders, THE Preview_Panel SHALL display an "一键创建缺失参数" button that invokes the auto-create API to generate parameters for all unmatched placeholders
4. THE Preview_Panel SHALL display a visual diff indicator (highlight or animation) on the changed sections when the JSON Schema or sample body updates, helping the user identify what changed

### Requirement 8: 键盘导航与快捷键

> **实现注意**：Undo/Redo 需要在前端维护操作栈，undo delete 需要在删除前缓存完整参数数据（含子参数），因为 DELETE API 返回 204 无 body。

**User Story:** As a 模板设计者, I want 使用键盘快捷键高效操作参数表, so that 我可以像使用电子表格一样快速编辑参数，无需频繁使用鼠标。

#### Acceptance Criteria

1. WHEN the user presses the Up Arrow or Down Arrow key while a parameter row is focused (not in edit mode), THE Keyboard_Navigation SHALL move focus to the previous or next visible parameter row
2. WHEN the user presses Ctrl+Z, THE Undo_Redo_Stack SHALL undo the last parameter operation (add, edit, delete, reorder) and restore the previous state by calling the appropriate API
3. WHEN the user presses Ctrl+Shift+Z, THE Undo_Redo_Stack SHALL redo the last undone operation
4. WHEN the user presses Delete key while a parameter row is focused (not in edit mode), THE Parameter_Settings_Page SHALL display a confirmation dialog and delete the parameter upon confirmation
5. WHEN the user presses Ctrl+D while a parameter row is focused, THE Parameter_Settings_Page SHALL duplicate the parameter (and its children if OBJECT or ARRAY) as a sibling with an auto-incremented name suffix
6. THE Undo_Redo_Stack SHALL maintain a maximum of 50 operations; when the stack exceeds this limit, the oldest operations SHALL be discarded

### Requirement 9: 批量操作 API 优化

**User Story:** As a 模板设计者, I want 批量删除和批量更新参数时系统一次性完成操作, so that 批量操作不会因为逐个 API 调用而缓慢或部分失败。

#### Acceptance Criteria

1. WHEN a POST request is sent to /api/templates/{templateId}/parameters/batch-delete with a list of parameter IDs, THE Batch_API SHALL delete all specified parameters (and their descendants via CASCADE) in a single transaction and return HTTP 204
2. WHEN a POST request is sent to /api/templates/{templateId}/parameters/batch-update with a list of objects each containing parameter ID, version (required for optimistic locking), and field updates, THE Batch_API SHALL update all specified parameters in a single transaction and return HTTP 200 with the updated ParameterDTO list
3. IF any parameter ID in a batch-delete request does not exist or belongs to a different template, THEN THE Batch_API SHALL return HTTP 400 with error code PARAMETER_BATCH_INVALID_IDS and list the invalid IDs
4. IF any parameter update in a batch-update request fails validation (e.g., duplicate name, invalid data_type), THEN THE Batch_API SHALL reject the entire batch and return HTTP 400 with error code PARAMETER_BATCH_VALIDATION_FAILED and list all validation errors
5. THE Parameter_Settings_Page SHALL use the batch-delete API when the user clicks "批量删除" instead of calling deleteParameter individually for each selected parameter

### Requirement 10: 快速添加栏

**User Story:** As a 模板设计者, I want 在参数表底部有一个快速添加栏, so that 我可以像在 Notion 中一样快速连续添加多个参数，无需反复点击"添加参数"按钮。

#### Acceptance Criteria

1. THE Parameter_Settings_Page SHALL display a Quick_Add_Bar at the bottom of the root-level parameter list, showing a text input with placeholder text "输入参数名，按 Enter 添加..."
2. WHEN the user types a parameter name in the Quick_Add_Bar and presses Enter, THE Quick_Add_Bar SHALL create a new root-level REQUEST parameter with the typed name and STRING as default data_type, then clear the input for the next entry
3. WHEN the user types a parameter name containing a dot (e.g., "company.name"), THE Quick_Add_Bar SHALL create the nested structure: "company" as OBJECT (if not exists) and "name" as STRING child parameter
4. WHEN the Quick_Add_Bar creates a parameter, THE Quick_Add_Bar SHALL keep focus on the input field so the user can immediately type the next parameter name without clicking
5. IF the user types a name that already exists at root level, THEN THE Quick_Add_Bar SHALL display an inline error message "参数名已存在" and not create the parameter
6. IF the user types a name containing invalid characters (not matching `^[a-zA-Z_][a-zA-Z0-9_-]*$`), THEN THE Quick_Add_Bar SHALL display an inline error message "参数名格式无效" and not create the parameter
7. IF the user presses Enter with an empty input, THEN THE Quick_Add_Bar SHALL do nothing (no API call, no error message)

### Requirement 11: 右键菜单完整功能

**User Story:** As a 模板设计者, I want 右键菜单的复制粘贴功能能完整复制参数的所有属性和子参数, so that 我可以快速复制已配置好的参数结构到其他位置。

#### Acceptance Criteria

1. WHEN the user selects "复制参数" from the context menu, THE Parameter_Settings_Page SHALL store the complete parameter definition (including all attributes and recursively all children) in a clipboard state
2. WHEN the user selects "粘贴参数" from the context menu, THE Parameter_Settings_Page SHALL create a new parameter as a sibling of the context row, copying all attributes (data_type, required, default_value, description, validation_rules, expression_text, expression_type) from the clipboard, with the name appended with "_copy" suffix, and recursively create all child parameters
3. WHEN the user selects "粘贴参数" and the clipboard is empty, THE context menu SHALL disable the "粘贴参数" option with a grayed-out appearance
4. WHEN the user selects "删除参数" from the context menu, THE Parameter_Settings_Page SHALL display a confirmation dialog showing the parameter name and the count of descendant parameters that will also be deleted
5. IF the user pastes a parameter tree that would exceed the maximum nesting depth of 5 levels at the target position, THEN THE Parameter_Settings_Page SHALL display an error message "粘贴后将超过最大嵌套深度（5层）" and not create the parameter


## Correctness Properties

### CP-1: 内联编辑幂等性 (Req 1)
对同一个参数字段连续执行 N 次相同值的内联编辑，最终状态应与执行 1 次相同，且 API 调用次数为 N（每次 Enter/blur 触发一次 update）。

### CP-2: 智能命名唯一性 (Req 2)
在任意参数树状态下，Smart_Name_Generator 生成的名称在同一 parent scope 内必须唯一，即 `∀ generated_name: ¬∃ sibling where sibling.name === generated_name`。

### CP-3: JSON 导入类型推断正确性 (Req 3)
对于任意合法 JSON 输入，JSON_Import_Engine 推断的 dataType 必须满足：typeof(value) === 'string' → STRING, typeof(value) === 'number' → NUMBER, typeof(value) === 'boolean' → BOOLEAN, value === null → STRING, typeof(value) === 'object' && !Array.isArray(value) → OBJECT, Array.isArray(value) → ARRAY。

### CP-4: 拖拽排序一致性 (Req 4)
拖拽排序完成后，同一 parent scope 下所有 siblings 的 sort_order 必须是从 0 开始的连续整数序列，且序列长度等于 siblings 数量。

### CP-5: 批量操作原子性 (Req 9)
batch-delete 和 batch-update 操作必须是原子的：要么全部成功，要么全部回滚，不存在部分成功的中间状态。

### CP-6: 快速添加栏名称验证 (Req 10)
Quick_Add_Bar 创建的参数名称必须通过后端的 name 校验规则（`^[a-zA-Z_][a-zA-Z0-9_-]*$`），且在同一 parent scope 内不重复。

### CP-7: 复制粘贴深拷贝完整性 (Req 11)
粘贴操作创建的参数树必须是源参数的深拷贝：所有属性值相同（除 id、name 后缀、parentId），且子参数递归复制的层级深度与源参数一致。

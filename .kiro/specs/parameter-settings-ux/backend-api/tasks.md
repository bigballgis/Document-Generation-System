# Implementation Plan: Parameter Settings UX — Backend API

## Overview

新增后端批量操作 API（batch-delete、batch-update）和 JSON 导入端点（json-import），包括 DTO、Service 逻辑、ErrorCode 常量、AuditLog 操作类型，以及对应的单元测试和属性测试。

## Tasks

- [x] 1. 新增 ErrorCode 常量和 DTO
  - [x] 1.1 在 `ErrorCode.java` 中添加 `PARAMETER_BATCH_INVALID_IDS`、`PARAMETER_BATCH_VALIDATION_FAILED`、`PARAMETER_JSON_IMPORT_FAILED`、`PARAMETER_JSON_IMPORT_DEPTH_EXCEEDED` 四个常量
    - 添加到 `// ── PARAMETER (参数表) ──` 区块末尾
    - _Requirements: 9.3, 9.4, 3.6, 3.7_
  - [x] 1.2 创建 `BatchDeleteParameterRequest.java` record DTO
    - 路径: `backend/src/main/java/com/docgen/dto/BatchDeleteParameterRequest.java`
    - 字段: `@NotEmpty List<@NotNull Long> ids`
    - _Requirements: 9.1_
  - [x] 1.3 创建 `BatchUpdateParameterRequest.java` record DTO（含内部 `BatchUpdateItem` record）
    - 路径: `backend/src/main/java/com/docgen/dto/BatchUpdateParameterRequest.java`
    - `BatchUpdateItem` 字段: id(@NotNull), version(@NotNull), name, parameterType, dataType, required, defaultValue, description, sortOrder, expressionText, expressionType, validationRules(Map<String,Object>)
    - _Requirements: 9.2_
  - [x] 1.4 创建 `JsonImportRequest.java` record DTO
    - 路径: `backend/src/main/java/com/docgen/dto/JsonImportRequest.java`
    - 字段: `@NotBlank String jsonData`, `Long parentId`（可选）
    - _Requirements: 3.1, 3.2_

- [x] 2. 实现 ParameterService 批量操作方法
  - [x] 2.0 在 `ParameterService.java` 中注入 `AuditLogService` 依赖
    - 添加 `private final AuditLogService auditLogService` 字段
    - 更新构造函数参数
    - 当前 ParameterService 未注入 AuditLogService，批量操作需要记录审计日志
    - _Requirements: 9.1, 9.2, 3.1_
  - [x] 2.1 实现 `batchDelete(Long templateId, List<Long> ids)` 方法
    - 校验所有 ID 属于指定 templateId，无效 ID 抛出 `PARAMETER_BATCH_INVALID_IDS`
    - 过滤出"最顶层" ID 集合（如果列表中同时包含父和子，只删父，子由 CASCADE 处理）
    - 单事务 `@Transactional` 删除
    - 记录 AuditLog（action=BATCH_DELETE_PARAMETER, resourceType=PARAMETER）
    - _Requirements: 9.1, 9.3_
  - [x] 2.2 实现 `batchUpdate(Long templateId, List<BatchUpdateItem> items)` 方法
    - 校验所有 ID 属于指定 templateId
    - 对每个 item 执行与 `updateParameter` 相同的校验逻辑（名称唯一性、数据类型兼容性、衍生表达式等）
    - 任一校验失败则整批回滚，返回所有校验错误（`PARAMETER_BATCH_VALIDATION_FAILED`）
    - 乐观锁冲突抛出 `PARAMETER_CONCURRENT_MODIFICATION`
    - 单事务 `@Transactional` 更新
    - 记录 AuditLog（action=BATCH_UPDATE_PARAMETER, resourceType=PARAMETER）
    - _Requirements: 9.2, 9.4_
  - [x] 2.3 编写 `batchDelete` 单元测试
    - 测试正常删除、无效 ID、跨模板 ID、CASCADE 子参数场景
    - 路径: `backend/src/test/java/com/docgen/service/ParameterServiceBatchTest.java`
    - _Requirements: 9.1, 9.3_
  - [x] 2.4 编写 `batchUpdate` 单元测试
    - 测试正常更新、校验失败回滚、乐观锁冲突、部分无效场景
    - 路径: `backend/src/test/java/com/docgen/service/ParameterServiceBatchTest.java`
    - _Requirements: 9.2, 9.4_
  - [x] 2.5 编写属性测试：批量更新原子性 (Property 9)
    - **Property 9: 批量更新原子性**
    - **Validates: Requirements 9.4**
    - 路径: `backend/src/test/java/com/docgen/property/ParameterBatchUpdatePropertyTest.java`
    - 生成随机批量更新请求（含有效和无效项），验证要么全部成功要么全部回滚

- [x] 3. 实现 ParameterService JSON 导入方法
  - [x] 3.1 实现 `jsonImport(Long templateId, String jsonData, Long parentId)` 方法
    - 解析 JSON 字符串，无效 JSON 抛出 `PARAMETER_JSON_IMPORT_FAILED`（含解析错误位置和原因）
    - 递归遍历 JSON 结构，按类型推断规则创建参数：string→STRING, number→NUMBER, boolean→BOOLEAN, null→STRING(required=false), object→OBJECT(递归), array→ARRAY
    - 数组含对象元素时取所有元素 keys 的并集创建子参数
    - 数组仅含原始值时创建 ARRAY 无子参数，description 注明元素类型
    - 空 JSON `{}` 或 `[]` 抛出 `PARAMETER_JSON_IMPORT_FAILED`（提示 JSON 数据为空）
    - 嵌套深度超过 5 层时，深层数据扁平化为 STRING 类型并在响应中附带警告
    - 单事务 `@Transactional` 创建所有参数
    - 记录 AuditLog（action=JSON_IMPORT_PARAMETER, resourceType=PARAMETER）
    - _Requirements: 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9_
  - [x] 3.2 编写 `jsonImport` 单元测试
    - 测试各种 JSON 结构、空 JSON、超深度、无效 JSON、数组含对象、数组仅原始值
    - 路径: `backend/src/test/java/com/docgen/service/ParameterServiceJsonImportTest.java`
    - _Requirements: 3.2-3.9_
  - [x] 3.3 编写属性测试：参数名称格式校验 (Property 11)
    - **Property 11: 参数名称格式校验**
    - **Validates: Requirements 10.6**
    - 路径: `backend/src/test/java/com/docgen/property/ParameterNameValidationPropertyTest.java`
    - 生成随机字符串，验证 `validateName()` 的结果与正则 `^[a-zA-Z_][a-zA-Z0-9_-]*$` 匹配一致

- [x] 4. 新增 Controller 端点并配置安全
  - [x] 4.1 在 `ParameterController.java` 中添加三个新端点
    - `POST /api/templates/{templateId}/parameters/batch-delete` → 调用 `batchDelete`，返回 204
    - `POST /api/templates/{templateId}/parameters/batch-update` → 调用 `batchUpdate`，返回 200 + List<ParameterDTO>
    - `POST /api/templates/{templateId}/parameters/json-import` → 调用 `jsonImport`，返回 201 + List<ParameterDTO>
    - 每个端点添加 `@Operation(summary = ...)` 注解
    - _Requirements: 9.1, 9.2, 3.1_
  - [x] 4.2 验证 SecurityConfig 无需修改（新端点匹配 `/api/**` 已有的 `.authenticated()` 规则）
    - _Requirements: 9.1, 9.2_

- [x] 5. Checkpoint — 后端编译和测试
  - 确保 `./mvnw compile` 编译通过
  - 确保所有测试通过，ask the user if questions arise.

- [x] 6. 编写集成测试
  - [x] 6.1 编写批量 API 集成测试
    - 路径: `backend/src/test/java/com/docgen/integration/ParameterBatchApiIntegrationTest.java`
    - 使用 Testcontainers + PostgreSQL
    - 测试 batch-delete 和 batch-update 的事务原子性
    - _Requirements: 9.1-9.4_
  - [x] 6.2 编写 JSON 导入集成测试
    - 路径: `backend/src/test/java/com/docgen/integration/ParameterJsonImportIntegrationTest.java`
    - 使用 Testcontainers + PostgreSQL
    - 测试端到端 JSON 导入流程
    - _Requirements: 3.1-3.9_

- [x] 7. Final checkpoint — 后端全部测试通过
  - 确保所有测试通过，ask the user if questions arise.

## Notes

- 新端点 URL 匹配 SecurityConfig 已有的 `/api/**` authenticated 规则，无需额外配置
- OpenApiConfig 已有 `Parameter` tag，无需修改
- 无数据库 schema 变更，无需 Flyway 迁移

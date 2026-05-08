# 实施计划：后端 API + 服务 + DTO 扩展

## 概述

实现设计阶段重构所需的后端变更：DTO 扩展、新增 API 端点、内容隔离校验服务、SecurityConfig 白名单、预置空白 .docx 模板文件。

## Tasks

- [x] 1. 扩展 AssemblySegmentEntry DTO 和新增 ErrorCode
  - [x] 1.1 扩展 `AssemblySegmentEntry.java`，新增 4 个字段：`headerFilePath`(String)、`footerFilePath`(String)、`pageNumberFormat`(String)、`pageNumberStart`(Integer)，含 getter/setter
    - 文件：`backend/src/main/java/com/docgen/dto/AssemblySegmentEntry.java`
    - JSONB schema-less，无需 Flyway 迁移，Jackson 反序列化自动兼容 null
    - _Requirements: 4.6, 4.7, 4.8_
  - [x] 1.2 在 `ErrorCode.java` ONLYOFFICE 模块下新增 `ONLYOFFICE_CONTENT_ISOLATION_VIOLATION` 常量
    - 文件：`backend/src/main/java/com/docgen/exception/ErrorCode.java`
    - _Requirements: 6.4, 6.5, 6.6_

- [x] 2. 创建预置空白 .docx 模板文件
  - [x] 2.1 创建 `backend/src/main/resources/templates/blank-body.docx`（仅含空 body，无 header/footer 区域）
    - 使用最小化 .docx 结构（ZIP 包含 [Content_Types].xml、_rels/.rels、word/document.xml）
    - _Requirements: 4.4, 6.1_
  - [x] 2.2 创建 `backend/src/main/resources/templates/blank-header.docx`（仅含 header 区域内容）
    - _Requirements: 4.6, 6.2_
  - [x] 2.3 创建 `backend/src/main/resources/templates/blank-footer.docx`（仅含 footer 区域内容）
    - _Requirements: 4.7, 6.3_

- [x] 3. 实现 ContentIsolationValidator 服务
  - [x] 3.1 创建 `ContentIsolationValidator.java` 服务类
    - 文件：`backend/src/main/java/com/docgen/service/ContentIsolationValidator.java`
    - 实现 `validate(byte[] docxBytes, String expectedContentType)` 方法
    - 解压 .docx (ZIP 格式)，根据 expectedContentType 检查非期望区域的 XML 文件是否包含非空内容
    - "body" → header*.xml 和 footer*.xml 必须为空
    - "header" → document.xml body 和 footer*.xml 必须为空
    - "footer" → document.xml body 和 header*.xml 必须为空
    - 校验失败抛出 BusinessException(ONLYOFFICE_CONTENT_ISOLATION_VIOLATION, 422)
    - _Requirements: 6.4, 6.5, 6.6, 6.7_
  - [x] 3.2 编写 ContentIsolationValidator 单元测试
    - 文件：`backend/src/test/java/com/docgen/service/ContentIsolationValidatorTest.java`
    - 测试各种 .docx 结构的边界情况（空文件、仅 body、仅 header、混合内容）
    - _Requirements: 6.4, 6.5, 6.6_

- [x] 4. 实现新增 API 端点
  - [x] 4.1 在 `CompositeTemplateController.java` 中新增 `POST /{id}/create-blank-segment` 端点
    - 接收 `{ name, segmentType }` 请求体
    - 使用 `blank-body.docx` classpath 资源复制后上传到 MinIO `segments/{templateId}/{uuid}_{name}.docx`
    - 返回 201 Created + AssemblySegmentEntry（含 filePath 和新增的 4 个 null 字段）
    - 验证模板存在且为 COMPOSITE 类型
    - _Requirements: 4.4_
  - [x] 4.2 在 `CompositeTemplateController.java` 中新增 `POST /{id}/create-blank-header-footer` 端点
    - 接收 `{ type: "header" | "footer" }` 请求体
    - 使用 `blank-header.docx` 或 `blank-footer.docx` 上传到 MinIO `segments/{templateId}/headers/` 或 `segments/{templateId}/footers/`
    - 返回 201 Created + `{ filePath }`
    - _Requirements: 4.6, 4.7_
  - [x] 4.3 在 `CompositeTemplateController.java` 中新增 `GET /{id}/segments/{segmentIndex}/onlyoffice-url` 端点
    - 根据 segmentIndex 从 assemblyConfig 获取 filePath，生成 MinIO presigned URL
    - 返回 `{ url }`
    - _Requirements: 5.1, 5.2_
  - [x] 4.4 在 `CompositeTemplateController.java` 中新增 `POST /{id}/segments/{segmentIndex}/onlyoffice-callback` 端点
    - 接收 OnlyOffice 标准回调格式 + 查询参数 `contentType`（body/header/footer）
    - 在保存到 MinIO 之前调用 ContentIsolationValidator 校验
    - 返回 `{ "error": 0 }`
    - _Requirements: 6.7_

- [x] 5. 更新 SecurityConfig 和 Checkpoint
  - [x] 5.1 在 `SecurityConfig.java` 中新增 permitAll URL 白名单
    - 添加 `.requestMatchers("/api/composite-templates/*/segments/*/onlyoffice-callback").permitAll()`
    - 位置：在现有 `/api/templates/*/onlyoffice-callback` 白名单之后
    - _Requirements: 6.7_
  - [x] 5.2 Checkpoint — 确保后端编译通过，运行现有测试无回归
    - 执行 `mvn compile -q` 和 `mvn test -q` 确认无错误
    - 如有问题请询问用户

## Notes

- 预置 .docx 文件使用最小化 ZIP 结构，避免引入 Apache POI 依赖
- JSONB 扩展无需 Flyway 迁移，Jackson 自动兼容
- 新增 API 端点均在现有 CompositeTemplateController 中，无需新建 Controller

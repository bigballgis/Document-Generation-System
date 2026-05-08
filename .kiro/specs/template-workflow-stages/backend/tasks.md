# Implementation Plan: 模板工作流阶段化 — Backend

## Overview

后端变更极小：在 `ErrorCode.java` 新增 `TEMPLATE_EXPORT_NOT_ACTIVE` 常量，在 `CompositeImportExportService.exportAsZip` 方法开头增加模板状态检查，仅允许 ACTIVE 状态的模板导出 ZIP 包。包含对应的单元测试和属性测试。

## Tasks

- [x] 1. 新增 ErrorCode 常量和导出约束
  - [x] 1.1 在 `ErrorCode.java` 的 `// ── TEMPLATE (模板管理) ──` 区块末尾新增常量
    - `public static final String TEMPLATE_EXPORT_NOT_ACTIVE = "TEMPLATE_EXPORT_NOT_ACTIVE";`
    - _Requirements: 12.2_
  - [x] 1.2 在 `CompositeImportExportService.exportAsZip` 方法开头增加状态检查
    - 路径: `backend/src/main/java/com/docgen/service/CompositeImportExportService.java`
    - 在 `findCompositeTemplateOrThrow` 之后、现有逻辑之前插入检查
    - 若 `template.getStatus()` 不为 `ACTIVE`，抛出 `BusinessException(ErrorCode.TEMPLATE_EXPORT_NOT_ACTIVE, "Only ACTIVE templates can be exported as ZIP", HttpStatus.BAD_REQUEST)`
    - _Requirements: 12.1_
  - [x] 1.3 在 i18n 错误消息映射中新增 `TEMPLATE_EXPORT_NOT_ACTIVE` 的三语言翻译
    - en-US: `"Only ACTIVE templates can be exported as ZIP"`
    - zh-CN: `"仅 ACTIVE 状态的模板可导出 ZIP 包"`
    - zh-TW: `"僅 ACTIVE 狀態的模板可匯出 ZIP 包"`
    - 添加到三语言文件的 `error` 区块
    - _Requirements: 12.1, 12.2_

- [x] 2. 编写测试
  - [x] 2.1 编写 `exportAsZip` 单元测试
    - 路径: `backend/src/test/java/com/docgen/service/CompositeImportExportServiceExportTest.java`
    - 测试场景: DRAFT 状态导出→抛出异常、PENDING_REVIEW 状态导出→抛出异常、REVIEWED 状态导出→抛出异常、ARCHIVED 状态导出→抛出异常、ACTIVE 状态导出→正常执行
    - 验证异常错误码为 `TEMPLATE_EXPORT_NOT_ACTIVE`
    - _Requirements: 12.1_
  - [x] 2.2 编写属性测试：导出约束后端强制 (Property 3)
    - **Property 3: 导出约束后端强制**
    - **Validates: Requirements 12.1**
    - 路径: `backend/src/test/java/com/docgen/property/ExportConstraintPropertyTest.java`
    - 使用 jqwik 生成随机 TemplateState（DRAFT/PENDING_REVIEW/REVIEWED/ARCHIVED/ACTIVE），验证非 ACTIVE 状态必定抛出 BusinessException，ACTIVE 状态不抛出该异常

- [x] 3. Checkpoint — 后端编译和测试
  - 确保 `./mvnw compile` 编译通过
  - 确保 `./mvnw test -Dtest=CompositeImportExportServiceExportTest,ExportConstraintPropertyTest` 测试通过
  - ask the user if questions arise.

## Notes

- 无数据库 schema 变更，无需 Flyway 迁移
- 无 SecurityConfig 变更（现有端点权限不变）
- 无 OpenApiConfig 变更

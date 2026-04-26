# Implementation Plan: Backend Core — 参数表数据模型、CRUD API、扫描关联

## Overview

创建 template_parameters 表、ParameterDefinition Entity、DTO、ParameterRepository、ParameterService（CRUD + 树形构建 + 验证）、TemplateScanService（占位符扫描 + dot-notation/loop 解析）、ParameterController，以及 Flyway V37 数据迁移脚本。

## Tasks

- [x] 1. Flyway V37 迁移脚本与 ErrorCode 常量
  - [x] 1.1 Create Flyway migration V37__create_template_parameters_and_migrate_data.sql
    - Create template_parameters table with all columns, constraints, and indexes per design
    - Include partial unique index for root-level name uniqueness (WHERE parent_id IS NULL)
    - Migrate TemplateVariable → REQUEST parameters, Expression-bound variables → DERIVED parameters, standalone Expressions → DERIVED parameters
    - Do NOT drop old tables in V37 — old tables will be dropped in V38 after Java code removal in backend-cleanup sub-spec
    - _Requirements: 1.1, 1.2, 1.6, 1.11, 9.1, 9.2, 9.3, 9.7, 9.8, 9.9_
  - [x] 1.2 Add PARAMETER_* error codes to ErrorCode.java
    - Add all 16 PARAMETER_* constants per design Error Handling section
    - _Requirements: 1.16, 1.17, 2.5, 2.6, 2.9, 2.10, 2.11, 2.12, 2.14_

- [x] 2. ParameterDefinition Entity and DTOs
  - [x] 2.1 Create ParameterDefinition entity
    - JPA entity with @Table, @Filter(tenantFilter), @Version, @PrePersist/@PreUpdate, all columns per design
    - Use java.time.Instant for timestamps, GenerationType.IDENTITY, no Lombok
    - _Requirements: 1.1, 1.2, 1.6, 1.11_
  - [x] 2.2 Create ParameterDTO, CreateParameterRequest, UpdateParameterRequest, ScanResultDTO, PlaceholderInfo, ParameterSchemaDTO, ParameterSchemaEntry
    - ParameterDTO with children list and parameterPath computed field
    - CreateParameterRequest and UpdateParameterRequest as records with Jakarta Validation
    - ScanResultDTO, PlaceholderInfo as records per design
    - _Requirements: 1.1, 2.1, 2.2, 2.13, 3.1, 3.2, 7.1, 7.4_
  - [x] 2.3 Create ParameterRepository (Spring Data JPA)
    - findByTemplateIdOrderBySortOrderAsc, findByParentIdOrderBySortOrderAsc, findByTemplateIdAndParentIdIsNullOrderBySortOrderAsc
    - countByTemplateIdAndParentId for depth calculation
    - _Requirements: 1.6, 2.2_

- [x] 3. Checkpoint — Ensure entity and migration compile
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. ParameterService — CRUD and tree operations
  - [x] 4.1 Implement ParameterService core CRUD methods
    - createParameter: validate name pattern, parent type, depth, derived expression, validation rules compatibility, duplicate name; save and return DTO
    - getParameterTree: fetch all by templateId, build nested tree structure with children arrays ordered by sortOrder
    - getParameterFlat: fetch all by templateId, compute parameterPath for each, return flat list
    - updateParameter: validate same constraints as create, handle optimistic locking (catch OptimisticLockException → PARAMETER_CONCURRENT_MODIFICATION)
    - deleteParameter: delete by id (cascade handled by DB)
    - _Requirements: 1.2, 1.3, 1.4, 1.5, 1.7, 1.8, 1.9, 1.10, 1.12, 1.13, 1.14, 1.15, 1.16, 1.17, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10, 2.11, 2.12, 2.13, 2.14_
  - [x] 4.2 Implement helper methods: computeParameterPath, validateDepth, validateName, validateParentType, detectCircularDependency, validateValidationRules
    - computeParameterPath: traverse parent chain, join with dots
    - validateDepth: recursive count from root, reject if > 5
    - validateName: regex ^[a-zA-Z_][a-zA-Z0-9_-]*$
    - validateParentType: parent must be OBJECT or ARRAY
    - detectCircularDependency: topological sort on DERIVED parameter expression references
    - validateValidationRules: check compatibility matrix, range consistency, pattern syntax
    - _Requirements: 1.8, 1.9, 1.10, 1.15, 1.16, 1.17, 2.9, 2.10, 2.11, 2.12, 5.6_
  - [x] 4.3 Write property tests for ParameterService
    - **Property 1-8, 11, 17**: All parameter validation property tests
    - **Validates: Requirements 1.2, 1.3, 1.4, 1.7, 1.8, 1.9, 1.10, 1.15, 1.16, 1.17, 2.5, 2.6, 2.9, 2.12, 5.6**
  - [x] 4.4 Write unit tests for ParameterService
    - Test CRUD happy paths, edge cases (empty template, root vs child), error scenarios
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.14_

- [x] 5. TemplateScanService — placeholder scanning
  - [x] 5.1 Create TemplateScanService
    - Extract and enhance logic from TemplateVariableService.extractVariableNames
    - scanPlaceholders: scan .docx file, return List<PlaceholderInfo>
    - extractXmlFromDocx: read ZIP entries from word/*.xml
    - parsePlaceholders: parse XML for simple variables, dot-notation paths, loop constructs, nested loops, conditions
    - Return PlaceholderInfo with name, fullPath, type, segments, children
    - _Requirements: 3.1, 3.4, 3.5, 3.6, 3.7, 3.8_
  - [x] 5.2 Implement scan comparison and auto-create in ParameterService
    - scanPlaceholders(templateId): call TemplateScanService, compare with existing parameters by path, return ScanResultDTO
    - autoCreateParameters(templateId): create full tree hierarchy for unmatched placeholders
    - Recommend data_type based on name keywords
    - _Requirements: 3.2, 3.3, 3.6, 3.7, 3.8, 4.16_
  - [x] 5.3 Write property tests for TemplateScanService
    - **Property 12-15**: Placeholder parsing, scan comparison, auto-create, type recommendation
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.6, 3.7, 3.8, 4.16**

- [x] 6. ParameterController
  - [x] 6.1 Create ParameterController with all endpoints
    - POST /api/templates/{templateId}/parameters → 201
    - GET /api/templates/{templateId}/parameters → 200 (tree, or flat with ?flat=true)
    - PUT /api/parameters/{id} → 200
    - DELETE /api/parameters/{id} → 204
    - POST /api/templates/{templateId}/parameters/scan → 200
    - POST /api/templates/{templateId}/parameters/auto-create → 201
    - GET /api/templates/{templateId}/parameter-schema → 200
    - Use @Valid on request bodies, @Tag("Parameter") for OpenAPI
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.13, 3.1, 3.3, 7.1_
  - [x] 6.2 Update SecurityConfig.java — add parameter API paths to authenticated endpoints
    - _Requirements: (SecurityConfig URL check)_
  - [x] 6.3 Update OpenApiConfig.java — add "Parameter" tag
    - _Requirements: (OpenApiConfig tag check)_

- [x] 7. Property test: validation_rules round-trip and tree structure
  - [x] 7.1 Write ValidationRulesRoundTripPropertyTest
    - **Property 5: validation_rules round-trip**
    - **Validates: Requirements 1.7**
  - [x] 7.2 Write TreeStructurePropertyTest
    - **Property 10: Tree structure correctness**
    - **Validates: Requirements 2.2**
  - [x] 7.3 Write CascadeDeletePropertyTest
    - **Property 9: Cascade deletion preserves tree integrity**
    - **Validates: Requirements 1.11, 2.4**

- [x] 8. Final checkpoint — Ensure all backend-core tests pass
  - Ensure all tests pass, ask the user if questions arise.

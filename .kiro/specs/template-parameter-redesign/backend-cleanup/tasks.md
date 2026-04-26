# Implementation Plan: Backend Cleanup — 旧架构代码彻底移除

## Overview

彻底移除 DataSource、Expression（独立管理）、TemplateVariable 相关的后端代码（Controller/Service/Entity/Repository/DTO/Test），更新所有依赖这些组件的服务，清理配置文件。

## Tasks

- [x] 1. Remove DataSource backend code
  - [x] 1.1 Delete DataSource Controller, Services, Entity, Repository, DTOs
    - Delete: DataSourceController.java, DataSourceCrudService.java, DataAggregationService.java, HttpApiDataSourceService.java, DatabaseDataSourceService.java, InternalSystemDataSourceService.java, DataSourceCacheService.java, DataSource.java, DataSourceType.java, DataSourceRepository.java, DataSourceDTO.java, DataSourceHealthDTO.java, CreateDataSourceRequest.java, UpdateDataSourceRequest.java
    - _Requirements: 10.1_
  - [x] 1.2 Delete DataSource test files
    - Delete: DataSourceCrudServiceTest.java, DatabaseDataSourceServiceTest.java, HttpApiDataSourceServiceTest.java, InternalSystemDataSourceServiceTest.java, DataSourceCacheServiceTest.java, DataSourceErrorPropagationPropertyTest.java, DataAggregationServiceTest.java, DataPipelineServiceImplTest.java
    - _Requirements: 10.2_
  - [x] 1.3 Remove DataPipelineService and DataPipelineServiceImpl
    - Delete DataPipelineService.java (interface) and DataPipelineServiceImpl.java
    - _Requirements: 10.9_

- [x] 2. Remove Expression and TemplateVariable backend code
  - [x] 2.1 Delete Expression Controller, Service, Entity, Repository, DTOs
    - Delete: ExpressionController.java, ExpressionCrudService.java, Expression.java, ExpressionRepository.java, CreateExpressionRequest.java, UpdateExpressionRequest.java, ExpressionDTO.java (backend DTO if exists)
    - _Requirements: 10.23_
  - [x] 2.2 Delete TemplateVariable Controller, Service, Entity, Repository, DTOs
    - Delete: TemplateVariableController.java, TemplateVariableService.java, TemplateVariable.java, TemplateVariableRepository.java, TemplateVariableDTO.java, BindVariableRequest.java
    - _Requirements: 10.29_
  - [x] 2.3 Delete Expression and TemplateVariable test files
    - Delete: ExpressionCrudServiceTest.java, TemplateVariableServiceTest.java, and any expression-specific test files
    - _Requirements: 10.30_

- [x] 3. Checkpoint — Verify deletions don't break unrelated code yet
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Update dependent services
  - [x] 4.1 Update Resilience4jConfig — remove dataSourceCircuitBreaker bean and DATASOURCE_CB constant
    - _Requirements: 10.10_
  - [x] 4.2 Update DashboardController, DashboardService, and DashboardServiceTest — remove data source health endpoint and related tests
    - _Requirements: 10.11_
  - [x] 4.3 Update CompositeImportExportService — remove DataSourceRepository dependency and importDataSources/toMaskedDataSourceMap/maskCredentialFields methods
    - _Requirements: 10.13_
  - [x] 4.4 Update TemplateImportExportService — remove DataSourceRepository dependency and data source export/import logic
    - _Requirements: 10.15_
  - [x] 4.5 Update MigrationService — remove DataSourceRepository dependency and migrateDataSources method
    - _Requirements: 10.14_
  - [x] 4.6 Update RedisIntegrationTest — remove DataSourceCacheService dependency
    - _Requirements: 10.2_
  - [x] 4.7 Update CoverageCheckServiceTest — remove DataSource/Expression mocks, align with new three-dimensional coverage
    - _Requirements: 10.12_

- [x] 5. Remove DATASOURCE and TEMPLATE_VARIABLE error codes from ErrorCode.java
  - Remove DATASOURCE_CONNECTION_FAILED, DATASOURCE_TIMEOUT, DATASOURCE_NOT_FOUND, TEMPLATE_VARIABLE_NOT_FOUND, TEMPLATE_VARIABLE_INVALID_TYPE, TEMPLATE_VARIABLE_SCAN_FAILED
  - Keep EXPRESSION_* codes as they are still used by ExpressionEngine for DERIVED parameter evaluation
  - _Requirements: 10.1, 10.29_

- [x] 6. Create Flyway V38 migration to drop old tables
  - [x] 6.1 Create V38__drop_legacy_datasource_expression_variable_tables.sql
    - DROP TABLE IF EXISTS data_sources CASCADE
    - DROP TABLE IF EXISTS expressions CASCADE
    - DROP TABLE IF EXISTS template_variables CASCADE
    - _Requirements: 9.4, 9.5, 9.6_

- [x] 7. Final checkpoint — Ensure all backend tests pass after cleanup
  - Ensure all tests pass, ask the user if questions arise.

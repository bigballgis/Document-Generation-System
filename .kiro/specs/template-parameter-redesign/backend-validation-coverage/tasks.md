# Implementation Plan: Backend Validation & Coverage — 参数验证、覆盖率重构、文档生成简化、参数 Schema

## Overview

创建 ParameterValidationService（参数验证 + validation_rules 校验 + 递归嵌套验证）、重构 CoverageCheckService（三维覆盖率）、简化 DocumentGeneratorService（三步管道）、实现 Parameter Schema 端点。

## Tasks

- [x] 1. ParameterValidationService — 参数验证与衍生参数计算
  - [x] 1.1 Create ParameterValidationService
    - validateAndBuildContext: validate all REQUEST params against Parameter_Table, apply defaults, evaluate DERIVED params, return complete data context
    - validateParameterValue: check data_type match, required/default handling
    - applyValidationRules: validate not_null, not_blank, min_length, max_length, min, max, pattern, enum_values, min_items, max_items; use custom_message when defined
    - validateNestedObject: recursively validate OBJECT children against JSON object fields
    - validateNestedArray: validate each array element against ARRAY children definitions
    - Collect all errors (not fail-fast), return errors array with full parameter paths (e.g., "items[2].price")
    - evaluateDerivedParameters: evaluate DERIVED params in sort_order, each has access to all REQUEST + previously evaluated DERIVED params
    - Skip validation when template has zero Parameter_Definitions (pass raw params)
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 6.8, 6.9, 6.10, 6.11, 6.12, 6.13, 6.14, 6.15, 6.16, 6.17, 6.18, 6.19, 6.20, 6.21, 6.22, 6.23_
  - [x] 1.2 Write property tests for ParameterValidationService
    - **Property 16: DERIVED parameter evaluation order** — jqwik test verifying sort_order evaluation with context accumulation
    - **Property 18: Required parameter handling with defaults** — jqwik test for missing required params with/without defaults
    - **Property 19: Type validation** — jqwik test for type mismatch detection
    - **Property 20: Extra parameters are ignored** — jqwik test verifying extra params don't appear in context
    - **Property 21: Validation rules enforcement** — jqwik test for each rule type violation
    - **Property 22: Multiple validation errors collected** — jqwik test verifying N errors → N entries
    - **Property 23: Recursive nested validation** — jqwik test for OBJECT/ARRAY nested validation with full paths
    - **Validates: Requirements 5.2, 5.5, 6.2, 6.3, 6.4, 6.5, 6.6, 6.10-6.23**
  - [x] 1.3 Write unit tests for ParameterValidationService
    - Test happy path validation, default value substitution, empty parameter table, expression evaluation failure
    - _Requirements: 6.1, 6.4, 6.7, 6.8_

- [x] 2. Checkpoint — Ensure ParameterValidationService tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 3. DocumentGeneratorService simplification
  - [x] 3.1 Refactor DocumentGeneratorService.executePipeline to three-step pipeline
    - Replace DataAggregationService call with ParameterValidationService.validateAndBuildContext
    - Pipeline: validate parameters → evaluate DERIVED parameters → render template
    - Remove DataAggregationService dependency and dataSourceCb circuit breaker reference
    - _Requirements: 6.9_
  - [x] 3.2 Update CompositeGeneratorService similarly
    - Remove DataAggregationService dependency and dataSourceCb circuit breaker reference
    - Use ParameterValidationService for parameter handling
    - _Requirements: 10.7_
  - [x] 3.3 Update TemplatePreviewService
    - Remove DataAggregationService dependency, use parameter-based data context
    - _Requirements: 10.8_

- [x] 4. CoverageCheckService refactor — three-dimensional coverage
  - [x] 4.1 Refactor CoverageCheckService to compute Branch/Loop/Parameter coverage
    - Inject TemplateScanService and ParameterRepository (replace DataSourceRepository, ExpressionRepository, TemplateVariableRepository)
    - computeBranchCoverage: scan conditions from template, check test cases for true/false paths
    - computeLoopCoverage: scan loops from template, check test cases for empty/non-empty arrays
    - computeParameterCoverage: check which parameters receive non-null values across test cases
    - overallCoverage: equal-weighted average of applicable dimensions (exclude zero-item dimensions)
    - Retain threshold mechanism and belowThreshold flag
    - Update CoverageReport DTO to new three-dimensional structure per design
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 8.8, 8.9, 8.10, 8.11_
  - [x] 4.2 Write property tests for CoverageCheckService
    - **Property 27: Branch coverage computation** — jqwik test verifying formula
    - **Property 28: Loop coverage computation** — jqwik test verifying formula
    - **Property 29: Parameter coverage computation** — jqwik test verifying formula
    - **Property 30: Overall coverage weighted average** — jqwik test verifying weighted average with zero-item exclusion
    - **Property 31: Coverage threshold** — jqwik test verifying belowThreshold logic
    - **Validates: Requirements 8.2, 8.3, 8.4, 8.5, 8.7, 8.9**
  - [x] 4.3 Write unit tests for CoverageCheckService
    - Test zero branches/loops scenario, expression dependency warnings, threshold gating
    - _Requirements: 8.7, 8.8, 8.9_

- [x] 5. Parameter Schema endpoint
  - [x] 5.1 Implement getParameterSchema in ParameterService
    - Build nested tree structure with "properties" for OBJECT, "items" for ARRAY (JSON Schema conventions)
    - Include name, data_type, required, default_value, description, validation_rules constraints
    - Generate sample request body with example values based on data_type and default_value
    - Include metadata: templateName, templateVersion, totalParameterCount, requiredParameterCount
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7_
  - [x] 5.2 Write property tests for Parameter Schema
    - **Property 24: Parameter schema structure** — jqwik test verifying OBJECT→properties, ARRAY→items mapping
    - **Property 25: Parameter schema metadata accuracy** — jqwik test verifying counts
    - **Property 26: Sample request body generation** — jqwik test verifying nested JSON with example values
    - **Validates: Requirements 7.1, 7.2, 7.4, 7.5, 7.6, 7.7**

- [x] 6. Data migration property test
  - [x] 6.1 Write DataMigrationPropertyTest
    - **Property 32: Data migration correctness** — jqwik test verifying TemplateVariable → REQUEST/DERIVED mapping, standalone Expression → DERIVED
    - **Validates: Requirements 9.2, 9.3, 9.9**

- [x] 7. Final checkpoint — Ensure all backend-validation-coverage tests pass
  - Ensure all tests pass, ask the user if questions arise.

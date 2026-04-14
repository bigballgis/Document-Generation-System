# Requirements Document

## Introduction

本需求文档描述从文档生成系统（Document Generation System）中彻底移除 Segment Library（段落库）功能的完整范围。用户认为文档信件需要特异性，段落复用会带来人为错误风险，因此决定移除该功能。

移除范围涵盖：后端 Java 代码（Controller、Service、Entity、Repository、DTO）、数据库表及迁移脚本、前端页面/组件/Store/API/路由/i18n、Docxtemplater 服务中的 merge-segments 端点，以及组合模板（Composite Template）中对 Segment 的依赖。

## Glossary

- **System**: 文档生成系统（Document Generation System）的整体应用
- **Segment_Library**: 段落库功能模块，包含段落的 CRUD、版本控制、收藏、权限、审查、测试数据、推荐、锁定等全部子功能
- **Composite_Template**: 组合模板，通过 assembly_config 引用多个 Segment 组装成完整文档
- **Assembly_Engine**: 组装引擎，负责渲染和合并多个 Segment 生成最终文档
- **Backend_API**: 后端 REST API 层，包括 SegmentController、SegmentReviewController 及 CompositeTemplateController 中的 Segment 相关端点
- **Frontend_App**: 前端 Vue 3 应用，包括段落管理页面、组件、Store、API 调用层
- **Database**: PostgreSQL 数据库，包含 segments、segment_versions、segment_tag_mappings、segment_reviews、segment_test_data、segment_favorites 等表
- **Merge_Segments_Endpoint**: Docxtemplater Node.js 服务中的 /merge-segments 端点
- **Dashboard_Service**: 仪表盘服务，包含段落统计相关的 API 和数据
- **Flyway_Migration**: 数据库迁移脚本，使用 Flyway 管理

## Requirements

### Requirement 1: 移除后端 Segment 核心 API

**User Story:** As a 系统维护者, I want 移除所有 Segment 相关的后端 REST API, so that 系统不再暴露任何段落库功能的接口。

#### Acceptance Criteria

1. WHEN the removal is complete, THE Backend_API SHALL no longer contain SegmentController (/api/segments) endpoints
2. WHEN the removal is complete, THE Backend_API SHALL no longer contain SegmentReviewController (/api/segment-reviews) endpoints
3. WHEN the removal is complete, THE System SHALL no longer contain the following backend services: SegmentService, SegmentVersionService, SegmentVariableService, SegmentLockService, SegmentPermissionService, SegmentTestService, SegmentRecommendationService, SegmentReviewService, SegmentRendererService, SegmentDataScopeService
4. WHEN the removal is complete, THE System SHALL no longer contain the following repositories: SegmentRepository, SegmentFavoriteRepository, SegmentReviewRepository, SegmentTagMappingRepository, SegmentTestDataRepository, SegmentVersionRepository
5. WHEN the removal is complete, THE System SHALL no longer contain the Segment entity class and related entity classes (SegmentFavorite, SegmentReview, SegmentTagMapping, SegmentTestData, SegmentVersion)
6. WHEN the removal is complete, THE System SHALL no longer contain Segment-related DTO classes (SegmentDTO, CreateSegmentRequest, UpdateSegmentRequest, SegmentVersionDTO, SegmentVariableDTO, SegmentTestDataDTO, SegmentTestResultDTO, SegmentReviewDTO, SegmentRecommendationDTO, SegmentStatsDTO, SegmentTemplateDTO, SegmentQueryRequest, CreateSegmentTemplateRequest, CreateSegmentTestDataRequest, DuplicateAnalysisDTO, SegmentCoverageEntry, SegmentRenderResult, AssemblySegmentEntry, AssemblyConfigDTO, LockInfo 等)
7. WHEN the removal is complete, THE System SHALL no longer contain Segment-related ErrorCode constants (SEGMENT_NOT_FOUND, SEGMENT_REFERENCED, SEGMENT_VERSION_FILE_MISSING, GENERATE_ALL_SEGMENTS_SKIPPED)

### Requirement 2: 移除组合模板中的 Segment 依赖

**User Story:** As a 系统维护者, I want 移除组合模板功能中对 Segment 的依赖, so that 组合模板相关代码不再引用段落库。

#### Acceptance Criteria

1. WHEN the removal is complete, THE CompositeTemplateController SHALL no longer contain /segments、/reviews（段落级审查）、/recommendations、/tests/run 等 Segment 相关端点
2. WHEN the removal is complete, THE System SHALL no longer contain the following Composite-Segment services: CompositeTemplateService（中的 Segment 相关方法）、DependencyGraphService、CompositeCoverageService、CompositeImportExportService（中的 Segment 导入导出逻辑）、CompositeGeneratorService、AssemblyEngineService、AssemblyConfigService
3. WHEN the removal is complete, THE System SHALL no longer contain Composite-Segment DTOs: CompositeCoverageReport（含 SegmentCoverageEntry）、CompositePreviewDTO、CompositeTestReportDTO、SubmitCompositeReviewRequest、SegmentReviewerAssignment、SelectivePreviewRequest、UpdateAssemblyConfigRequest
4. WHEN the removal is complete, THE Template entity SHALL no longer contain the assembly_config JSONB column reference in application code
5. WHEN the removal is complete, THE Dashboard_Service SHALL no longer contain getSegmentStats() 方法和 getComponentRanking() 方法，DashboardController SHALL no longer contain /segment-stats 端点

### Requirement 3: 移除数据库 Segment 相关表

**User Story:** As a 系统维护者, I want 通过 Flyway 迁移脚本安全地删除所有 Segment 相关的数据库表, so that 数据库中不再保留段落库的数据结构。

#### Acceptance Criteria

1. THE Flyway_Migration SHALL create a new migration script that drops the following tables in correct dependency order: segment_favorites, segment_test_data, segment_reviews, segment_tag_mappings, segment_versions, segments
2. THE Flyway_Migration SHALL drop the segment-related indexes before dropping the tables
3. THE Flyway_Migration SHALL remove the segment_id column from the permissions table (added by V32)
4. THE Flyway_Migration SHALL remove the assembly_config column and template_type column from the templates table (added by V29)
5. THE Flyway_Migration SHALL remove the idx_templates_template_type index from the templates table
6. IF existing data references Segment records, THEN THE Flyway_Migration SHALL handle foreign key constraints by dropping dependent tables first

### Requirement 4: 移除前端 Segment 相关代码

**User Story:** As a 系统维护者, I want 移除前端应用中所有 Segment 相关的页面、组件、Store、API 调用和路由, so that 用户界面不再展示任何段落库功能。

#### Acceptance Criteria

1. WHEN the removal is complete, THE Frontend_App SHALL no longer contain the segment management pages: views/segments/Index.vue, views/segments/Detail.vue, views/segments/Editor.vue
2. WHEN the removal is complete, THE Frontend_App SHALL no longer contain the segment form components: views/segments/components/SegmentFormDialog.vue, SegmentPermissionDialog.vue, SegmentVariableList.vue, SegmentVersionList.vue
3. WHEN the removal is complete, THE Frontend_App SHALL no longer contain the SegmentArrangementTab.vue component in template-workspace
4. WHEN the removal is complete, THE Frontend_App SHALL no longer contain the segment API module (api/segments.ts) and the segment-related functions in api/composite-templates.ts
5. WHEN the removal is complete, THE Frontend_App SHALL no longer contain the segment Pinia store (stores/segment.ts) and the segment-related state/actions in stores/templateWorkspace.ts
6. WHEN the removal is complete, THE Frontend_App SHALL no longer contain the segment type definitions (types/segment.ts)
7. WHEN the removal is complete, THE Frontend_App SHALL no longer contain the useSegmentLock composable (composables/useSegmentLock.ts) and the useSegmentDrag composable (if exists)
8. WHEN the removal is complete, THE Frontend_App SHALL no longer contain the segment-related router entries (/segments, /segments/:id, /segments/:id/editor)
9. WHEN the removal is complete, THE Frontend_App SHALL remove all segment-related i18n keys from en-US.json, zh-CN.json, zh-TW.json (including nav.segments, nav.segmentLibrary, segment.* namespace, composite.segments, workspace.tabSegments, workspace.segment.* namespace)

### Requirement 5: 移除 Docxtemplater 服务中的 Segment 合并端点

**User Story:** As a 系统维护者, I want 移除 Docxtemplater Node.js 服务中的 /merge-segments 端点, so that 文档引擎不再提供段落合并能力。

#### Acceptance Criteria

1. WHEN the removal is complete, THE Merge_Segments_Endpoint SHALL be removed from docxtemplater-service/src/routes/merge-segments.js
2. WHEN the removal is complete, THE System SHALL remove the merge-segments route registration from docxtemplater-service/server.js
3. WHEN the removal is complete, THE System SHALL remove the merge-segments test file (docxtemplater-service/src/__tests__/merge-segments.test.js)

### Requirement 6: 清理前端组合模板工作区中的 Segment 引用

**User Story:** As a 系统维护者, I want 清理组合模板工作区中所有对 Segment 的引用, so that 模板工作区界面不再展示段落编排、段落编辑器和段落相关的测试/覆盖率信息。

#### Acceptance Criteria

1. WHEN the removal is complete, THE template-workspace Index.vue SHALL no longer contain the "segments" tab pane and SegmentArrangementTab component
2. WHEN the removal is complete, THE VisualEditorTab.vue SHALL no longer reference segment data, segment locks, or the "switchToSegments" event
3. WHEN the removal is complete, THE TestingTab.vue SHALL no longer display segmentResults or segmentCoverages data
4. WHEN the removal is complete, THE VersionHistoryPanel.vue SHALL no longer call refreshSegments()
5. WHEN the removal is complete, THE useWorkflowSteps composable SHALL no longer contain hasEnabledSegment() and allSegmentsEdited() functions, and the workflow steps SHALL no longer include the "segments" and "editor" steps
6. WHEN the removal is complete, THE templateWorkspace store SHALL no longer contain segments state, refreshSegments action, or import segment-related types and API functions

### Requirement 7: 清理 Segment 相关的 MinIO 存储文件

**User Story:** As a 系统维护者, I want 提供清理 MinIO 中 Segment 文件的迁移方案, so that 对象存储中不再保留孤立的段落文件。

#### Acceptance Criteria

1. THE System SHALL document a procedure to identify and remove Segment-related files from MinIO storage (files referenced by segments.file_path and segment_versions.file_path)
2. IF Segment files exist in MinIO, THEN THE System SHALL provide a safe cleanup script that removes only Segment-related files without affecting template files

### Requirement 8: 清理 Segment 相关的前端测试

**User Story:** As a 系统维护者, I want 移除所有 Segment 相关的前端测试文件, so that 测试套件不再包含已移除功能的测试。

#### Acceptance Criteria

1. WHEN the removal is complete, THE Frontend_App SHALL no longer contain SegmentIndex.test.ts
2. WHEN the removal is complete, THE Frontend_App SHALL no longer contain SegmentArrangementTab.test.ts
3. WHEN the removal is complete, THE Frontend_App SHALL no longer contain useSegmentLock.test.ts
4. WHEN the removal is complete, THE Frontend_App SHALL no longer contain useSegmentDrag.test.ts
5. WHEN the removal is complete, THE Frontend_App SHALL no longer contain workspace-segments-property.test.ts
6. WHEN the removal is complete, THE useWorkflowSteps property test SHALL be updated to remove segment-related generators and assertions

### Requirement 9: 确保编译和运行时完整性

**User Story:** As a 系统维护者, I want 确保移除 Segment Library 后系统能正常编译和运行, so that 移除操作不会引入编译错误或运行时异常。

#### Acceptance Criteria

1. WHEN all Segment-related code is removed, THE System SHALL compile without errors (backend Maven build and frontend Vite build)
2. WHEN all Segment-related code is removed, THE System SHALL pass all remaining unit tests and integration tests
3. WHEN all Segment-related code is removed, THE System SHALL start without runtime errors
4. IF any non-Segment code references a removed Segment class or method, THEN THE System SHALL update that reference to remove the dependency
5. WHEN all Segment-related code is removed, THE Frontend_App SHALL have no broken imports or unresolved references to removed modules

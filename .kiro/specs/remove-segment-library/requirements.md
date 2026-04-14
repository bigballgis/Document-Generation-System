# Requirements Document

## Introduction

本需求文档描述从文档生成系统（Document Generation System）中移除"段落库"（Segment Library）这一独立共享复用模块，同时保留组合模板（Composite Template）自身的段落编排功能。

核心变更思路：
- **移除**：段落库作为独立共享资源池的全部功能（独立 CRUD、版本控制、收藏、权限、审查、推荐、锁定、测试数据、重复分析、段落模板等）
- **保留并重构**：组合模板自身拥有的段落编排功能（assembly_config、段落排列、条件表达式、DataScope、渲染合并等），使段落从"引用共享库中的段落"变为"组合模板自己拥有的段落"

当前架构中，段落存储在独立的 `segments` 表中，组合模板通过 `assembly_config` JSONB 中的 `segmentId` 引用段落库中的记录。重构后，段落将内联存储在组合模板自身（如 assembly_config 中直接包含段落文件路径和元数据），不再依赖独立的 segments 表。

## Glossary

- **System**: 文档生成系统（Document Generation System）的整体应用
- **Segment_Library**: 段落库功能模块——作为独立共享资源池的段落管理系统，包含段落的独立 CRUD、版本控制、收藏、权限、审查、推荐、锁定等全部子功能。这是本次要移除的目标
- **Composite_Template**: 组合模板，通过 assembly_config 编排多个段落组装成完整文档。组合模板自身的段落编排功能要保留
- **Assembly_Config**: 组合模板的段落编排配置（JSONB），定义段落顺序、启用状态、分页、条件表达式、DataScope 等。属于组合模板自身功能，要保留并重构
- **Assembly_Engine**: 组装引擎，负责按 Assembly_Config 渲染和合并多个段落生成最终文档。属于组合模板自身功能，要保留并重构
- **Backend_API**: 后端 REST API 层
- **Frontend_App**: 前端 Vue 3 应用
- **Database**: PostgreSQL 数据库
- **Merge_Segments_Endpoint**: Docxtemplater Node.js 服务中的 /merge-segments 端点。属于组合模板自身的段落合并功能，要保留
- **Flyway_Migration**: 数据库迁移脚本，使用 Flyway 管理

## Requirements

### Requirement 1: 移除后端段落库核心 API

**User Story:** As a 系统维护者, I want 移除段落库的独立管理 API, so that 系统不再暴露共享段落库的管理接口。

#### Acceptance Criteria

1. WHEN the removal is complete, THE Backend_API SHALL no longer contain SegmentController (/api/segments) 及其全部端点（CRUD、克隆、升级/降级、版本、变量、收藏、锁定、权限、测试数据、重复分析、段落模板）
2. WHEN the removal is complete, THE Backend_API SHALL no longer contain SegmentReviewController (/api/segment-reviews) 及其全部端点
3. WHEN the removal is complete, THE System SHALL no longer contain以下段落库独立管理服务: SegmentService, SegmentVersionService, SegmentVariableService, SegmentLockService, SegmentPermissionService, SegmentTestService, SegmentRecommendationService, SegmentReviewService
4. WHEN the removal is complete, THE System SHALL no longer contain以下段落库仓库: SegmentRepository, SegmentFavoriteRepository, SegmentReviewRepository, SegmentTagMappingRepository, SegmentTestDataRepository, SegmentVersionRepository
5. WHEN the removal is complete, THE System SHALL no longer contain Segment 实体类及相关实体类: Segment, SegmentFavorite, SegmentReview, SegmentTagMapping, SegmentTestData, SegmentVersion
6. WHEN the removal is complete, THE System SHALL no longer contain段落库独立管理相关的 DTO 类: SegmentDTO, CreateSegmentRequest, UpdateSegmentRequest, SegmentVersionDTO, SegmentVariableDTO, SegmentTestDataDTO, SegmentTestResultDTO, SegmentReviewDTO, SegmentRecommendationDTO, SegmentStatsDTO, SegmentTemplateDTO, SegmentQueryRequest, CreateSegmentTemplateRequest, CreateSegmentTestDataRequest, DuplicateAnalysisDTO, LockInfo, SubmitCompositeReviewRequest, SegmentReviewerAssignment, ComponentRankingDTO, CompositeTestReportDTO
7. WHEN the removal is complete, THE System SHALL 保留并重构以下 DTO 类以适配内联段落模式: AssemblySegmentEntry（将 segmentId 替换为 filePath/name/segmentType）、AssemblyConfigDTO、CompositePreviewDTO、CompositeCoverageReport、SegmentRenderResult（重构为基于 filePath 而非 segmentId）、AssemblyResult、UpdateAssemblyConfigRequest、SelectivePreviewRequest、CompositeTestReportDTO（如果保留组合级测试则重构，否则移除）
8. WHEN the removal is complete, THE System SHALL no longer contain段落库相关的 ErrorCode 常量: SEGMENT_NOT_FOUND, SEGMENT_REFERENCED, SEGMENT_VERSION_FILE_MISSING, COMPONENT_REFERENCED（仅在 SegmentService.demoteFromComponent 中使用）
9. WHEN the removal is complete, THE System SHALL 保留 GENERATE_ALL_SEGMENTS_SKIPPED 和 COMPOSITE_TEMPLATE_EMPTY ErrorCode 常量（属于组合模板自身功能，在 AssemblyEngineService 和 CompositeTemplateService 中使用）

### Requirement 2: 移除数据库段落库相关表

**User Story:** As a 系统维护者, I want 通过 Flyway 迁移脚本安全地删除段落库的数据库表, so that 数据库中不再保留共享段落库的数据结构。

#### Acceptance Criteria

1. THE Flyway_Migration SHALL create a new migration script that drops the following segment library tables in correct dependency order: segment_favorites, segment_test_data, segment_reviews, segment_tag_mappings, segment_versions, segments
2. THE Flyway_Migration SHALL drop the segment-related indexes before dropping the tables
3. THE Flyway_Migration SHALL 清理 V32 添加的 permissions 表扩展：移除 resource_type 列中值为 'SEGMENT' 的记录，但保留 resource_type 和 resource_id 列本身（因为 'TEMPLATE' 类型的权限仍需使用这些列）
4. IF existing data references Segment records, THEN THE Flyway_Migration SHALL handle foreign key constraints by dropping dependent tables first

### Requirement 3: 重构组合模板的段落编排为自有段落模式

**User Story:** As a 系统维护者, I want 重构组合模板的段落编排功能使段落由组合模板自身拥有, so that 组合模板不再依赖共享段落库而是管理自己的段落。

#### Acceptance Criteria

1. THE Assembly_Config JSONB 结构 SHALL 将段落信息从引用模式（segmentId 引用 segments 表）重构为内联模式，每个段落条目直接包含 filePath（MinIO 文件路径）、name、segmentType 等元数据
2. THE templates 表 SHALL 保留 assembly_config 列和 template_type 列，因为这些属于组合模板自身功能
3. WHEN the refactoring is complete, THE AssemblyConfigService SHALL 不再依赖 SegmentRepository 进行验证，改为基于内联段落数据进行验证（如检查 filePath 非空）
4. WHEN the refactoring is complete, THE AssemblyEngineService SHALL 不再通过 SegmentRendererService 和 SegmentRepository 获取段落文件，改为直接从 Assembly_Config 中的内联 filePath 读取 MinIO 文件并渲染
5. WHEN the refactoring is complete, THE AssemblyEngineService SHALL 不再依赖 SegmentDataScopeService（DataScope 逻辑可内联到 AssemblyEngineService 中），不再依赖 SegmentRendererService（渲染逻辑可直接调用 Docxtemplater /render 端点）
6. WHEN the refactoring is complete, THE CompositeTemplateService SHALL 不再依赖 SegmentRepository 和 DependencyGraphService，preview 和 activate 方法改为基于内联段落数据工作
7. WHEN the refactoring is complete, THE CompositeGeneratorService SHALL 继续通过 AssemblyEngineService 组装文档，但不再间接依赖 SegmentRepository
8. THE Flyway_Migration SHALL create a data migration script that converts existing assembly_config JSONB from reference mode (segmentId) to inline mode (filePath + name + segmentType), by looking up the segments table before it is dropped

### Requirement 4: 移除组合模板中的段落库依赖端点

**User Story:** As a 系统维护者, I want 移除 CompositeTemplateController 中依赖段落库的端点, so that 组合模板 API 不再暴露段落库相关功能。

#### Acceptance Criteria

1. WHEN the removal is complete, THE CompositeTemplateController SHALL no longer contain GET /{id}/segments 端点（该端点通过 DependencyGraphService 从 segments 表查询段落，属于段落库依赖）
2. WHEN the removal is complete, THE CompositeTemplateController SHALL no longer contain POST /{id}/reviews 和 GET /{id}/reviews/{reviewId}/segments 端点（这些端点依赖 SegmentReviewService 进行段落级审查，属于段落库功能）
3. WHEN the removal is complete, THE CompositeTemplateController SHALL no longer contain GET /{id}/recommendations 端点（该端点依赖 SegmentRecommendationService 从段落库推荐段落）
4. WHEN the removal is complete, THE CompositeTemplateController SHALL no longer contain POST /{id}/tests/run 端点（该端点依赖 SegmentTestService 运行段落级测试）
5. THE CompositeTemplateController SHALL 保留以下属于组合模板自身功能的端点: POST / (创建), GET /{id}/assembly-config (获取编排配置), PUT /{id}/assembly-config (更新编排配置), POST /{id}/preview (预览), POST /{id}/preview/selective (选择性预览), GET /{id}/coverage (覆盖率)
6. THE CompositeTemplateController SHALL 保留 import/export 端点，但 CompositeImportExportService 需要重构为基于内联段落数据工作，不再依赖 SegmentRepository

### Requirement 5: 移除段落库相关的后端辅助服务

**User Story:** As a 系统维护者, I want 移除仅服务于段落库的后端辅助服务, so that 系统不再包含段落库的辅助功能代码。

#### Acceptance Criteria

1. WHEN the removal is complete, THE System SHALL no longer contain DependencyGraphService（该服务的核心功能是查询 segments 表与 assembly_config 的引用关系，属于段落库依赖图功能）
2. WHEN the removal is complete, THE System SHALL no longer contain SegmentRendererService（该服务通过 SegmentRepository 和 SegmentVersionRepository 获取段落文件路径并渲染，属于段落库依赖）
3. WHEN the removal is complete, THE System SHALL no longer contain SegmentDataScopeService（其 DataScope 解析逻辑可内联到重构后的 AssemblyEngineService 中）
4. WHEN the removal is complete, THE CompositeCoverageService SHALL 被重构为不再依赖 SegmentVariableService 和 DependencyGraphService，改为基于内联段落数据计算覆盖率
5. WHEN the removal is complete, THE Dashboard_Service SHALL no longer contain getSegmentStats() 方法和 getComponentRanking() 方法，DashboardController SHALL no longer contain /segment-stats 和 /component-ranking 端点
6. WHEN the removal is complete, THE DashboardService.getSystemOverview() SHALL 移除对 SegmentRepository 的依赖（当前调用 segmentRepository.count() 和 segmentRepository.countByComponent(true)），SystemOverviewDTO 中的 segmentCount、componentCount 字段应移除
7. WHEN the removal is complete, THE DashboardService SHALL 移除对 DependencyGraphService 的构造器注入依赖

### Requirement 6: 重构前端段落编排界面为自有段落模式

**User Story:** As a 系统维护者, I want 重构前端组合模板工作区的段落编排界面, so that 用户在组合模板中直接管理自有段落而非引用段落库。

#### Acceptance Criteria

1. THE SegmentArrangementTab.vue SHALL 保留段落排列、拖拽排序、启用/禁用、分页设置、条件表达式、DataScope 等编排功能
2. WHEN the refactoring is complete, THE SegmentArrangementTab.vue SHALL 将"创建新段落"功能从调用段落库 API (createSegment) 改为直接上传 .docx 文件并将段落信息内联存储到 assembly_config 中
3. WHEN the refactoring is complete, THE SegmentArrangementTab.vue SHALL 移除"添加已有段落"对话框（该功能搜索段落库中的共享段落，属于段落库依赖）
4. WHEN the refactoring is complete, THE templateWorkspace store SHALL 移除 segments state 和 refreshSegments action（这些从 /composite-templates/{id}/segments 端点获取段落库数据），段落数据改为从 assemblyConfig 中直接获取
5. THE useAssemblyConfig composable SHALL 保留，因为它管理的是组合模板自身的段落编排状态（undo/redo、添加/删除/更新段落条目）
6. THE useSegmentDrag composable SHALL 保留，因为它管理的是组合模板自身的段落拖拽排序功能
7. THE useWorkflowSteps composable SHALL 保留 segments 和 editor 步骤，但 hasEnabledSegment() 和 allSegmentsEdited() 函数需要适配新的内联段落数据结构

### Requirement 7: 移除前端段落库独立管理页面

**User Story:** As a 系统维护者, I want 移除前端段落库的独立管理页面和组件, so that 用户界面不再展示共享段落库的管理入口。

#### Acceptance Criteria

1. WHEN the removal is complete, THE Frontend_App SHALL no longer contain the segment library management pages: views/segments/Index.vue, views/segments/Detail.vue, views/segments/Editor.vue
2. WHEN the removal is complete, THE Frontend_App SHALL no longer contain the segment form components: views/segments/components/SegmentFormDialog.vue, SegmentPermissionDialog.vue, SegmentVariableList.vue, SegmentVersionList.vue
3. WHEN the removal is complete, THE Frontend_App SHALL no longer contain the segment API module (api/segments.ts)
4. WHEN the removal is complete, THE Frontend_App SHALL no longer contain the segment Pinia store (stores/segment.ts)
5. WHEN the removal is complete, THE Frontend_App SHALL no longer contain the useSegmentLock composable (composables/useSegmentLock.ts)
6. WHEN the removal is complete, THE Frontend_App SHALL no longer contain the segment-related router entries (/segments, /segments/:id, /segments/:id/editor)
7. WHEN the removal is complete, THE Frontend_App SHALL remove segment library related i18n keys from en-US.json, zh-CN.json, zh-TW.json (including nav.segments, nav.segmentLibrary, segment.* namespace)，但保留 workspace.segment.* 和 assembly.* 等属于组合模板自身段落编排的 i18n keys

### Requirement 8: 重构前端类型定义和 API 调用

**User Story:** As a 系统维护者, I want 重构前端类型定义和 API 调用层, so that 类型系统反映新的内联段落数据模型。

#### Acceptance Criteria

1. THE types/segment.ts SHALL 移除段落库独立管理相关的类型: Segment, SegmentVersion, SegmentVariable, SegmentReview, SegmentReviewStatus, SegmentTestData, SegmentTestResult, LockInfo, SegmentRecommendation, DuplicatePair, DuplicateAnalysis, SegmentTemplate, SegmentPermission, GrantSegmentPermissionRequest, SegmentQuery, CreateSegmentRequest, UpdateSegmentRequest, CreateSegmentTestDataRequest, CreateSegmentTemplateRequest（注意：SegmentType 枚举保留，因为 AssemblySegmentEntry 使用）
2. THE types/segment.ts SHALL 保留并重构 AssemblySegmentEntry 类型，将 segmentId 替换为 filePath、name、segmentType 等内联字段
3. THE types/segment.ts SHALL 保留 AssemblyConfig, CreateCompositeTemplateRequest, UpdateAssemblyConfigRequest, SelectivePreviewRequest, CompositePreview, SegmentPreviewEntry, CompositeCoverageReport, SegmentCoverageEntry, MigrationResult 等属于组合模板自身功能的类型
4. THE types/segment.ts SHALL 移除依赖段落库审查/测试功能的类型: SubmitCompositeReviewRequest, SegmentReviewerAssignment, ReviewActionRequest, CompositeTestReport
5. THE api/composite-templates.ts SHALL 移除依赖段落库的 API 函数: getCompositeSegments, submitCompositeReview, getSegmentReviews, approveSegmentReview, rejectSegmentReview, getSegmentRecommendations, runAllCompositeTests
6. THE api/composite-templates.ts SHALL 保留属于组合模板自身功能的 API 函数: createCompositeTemplate, getAssemblyConfig, updateAssemblyConfig, previewCompositeTemplate, previewSelectiveSegments, getCompositeCoverage, exportCompositeAsZip, exportCompositeConfig, importCompositeFromZip, migrateToComposite

### Requirement 9: 清理前端组合模板工作区中的段落库引用

**User Story:** As a 系统维护者, I want 清理组合模板工作区中对段落库的引用, so that 工作区界面仅使用组合模板自有段落数据。

#### Acceptance Criteria

1. THE template-workspace Index.vue SHALL 保留 segments tab pane 和 SegmentArrangementTab 组件（属于组合模板自身的段落编排功能）
2. WHEN the cleanup is complete, THE VisualEditorTab.vue SHALL 不再引用段落库的锁定功能（segment locks），但保留 "switchToSegments" 事件（用于切换到段落编排 tab）
3. WHEN the cleanup is complete, THE TestingTab.vue SHALL 移除 segmentResults 和 segmentCoverages 中依赖段落库的数据展示，覆盖率数据改为基于内联段落
4. WHEN the cleanup is complete, THE VersionHistoryPanel.vue SHALL 不再调用 refreshSegments()（该方法从段落库获取数据），版本历史改为仅关注模板自身版本

### Requirement 10: 保留 Docxtemplater 服务中的段落合并端点

**User Story:** As a 系统维护者, I want 保留 Docxtemplater 服务中的 /merge-segments 端点, so that 组合模板的段落合并渲染功能继续正常工作。

#### Acceptance Criteria

1. THE Merge_Segments_Endpoint (/merge-segments) SHALL 继续存在于 docxtemplater-service 中，因为它是组合模板自身段落合并功能的基础设施
2. THE AssemblyEngineService 重构后 SHALL 继续调用 /merge-segments 端点合并渲染后的段落文档

### Requirement 11: 移除段落库相关的前端和后端测试

**User Story:** As a 系统维护者, I want 移除段落库相关的测试文件并更新受影响的测试, so that 测试套件不再包含已移除功能的测试。

#### Acceptance Criteria

1. WHEN the removal is complete, THE Frontend_App SHALL no longer contain SegmentIndex.test.ts
2. WHEN the removal is complete, THE Frontend_App SHALL no longer contain useSegmentLock.test.ts
3. THE useSegmentDrag.test.ts SHALL 保留，因为 useSegmentDrag composable 属于组合模板自身的段落拖拽排序功能
4. THE workspace-segments-property.test.ts SHALL 被更新以反映新的内联段落模式（当前测试基于 segmentId 引用模式的 mergeSegments 函数，需要适配内联模式）
5. THE SegmentArrangementTab.test.ts SHALL 被更新以反映新的内联段落模式（移除对段落库 API 的 mock，改为测试内联段落的创建和编排）
6. THE useWorkflowSteps property test (composables/useWorkflowSteps.property.test.ts) SHALL be updated to adapt generators and assertions to the new inline segment data structure
7. THE useAssemblyConfig.test.ts SHALL 保留，因为它测试的是组合模板自身的段落编排逻辑
8. WHEN the removal is complete, THE Backend SHALL no longer contain以下段落库单元测试: SegmentServiceTest, SegmentLockServiceTest
9. WHEN the removal is complete, THE Backend SHALL no longer contain以下段落库 Property-Based 测试: SegmentVersionPropertyTest, SegmentTenantIsolationPropertyTest, SegmentServicePropertyTest, SegmentLockPropertyTest, SegmentEmptyDocxPropertyTest
10. WHEN the removal is complete, THE Backend SHALL no longer contain以下段落库集成测试: SegmentEntityIntegrationTest, SegmentControllerIntegrationTest
11. THE Backend 的 AssemblyConfigServiceTest SHALL 被更新以移除对 SegmentRepository mock 的依赖，改为测试基于内联段落数据的验证逻辑
12. THE Backend 的 SegmentAssemblyConfigPropertyTest SHALL 被更新以适配新的内联段落数据结构（移除 segmentId 验证，改为 filePath 验证）
13. THE Backend 的 CompositeCoveragePropertyTest SHALL 被更新以移除对 SegmentVariableService 和 DependencyGraphService 的 mock，改为基于内联段落数据的覆盖率计算
14. THE Backend 的 AssemblyOrderPropertyTest 和 ConditionalRenderingPropertyTest SHALL 被更新以移除对 SegmentRendererService 的 mock，改为基于内联 filePath 的渲染逻辑
15. THE Backend 的 CompositeImportExportServiceTest SHALL 被更新以移除对 SegmentRepository 的 mock，改为基于内联段落数据的导入导出逻辑
16. THE Backend 的 CompositeTemplateControllerIntegrationTest SHALL 被更新以移除段落库依赖端点的测试用例

### Requirement 12: 数据迁移与 MinIO 文件处理

**User Story:** As a 系统维护者, I want 安全地迁移现有数据从段落库引用模式到内联模式, so that 现有组合模板在移除段落库后继续正常工作。

#### Acceptance Criteria

1. THE Flyway_Migration SHALL 在删除 segments 表之前，先执行数据迁移：将所有 templates.assembly_config 中的 segmentId 引用解析为对应的 segments 记录，将 filePath、name、segmentType 等信息内联写入 assembly_config
2. IF a segmentId in assembly_config references a non-existent segment, THEN THE Flyway_Migration SHALL log a warning and mark that segment entry as invalid in the migrated assembly_config
3. THE System SHALL 保留 MinIO 中被组合模板 assembly_config 引用的段落文件（这些文件迁移后由组合模板直接拥有）
4. THE System SHALL document a procedure to identify and remove MinIO 中仅被段落库引用但未被任何组合模板使用的孤立段落文件

### Requirement 13: 确保编译和运行时完整性

**User Story:** As a 系统维护者, I want 确保移除段落库并重构后系统能正常编译和运行, so that 变更不会引入编译错误或运行时异常。

#### Acceptance Criteria

1. WHEN all changes are complete, THE System SHALL compile without errors (backend Maven build and frontend Vite build)
2. WHEN all changes are complete, THE System SHALL pass all remaining unit tests and integration tests
3. WHEN all changes are complete, THE System SHALL start without runtime errors
4. IF any non-segment-library code references a removed class or method, THEN THE System SHALL update that reference to remove the dependency or use the refactored alternative
5. WHEN all changes are complete, THE Frontend_App SHALL have no broken imports or unresolved references to removed modules
6. WHEN all changes are complete, THE composite template creation, segment arrangement, preview, rendering, and export/import workflows SHALL function correctly with the new inline segment model

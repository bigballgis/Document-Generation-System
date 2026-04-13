# 实施计划: 模板分段/组件化

## 概述

本实施计划将模板分段/组件化功能拆分为渐进式的编码任务。从数据库迁移和核心实体开始，逐步构建段落管理、组合模板、渲染引擎、扩展服务和前端界面。每个任务构建在前一个任务之上，确保增量可验证。

## 任务

- [ ] 1. 数据库迁移与核心实体
  - [x] 1.1 创建 Flyway 迁移脚本 V29-V35
    - 创建 `V29__create_segments_and_extend_templates.sql`: 扩展 templates 表添加 `template_type` 和 `assembly_config` 列，创建 `segments` 表及索引
    - 创建 `V30__create_segment_versions.sql`: 创建 `segment_versions` 表及唯一约束
    - 创建 `V31__create_segment_tag_mappings.sql`: 创建 `segment_tag_mappings` 表
    - 创建 `V32__extend_permissions_for_segments.sql`: 扩展 `permissions` 表添加 `resource_type` 和 `resource_id` 列
    - 创建 `V33__create_segment_reviews.sql`: 创建 `segment_reviews` 表
    - 创建 `V34__create_segment_test_data.sql`: 创建 `segment_test_data` 表
    - 创建 `V35__create_segment_favorites.sql`: 创建 `segment_favorites` 表
    - _需求: 1.1, 1.2, 2.3, 4.1, 10.1, 11.1, 14.2, 23.4_

  - [x] 1.2 创建 JPA 实体类
    - 创建 `Segment` 实体（含 `@Filter(name = "tenantFilter")`、`@PrePersist`、`@PreUpdate`）
    - 创建 `SegmentVersion` 实体（含 `segment_id` + `version_number` 唯一约束）
    - 创建 `SegmentTagMapping` 实体
    - 创建 `SegmentReview` 实体
    - 创建 `SegmentTestData` 实体
    - 创建 `SegmentFavorite` 实体
    - 扩展 `Template` 实体添加 `templateType` 和 `assemblyConfig` 字段
    - 扩展 `Permission` 实体添加 `resourceType` 和 `resourceId` 字段
    - _需求: 1.1, 1.2, 1.3, 2.3, 2.11, 4.1, 9.2, 10.1_

  - [x] 1.3 创建 Repository 接口
    - 创建 `SegmentRepository`（含按 tenant_id、is_component、name 模糊搜索等查询方法）
    - 创建 `SegmentVersionRepository`（含按 segment_id 查询、获取最大版本号方法）
    - 创建 `SegmentTagMappingRepository`
    - 创建 `SegmentReviewRepository`
    - 创建 `SegmentTestDataRepository`
    - 创建 `SegmentFavoriteRepository`
    - _需求: 1.5, 4.2, 11.1, 14.2, 23.4_

  - [x] 1.4 编写实体层集成测试
    - 使用 Testcontainers + PostgreSQL 验证 Flyway 迁移脚本执行成功
    - 验证所有实体的 CRUD 操作和约束（唯一约束、外键约束）
    - 验证 Hibernate tenantFilter 对 Segment 的租户隔离
    - _需求: 1.3, 2.10_

- [ ] 2. 创建核心 DTO 和 ErrorCode 扩展
  - [x] 2.1 创建段落相关 DTO
    - 创建 `SegmentDTO`、`CreateSegmentRequest`（含 `@NotBlank`、`@Size` 校验）、`UpdateSegmentRequest`
    - 创建 `SegmentVersionDTO`、`VersionDiffResult`
    - 创建 `SegmentQueryRequest`（含名称搜索、标签筛选、分类筛选、类型筛选参数）
    - 创建 `SegmentVariableDTO`、`SegmentReviewDTO`、`SegmentTestDataDTO`
    - 创建 `LockInfo` DTO
    - _需求: 1.1, 1.5, 4.1, 4.5, 6.2, 10.1, 22.3_

  - [x] 2.2 创建组合模板相关 DTO
    - 创建 `CreateCompositeTemplateRequest`、`UpdateAssemblyConfigRequest`
    - 创建 `AssemblyConfigDTO`、`AssemblySegmentEntry`（含 segmentId、position、enabled、pageBreakBefore、lockedVersion、conditionExpression、dataScope）
    - 创建 `CompositePreviewDTO`、`AssemblyResult`、`SegmentRenderResult`
    - 创建 `MigrationResultDTO`
    - 创建 `CompositeCoverageReport`（含每个 Segment 独立覆盖率 + 整体覆盖率）
    - 创建 `DuplicateAnalysisDTO`、`SegmentRecommendationDTO`
    - _需求: 2.1, 2.3, 5.1, 6.3, 7.7, 9.4, 21.4_

  - [x] 2.3 扩展 ErrorCode 枚举
    - 新增 `SEGMENT_REFERENCED`（409）、`COMPONENT_REFERENCED`（409）
    - 新增 `COMPOSITE_TEMPLATE_EMPTY`（422）、`SEGMENT_NOT_FOUND`（422）
    - 新增 `SEGMENT_VERSION_FILE_MISSING`、`MIGRATION_FILE_ACCESS_FAILED`
    - 新增 `GENERATE_ALL_SEGMENTS_SKIPPED`（200）、`IMPORT_INVALID_FILE`
    - _需求: 1.7, 2.8, 2.9, 3.8, 4.8, 8.8, 9.8, 12.4_

- [ ] 3. 段落 CRUD 核心服务
  - [x] 3.1 实现 SegmentService
    - 实现 `createSegment`: 上传 .docx 到 MinIO（路径 `segments/{tenantId}/{uuid}_{filename}`），持久化元数据，记录审计日志
    - 实现 `updateSegment`: 更新文件和元数据，调用 SegmentVersionService 创建新版本
    - 实现 `deleteSegment`: 检查引用关系（DependencyGraphService），被引用时返回 `SEGMENT_REFERENCED` (409)。注意: DependencyGraphService 在任务 6.1 中实现，此处先实现基础删除逻辑，引用检查在 6.1 完成后集成
    - 实现 `listSegments`: 分页查询，支持名称模糊搜索、标签筛选、分类筛选
    - 实现 `getSegment`、`cloneSegment`（MinIO CopyObject + 名称加"- 副本"后缀）
    - 实现 `promoteToComponent`、`demoteFromComponent`（引用数检查在 6.1 完成后集成）
    - _需求: 1.1, 1.2, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10, 1.11, 3.1, 3.6, 3.7, 3.8_

  - [x] 3.2 编写 SegmentService 属性测试 — Property 2: 组件引用完整性
    - **Property 2: componentReferenceIntegrity**
    - 随机创建引用关系，验证被引用的 Segment 删除被拒绝（SEGMENT_REFERENCED）
    - 验证引用计数与实际引用数一致
    - **验证: 需求 1.7, 3.8**

  - [x] 3.3 编写 SegmentService 属性测试 — Property 5: 租户隔离性
    - **Property 5: segmentTenantIsolation**
    - 创建多租户 Segment 数据，切换租户上下文后验证查询结果不包含其他租户的 Segment
    - **验证: 需求 1.3, 2.10**

  - [x] 3.4 实现 SegmentController
    - 实现段落管理 API 端点: POST/GET/PUT/DELETE `/api/segments`、`/api/segments/{id}`
    - 实现 `/api/segments/{id}/clone`、`/api/segments/{id}/promote`、`/api/segments/{id}/demote`
    - 实现 `/api/segments/{id}/favorite`（收藏/取消收藏）
    - 使用 `@Valid` 校验请求参数
    - _需求: 1.1-1.11, 3.1, 3.6, 3.7, 23.4_

  - [x] 3.5 编写 SegmentController 集成测试
    - 使用 Testcontainers 验证段落 CRUD API 端点
    - 验证删除被引用段落返回 409
    - 验证租户隔离
    - _需求: 1.1-1.11_

- [ ] 4. 段落版本管理服务
  - [x] 4.1 实现 SegmentVersionService
    - 实现 `createVersion`: 版本号严格递增，复制文件到 `segment-versions/{tenantId}/{segmentId}/{versionNumber}_{uuid}.docx`
    - 实现 `listVersions`: 按版本号降序分页查询
    - 实现 `rollbackToVersion`: 基于历史版本创建新版本（版本号继续递增）
    - 实现 `compareVersions`: 复用 VersionDiffService 的差异检测逻辑
    - 记录审计日志
    - _需求: 4.1, 4.2, 4.3, 4.4, 4.5, 4.9_

  - [x] 4.2 编写 SegmentVersionService 属性测试 — Property 3: 版本号严格递增
    - **Property 3: segmentVersionMonotonicallyIncreasing**
    - 对同一 Segment 执行随机次数的保存和回滚操作，验证版本号序列严格递增
    - **验证: 需求 4.1, 4.4**

  - [x] 4.3 实现段落版本 API 端点
    - 在 SegmentController 中添加版本相关端点
    - GET `/api/segments/{id}/versions`、POST `/api/segments/{id}/rollback/{versionId}`
    - GET `/api/segments/{id}/versions/diff`（query: versionA, versionB）
    - _需求: 4.2, 4.3, 4.5_

- [x] 5. 检查点 — 确保所有测试通过
  - 确保所有测试通过，如有问题请询问用户。

- [ ] 6. 引用关系与编辑锁服务
  - [x] 6.1 实现 DependencyGraphService
    - 实现 `getReferencingTemplates`: 查询 assembly_config JSONB 中引用了指定 segmentId 的所有 Composite_Template
    - 实现 `getReferenceCount`、`isReferenced`
    - 实现 `getSegmentsForTemplate`: 从 assembly_config 中提取所有 segment 引用
    - 集成到 SegmentService: 补充 deleteSegment 的引用检查和 demoteFromComponent 的引用数检查
    - 实现 Component_Template 变更通知: 当 Component_Template 创建新版本时，向所有引用方的设计者发送系统内通知
    - _需求: 3.4, 3.5, 3.9, 4.6_

  - [x] 6.2 实现 SegmentLockService
    - 实现 Redis 分布式编辑锁: `acquireLock`、`releaseLock`、`renewLock`、`getLockInfo`、`getBatchLockInfo`
    - Key 格式: `segment-lock:{segmentId}`，TTL 30 分钟
    - _需求: 22.1, 22.2, 22.3, 22.4, 22.5_

  - [x] 6.3 编写 SegmentLockService 属性测试 — Property 8: 编辑锁互斥性
    - **Property 8: editLockMutualExclusion**
    - 并发模拟多用户同时获取同一 Segment 的编辑锁，验证只有一个成功
    - **验证: 需求 22.3**

  - [x] 6.4 实现编辑锁 API 端点
    - POST `/api/segments/{id}/lock`、DELETE `/api/segments/{id}/lock`
    - POST `/api/segments/{id}/lock/renew`、GET `/api/segments/{id}/lock`
    - _需求: 22.3, 22.4_

- [ ] 7. 组合模板管理服务
  - [x] 7.1 实现 AssemblyConfigService
    - 实现 Assembly_Config JSONB 的序列化/反序列化
    - 实现验证逻辑: 至少一个启用的 Segment、所有 Segment ID 存在
    - _需求: 2.3, 2.8, 2.9_

  - [x] 7.2 编写 AssemblyConfigService 属性测试 — Property 9: Assembly_Config 验证完备性
    - **Property 9: assemblyConfigValidation**
    - 生成包含无效 ID 和全部禁用的随机 Assembly_Config，验证保存被拒绝
    - **验证: 需求 2.8, 2.9**

  - [x] 7.3 实现 CompositeTemplateService
    - 实现 `createCompositeTemplate`: template_type = COMPOSITE，复用 Template 实体和状态机
    - 实现 `updateAssemblyConfig`: 调用 AssemblyConfigService 验证后更新
    - 实现 `activateCompositeTemplate`: 验证所有引用的 Segment 存在且可访问
    - 实现 `getAssemblyConfig`、`previewCompositeTemplate`
    - _需求: 2.1, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 2.11, 2.12, 2.13_

  - [x] 7.4 实现 CompositeTemplateController
    - POST `/api/composite-templates`
    - GET/PUT `/api/composite-templates/{id}/assembly-config`
    - POST `/api/composite-templates/{id}/preview`
    - POST `/api/composite-templates/{id}/preview/selective`
    - GET `/api/composite-templates/{id}/segments`
    - _需求: 2.1-2.13_

  - [x] 7.5 编写 CompositeTemplateController 集成测试
    - 验证组合模板创建、Assembly_Config 更新和验证逻辑
    - 验证激活时 Segment 存在性检查
    - _需求: 2.1, 2.8, 2.9, 2.12_

- [x] 8. 检查点 — 确保所有测试通过
  - 确保所有测试通过，如有问题请询问用户。

- [ ] 9. 段落变量扫描与覆盖率服务
  - [x] 9.1 实现 SegmentVariableService
    - 实现 `scanVariables`: 复用 TemplateVariableService.extractVariableNames 逻辑，从 .docx ZIP 中提取 word/*.xml 并匹配 Docxtemplater 变量模式
    - 实现 `listVariables`: 返回段落变量列表
    - _需求: 6.1, 6.2_

  - [x] 9.2 实现 CompositeCoverageService
    - 实现 `checkCoverage`: 汇总所有 Segment 变量，计算每个 Segment 独立覆盖率 + 整体覆盖率
    - 实现 `isBelowThreshold`: 复用 CoverageCheckService 的阈值检查逻辑
    - _需求: 6.3, 6.4, 6.5_

  - [x] 9.3 编写 CompositeCoverageService 属性测试 — Property 10: 覆盖率计算正确性
    - **Property 10: compositeCoverageCalculation**
    - 创建包含随机变量绑定状态的多段落组合模板，验证聚合覆盖率 = Σ(boundVars) / Σ(totalVars) × 100%
    - **验证: 需求 6.3**

  - [x] 9.4 实现段落变量和覆盖率 API 端点
    - GET `/api/segments/{id}/variables`
    - GET `/api/composite-templates/{id}/coverage`
    - _需求: 6.2, 6.4_

- [ ] 10. 段落数据作用域服务
  - [x] 10.1 实现 SegmentDataScopeService
    - 实现数据作用域映射逻辑: 从全局数据上下文中按 DataScope 配置提取子集
    - 未配置 DataScope 时传递完整数据上下文（向后兼容）
    - 映射的全局变量不存在时记录警告日志并设置为 null
    - _需求: 5.1, 5.2, 5.3, 5.4, 5.6_

  - [x] 10.2 编写 SegmentDataScopeService 属性测试 — Property 4: 数据作用域隔离性
    - **Property 4: dataScopeIsolation**
    - 生成随机全局数据和 DataScope 映射，验证渲染时传入的数据仅包含映射的子集
    - **验证: 需求 5.3, 5.4**

- [ ] 11. 段落渲染与组装引擎
  - [x] 11.1 实现 SegmentRendererService
    - 实现 `renderSegment`: 通过 CircuitBreaker 调用 Docxtemplater `/render` 端点渲染单个 Segment
    - 实现 `renderSegmentSafe`: 捕获异常，返回包含错误信息的 SegmentRenderResult（部分失败模式）
    - 支持版本锁定: 根据 lockedVersion 从 MinIO 获取指定版本的 .docx 文件
    - _需求: 7.1, 7.2, 7.7, 8.6, 8.11_

  - [x] 11.2 实现 AssemblyEngineService
    - 实现 `assembleDocument`: 遍历 Assembly_Config → 计算条件表达式 → 应用 DataScope → 渲染各 Segment → 调用 `/merge-segments` 合并
    - 条件表达式为 false 的 Segment 跳过渲染
    - 所有 Segment 均被跳过时返回 `GENERATE_ALL_SEGMENTS_SKIPPED`
    - 返回合并文档和各 Segment 渲染耗时统计
    - _需求: 7.5, 7.6, 8.1, 8.2, 8.3, 8.8, 8.9_

  - [x] 11.3 编写 AssemblyEngineService 属性测试 — Property 1: 段落顺序保持性
    - **Property 1: segmentOrderPreservedAfterAssembly**
    - 生成随机排列的 Segment 列表，渲染合并后验证内容顺序与 position 一致
    - **验证: 需求 8.1, 8.2**

  - [x] 11.4 编写 AssemblyEngineService 属性测试 — Property 6: 条件渲染一致性
    - **Property 6: conditionalRenderingConsistency**
    - 生成随机条件表达式和数据，验证合并文档中的段落与条件计算结果一致
    - **验证: 需求 2.5, 7.6, 8.8**

- [ ] 12. Docxtemplater 服务扩展 — /merge-segments 端点
  - [x] 12.1 实现 /merge-segments 端点
    - 在 `docxtemplater-service/` 中新增 `POST /merge-segments` 端点
    - 接收 segments 数组（每项含 base64 buffer 和 pageBreakBefore 标志）
    - 使用 docx 操作库合并多个 .docx 文件，支持分页符插入
    - 返回合并后的 .docx 文件（binary）
    - _需求: 8.2, 8.3_

  - [x] 12.2 编写 /merge-segments 端点测试
    - 使用 Jest 验证多段落合并、分页符插入、空段落处理
    - _需求: 8.2, 8.3_

- [ ] 13. 组合文档生成服务
  - [x] 13.1 实现 CompositeGeneratorService
    - 实现 `generateCompositeDocument`: 执行数据管道 → 调用 AssemblyEngineService → 应用水印 → 存储文档
    - 复用 DataAggregationService、WatermarkService、DocumentStorageService
    - 响应元数据包含各 Segment 渲染耗时统计
    - _需求: 8.1, 8.4, 8.5, 8.9, 8.10, 16.1, 16.2_

  - [x] 13.2 扩展 DocumentGeneratorService 路由逻辑
    - 在现有 `generateDocument` 方法中根据 `template_type` 字段路由: SINGLE → 现有流程，COMPOSITE → CompositeGeneratorService
    - 确保统一 API 端点 `POST /api/generate/{templateId}` 对两种模板类型透明
    - _需求: 8.7, 9.1, 9.2_

  - [x] 13.3 扩展 Webhook payload 支持段落级渲染状态
    - 在 WebhookService 的通知 payload 中添加 Composite_Template 特有信息: 各 Segment 渲染状态和耗时
    - _需求: 8.12, 13.3, 13.4_

- [x] 14. 检查点 — 确保所有测试通过
  - 确保所有测试通过，如有问题请询问用户。

- [ ] 15. 迁移工具服务
  - [x] 15.1 实现 MigrationService
    - 实现 `migrateToComposite`: 读取原始 .docx → 创建单个 Segment → 创建 Composite_Template + Assembly_Config → 迁移数据源/表达式/变量绑定 → 归档原始模板 → 记录审计日志
    - 原始模板 .docx 无法读取时返回 `MIGRATION_FILE_ACCESS_FAILED`
    - _需求: 9.3, 9.4, 9.5, 9.6, 9.7, 9.8, 9.9_

  - [x] 15.2 编写 MigrationService 属性测试 — Property 7: 迁移往返一致性
    - **Property 7: migrationPreservesConfiguration**
    - 对随机模板执行迁移，验证迁移后配置（数据源、表达式、变量绑定）完整保留
    - **验证: 需求 9.4, 9.6**

  - [x] 15.3 实现迁移 API 端点
    - POST `/api/templates/{id}/migrate-to-composite`
    - _需求: 9.3_

- [ ] 16. 段落权限与审查服务
  - [x] 16.1 实现 SegmentPermissionService
    - 复用 PermissionService，通过 `resource_type = 'SEGMENT'` 和 `resource_id = segmentId` 实现段落级权限
    - 实现权限查询、分配、撤销
    - 编辑 Segment 时验证 EDIT 权限，无权限返回 403 (`AUTH_ACCESS_DENIED`)
    - _需求: 10.1, 10.2, 10.3, 10.5, 10.6_

  - [x] 16.2 实现 SegmentReviewService
    - 实现 `createSegmentReviews`: 为每个 Segment 分配独立审查人
    - 实现 `approveSegmentReview`、`rejectSegmentReview`
    - 实现 `checkAndUpdateCompositeReviewStatus`: 所有通过 → 整体通过；任一驳回 → 整体驳回
    - _需求: 11.1, 11.2, 11.3, 11.4, 11.5_

  - [x] 16.3 实现段落权限和审查 API 端点
    - 权限: GET/POST/DELETE `/api/segments/{id}/permissions`
    - 审查: POST `/api/composite-templates/{id}/reviews`、GET 段落级审查状态
    - PUT `/api/segment-reviews/{id}/approve`、PUT `/api/segment-reviews/{id}/reject`
    - _需求: 10.6, 11.1-11.5_

- [ ] 17. 导入导出与测试服务
  - [x] 17.1 实现 CompositeImportExportService
    - 实现 `exportAsZip`: 打包所有 Segment .docx + config.json 为 ZIP
    - 实现 `exportConfig`: 仅导出 JSON 配置
    - 实现 `importFromZip`: 验证 ZIP 结构 → 创建 Segments → 创建 Composite_Template；Component_Template 同名存在则关联
    - _需求: 12.1, 12.2, 12.3, 12.4, 12.5_

  - [x] 17.2 实现 SegmentTestService
    - 实现 `saveTestData`、`listTestData`、`deleteTestData`
    - 实现 `runSegmentTest`: 使用测试数据渲染 Segment 并比对预期结果
    - 实现 `runAllCompositeTests`: 运行所有段落级 + 整体级测试用例
    - _需求: 14.1, 14.2, 14.3, 14.4, 14.5, 14.6, 14.7_

  - [x] 17.3 实现导入导出和测试 API 端点
    - GET `/api/composite-templates/{id}/export`、GET `/api/composite-templates/{id}/export-config`
    - POST `/api/composite-templates/import`
    - POST/GET/DELETE `/api/segments/{id}/test-data`
    - _需求: 12.1-12.5, 14.1-14.7_

- [ ] 18. 扩展服务（仪表板、推荐、市场）
  - [x] 18.1 扩展 DashboardService
    - 扩展 SystemOverviewDTO 新增 segmentCount、componentCount、compositeTemplateCount 字段
    - 新增 `/api/dashboard/segment-stats` 和 `/api/dashboard/component-ranking` 端点
    - 在文档生成统计中区分传统模板和组合模板
    - _需求: 19.1, 19.2, 19.3, 19.4_

  - [x] 18.2 实现 SegmentRecommendationService 和段落模板库
    - 实现 `recommendSegments`: 基于当前段落类型标签推荐可能需要的其他段落
    - 实现 `analyzeDuplicates`: 扫描租户下非组件 Segment，识别文本内容重复率 > 80% 的段落
    - 实现预置段落模板库: 创建系统级 Segment 模板（封面页、目录页、章节标题、表格数据页、签名页、法律声明页、附录页），用户可一键创建
    - 实现自定义段落模板: 支持用户将 Segment 保存为自定义模板
    - 实现段落类型标签: 支持为 Segment 添加类型标签（COVER、TOC、CHAPTER、TABLE、SIGNATURE、LEGAL、APPENDIX）
    - GET `/api/composite-templates/{id}/recommendations`、GET `/api/segments/duplicate-analysis`
    - GET `/api/segment-templates`（预置模板库列表）、POST `/api/segment-templates`（保存自定义模板）
    - _需求: 21.1, 21.2, 21.3, 21.4, 21.5_

  - [x] 18.3 实现 CompositeMarketService
    - 实现 `copyCompositeFromMarket`: 创建完整独立副本（所有 Segment .docx + Assembly_Config），Component_Template 引用断开
    - 实现 `shareCompositeToMarket`
    - _需求: 15.1, 15.2, 15.3, 15.4, 15.5_

- [x] 19. 检查点 — 确保所有测试通过
  - 确保所有测试通过，如有问题请询问用户。

- [ ] 20. 前端 — TypeScript 类型定义与 API 层
  - [x] 20.1 创建前端 TypeScript 类型定义
    - 创建 `frontend/src/types/segment.ts`: Segment、SegmentVersion、AssemblyConfig、AssemblySegmentEntry、SegmentReview、SegmentTestData、LockInfo、CompositeCoverageReport 等类型
    - _需求: 1.1, 2.3, 4.1, 5.1, 22.3_

  - [x] 20.2 创建前端 API 调用层
    - 创建 `frontend/src/api/segments.ts`: 段落 CRUD、版本管理、变量扫描、权限、编辑锁、测试数据、收藏等 API
    - 创建 `frontend/src/api/composite-templates.ts`: 组合模板 CRUD、Assembly_Config、预览、覆盖率、审查、导入导出、推荐等 API
    - 使用现有 `request.ts` 封装的 Axios 实例
    - _需求: 1.1-1.11, 2.1-2.13_

- [ ] 21. 前端 — 段落管理页面
  - [x] 21.1 创建段落列表页 (views/segments/Index.vue)
    - 实现段落列表表格（分页、名称搜索、标签筛选、分类筛选、类型筛选）
    - 实现创建/编辑段落对话框 (SegmentFormDialog.vue)
    - 实现删除、复制、提升/降级组件操作
    - 实现收藏功能
    - 所有用户可见文本使用 i18n key
    - _需求: 1.1, 1.4, 1.5, 1.6, 1.8, 3.1, 3.2, 3.6, 23.1, 23.3, 23.4_

  - [x] 21.2 创建段落详情页 (views/segments/Detail.vue)
    - 实现段落基本信息展示
    - 集成版本历史列表 (SegmentVersionList.vue): 版本列表、回滚、版本对比
    - 集成变量列表 (SegmentVariableList.vue): 变量扫描结果、绑定状态标注
    - 集成权限管理对话框 (SegmentPermissionDialog.vue)
    - 组件模板详情页展示引用关系列表和引用数量统计
    - _需求: 1.4, 3.5, 4.2, 4.3, 4.5, 6.2, 6.6, 10.6_

  - [x] 21.3 创建段落编辑器页 (views/segments/Editor.vue)
    - 复用 OnlyOfficeEditor.vue 组件实现段落在线编辑
    - 集成编辑锁: 打开编辑器时获取锁，关闭时释放，心跳续期 (useSegmentLock.ts)
    - 显示其他用户编辑状态警告
    - _需求: 1.4, 22.1, 22.2, 22.3, 22.4_

  - [x] 21.4 创建组件模板列表页 (views/components/Index.vue)
    - 实现组件模板列表（名称搜索、标签筛选、分页）
    - 支持按引用数量排序
    - _需求: 3.2, 3.10_

  - [x] 21.5 编写段落管理页面前端测试
    - 使用 Vitest 测试段落列表页的搜索、筛选、分页逻辑
    - 测试编辑锁心跳续期逻辑 (useSegmentLock.ts)
    - _需求: 1.5, 22.3_

- [ ] 22. 前端 — 组合模板管理页面
  - [x] 22.1 创建组合模板列表页 (views/composite-templates/Index.vue)
    - 实现组合模板列表表格（分页、搜索、段落数量/组件数量统计）
    - 实现创建组合模板功能
    - 支持全文搜索（模板名称 + 描述 + 段落名称）
    - _需求: 2.1, 23.2, 23.5_

  - [x] 22.2 创建组合模板详情页 (views/composite-templates/Detail.vue)
    - 展示组合模板基本信息、段落缩略预览列表（名称、描述、最新版本号、组件/普通标识）
    - 集成覆盖率指标组件 (CoverageIndicator.vue)
    - 集成段落审查面板 (SegmentReviewPanel.vue)
    - 集成迁移工具对话框 (MigrationDialog.vue)
    - _需求: 2.13, 6.4, 9.3, 11.5_

  - [x] 22.3 创建组装配置编辑器 (views/composite-templates/AssemblyEditor.vue)
    - 实现拖拽排序界面 (useSegmentDrag.ts): 拖拽占位符、插入位置指示线、半透明预览
    - 实现键盘快捷键调整顺序（Alt+↑/↓）
    - 实现段落快速搜索面板 (SegmentSearchPanel.vue): 实时搜索并一键添加
    - 实现大纲导航视图 (OutlineNavigation.vue)
    - 实现数据作用域映射器 (DataScopeMapper.vue): 可视化全局变量到局部变量映射
    - 实现每个 Segment 的配置: 启用/禁用、条件表达式、分页符、版本锁定
    - 实现批量操作: 批量启用、禁用、移除
    - 实现撤销/重做 (useAssemblyConfig.ts): 至少 20 步操作历史
    - 实现空状态引导流程
    - 实现预估总页数显示
    - 实现编辑锁状态标识（彩色边框/锁图标）
    - 实现智能推荐面板 (SegmentRecommendPanel.vue)
    - _需求: 2.2, 2.4, 2.5, 2.6, 2.13, 5.5, 5.7, 10.4, 20.1-20.8, 21.3, 22.5_

  - [x] 22.4 编写组合模板页面前端测试
    - 使用 Vitest 测试拖拽排序逻辑 (useSegmentDrag.ts)
    - 测试撤销/重做逻辑 (useAssemblyConfig.ts)
    - 测试 Assembly_Config 序列化/反序列化
    - _需求: 2.2, 20.8_

- [ ] 23. 前端 — i18n 与路由配置
  - [x] 23.1 扩展 i18n 翻译文件
    - 在 en-US.json、zh-CN.json、zh-TW.json 中新增 `segment.*`、`component.*`、`composite.*`、`assembly.*`、`migration.*` 前缀的翻译 key
    - _需求: 全部前端需求_

  - [x] 23.2 扩展路由配置
    - 在 `router/index.ts` 中新增段落、组件模板、组合模板相关路由
    - _需求: 全部前端需求_

  - [x] 23.3 创建 Pinia Store
    - 创建 `frontend/src/stores/segment.ts`: 段落状态管理
    - _需求: 全部前端需求_

- [x] 24. 最终检查点 — 确保所有测试通过
  - 确保所有测试通过，如有问题请询问用户。

## 备注

- 所有任务均为必须完成项
- 每个任务引用了具体的需求编号，确保可追溯性
- 检查点任务确保增量验证，及时发现问题
- 属性测试验证设计文档中定义的 10 个正确性属性
- 单元测试和集成测试验证具体示例和边界条件

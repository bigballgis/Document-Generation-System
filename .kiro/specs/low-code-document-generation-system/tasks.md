# 实现计划：低代码文档生成系统

## 概述

本实现计划将低代码文档生成系统分为 23 个主要任务组（其中 5 个为检查点），按照依赖关系从基础设施到核心功能再到高级功能的顺序排列。后端使用 Java 17 + Spring Boot 3.2，前端使用 Vue 3 + TypeScript，Node.js 服务使用 Docxtemplater + LibreOffice。

## 任务

- [x] 1. 项目初始化和基础设施搭建
  - [x] 1.1 创建 Spring Boot 后端项目结构
    - 使用 Spring Initializr 创建 Maven 项目，配置 Java 17、Spring Boot 3.2
    - 添加依赖：Spring Web、Spring Security、Spring Data JPA、PostgreSQL Driver、Redis、Flyway、SpringDoc OpenAPI、Spring Batch、Spring Mail、Resilience4j、MinIO Java SDK
    - 创建基础包结构：`config`、`controller`、`service`、`repository`、`entity`、`dto`、`exception`、`filter`、`util`
    - 配置 `application.yml`（数据库、Redis、MinIO、JWT、加密密钥等环境变量引用）
    - 配置 MinIO 客户端（MinioClient Bean，连接 endpoint、access key、secret key）
    - _需求: 15、20、29_

  - [x] 1.2 创建 Vue 3 前端项目结构
    - 使用 Vite 创建 Vue 3 + TypeScript 项目
    - 安装依赖：Element Plus、Pinia、Vue Router、Axios、ECharts、vue-i18n
    - 创建目录结构：`api/`、`components/`、`composables/`、`i18n/`、`layouts/`、`router/`、`stores/`、`types/`、`views/`
    - 配置 Vite 代理、TypeScript 配置、ESLint
    - _需求: 22、28_

  - [x] 1.3 创建 Docker Compose 部署配置
    - 编写 `docker-compose.yml`，包含 Spring Boot 应用、Docxtemplater Node.js 服务、OnlyOffice、PostgreSQL 16.5、Redis 7.2、MinIO
    - 编写 Spring Boot 应用 Dockerfile
    - 编写 Docxtemplater 服务 Dockerfile（含 LibreOffice Headless 安装）
    - 创建 `.env.example` 环境变量模板文件
    - _需求: 20_

  - [x] 1.4 配置全局异常处理和标准化错误响应
    - 创建 `ErrorResponse` DTO（包含 error code、message、timestamp、traceId、details）
    - 创建 `BusinessException` 基类和各模块异常子类
    - 实现 `GlobalExceptionHandler`（@RestControllerAdvice），处理 BusinessException、AccessDeniedException、RateLimitExceededException 等
    - 定义错误码常量类（AUTH_、TENANT_、TEMPLATE_、DATASOURCE_、EXPRESSION_、GENERATE_、VALIDATION_、RATE_LIMIT_、REVIEW_ 前缀）
    - _需求: 15.1_

- [x] 2. 数据库表创建（Flyway 迁移脚本）
  - [x] 2.1 创建核心业务表迁移脚本
    - `V1__create_tenants_table.sql`：tenants 表
    - `V2__create_teams_table.sql`：teams 表
    - `V3__create_users_table.sql`：users 表（含索引）
    - `V4__create_templates_table.sql`：templates 表（含索引）
    - `V5__create_template_versions_table.sql`：template_versions 表
    - `V6__create_data_sources_table.sql`：data_sources 表
    - `V7__create_expressions_table.sql`：expressions 表
    - `V8__create_generated_documents_table.sql`：generated_documents 表
    - `V9__create_async_tasks_table.sql`：async_tasks 表
    - `V10__create_permissions_table.sql`：permissions 表（含 CHECK 约束）
    - _需求: 1、2、3、5、8、10、18、27_

  - [x] 2.2 创建扩展功能表迁移脚本
    - `V11__create_template_categories_table.sql`：template_categories 表
    - `V12__create_template_tags_table.sql`：template_tags 表
    - `V13__create_template_tag_mappings_table.sql`：template_tag_mappings 关联表
    - `V14__create_template_reviews_table.sql`：template_reviews 表
    - `V15__create_scheduled_tasks_table.sql`：scheduled_tasks 表
    - `V16__create_task_executions_table.sql`：task_executions 表
    - `V17__create_webhook_configs_table.sql`：webhook_configs 表
    - `V18__create_webhook_logs_table.sql`：webhook_logs 表
    - `V19__create_audit_logs_table.sql`：audit_logs 表
    - `V20__create_test_cases_table.sql`：test_cases 表
    - `V21__create_test_results_table.sql`：test_results 表
    - `V22__create_api_keys_table.sql`：api_keys 表
    - `V23__create_rate_limit_configs_table.sql`：rate_limit_configs 表
    - `V24__create_market_templates_table.sql`：market_templates 表
    - `V25__create_template_variables_table.sql`：template_variables 表
    - _需求: 31、32、35、36、37、38、41、43、45_

- [x] 3. 检查点 - 确保数据库迁移正常执行
  - 启动应用验证 Flyway 迁移脚本全部执行成功，确保所有 25 张表和索引正确创建，如有问题请询问用户。

- [x] 4. 安全基础设施（Spring Security、JWT、API Key、多租户隔离）
  - [x] 4.1 实现 JWT 认证机制
    - 创建 `JwtTokenProvider` 工具类（生成 Access Token 2h、Refresh Token 7d、验证签名和过期时间、提取 userId/tenantId/role）
    - 创建 `JwtAuthenticationFilter`（OncePerRequestFilter），从 Authorization 头提取 Bearer Token 并设置 SecurityContext
    - 创建 `UserPrincipal` 实现 UserDetails，包含 userId、tenantId、role、teamId
    - 将 Refresh Token 存储到 Redis，支持令牌刷新和黑名单机制
    - _需求: 9.1、48.3、48.4、48.5_

  - [x] 4.2 实现 OAuth 2.0 认证支持
    - 集成 Spring Security OAuth2 Resource Server
    - 支持作为 OAuth 2.0 资源服务器验证外部 OAuth Provider 签发的 Access Token
    - 支持配置 OAuth 2.0 Provider 的 JWKS 端点或 Token Introspection 端点
    - 从 OAuth Token 中提取用户信息并映射到 UserPrincipal
    - _需求: 9.2_

  - [x] 4.3 实现 API Key 认证机制
    - 创建 `ApiKeyAuthenticationFilter`（OncePerRequestFilter），从 X-API-Key 请求头提取 API Key
    - 查询 api_keys 表验证 key_hash（SHA-256）、enabled 状态、expires_at 过期时间
    - 验证通过后设置 SecurityContext
    - _需求: 9.1_

  - [x] 4.4 实现多租户数据隔离
    - 创建 `TenantContext`（ThreadLocal 存储当前 tenantId）
    - 创建 `TenantIsolationFilter`（OncePerRequestFilter），从 SecurityContext 提取 tenantId 设置到 TenantContext
    - 在 JPA Entity 上配置 Hibernate @FilterDef 和 @Filter 注解，自动添加 `tenant_id = :tenantId` 条件
    - 创建 `TenantAwareRepository` 基类，在查询前自动启用 Hibernate 租户过滤器
    - _需求: 19.1、19.2、19.3、19.4_

  - [x] 4.5 配置 Spring Security 过滤器链
    - 创建 `SecurityConfig` 配置类，定义过滤器链顺序：RateLimitFilter → ApiKeyAuthenticationFilter → JwtAuthenticationFilter → TenantIsolationFilter
    - 配置 CORS（允许所有来源、GET/POST/PUT/DELETE/OPTIONS 方法、凭证支持）
    - 配置无状态会话管理（STATELESS）
    - 配置公开端点（/api/auth/**、/actuator/health）和角色权限（/api/tenants/** 需要 SUPER_ADMIN）
    - _需求: 9.3、9.4、9.5_

  - [x] 4.6 编写属性测试：租户数据隔离
    - **Property 17: 租户数据隔离**
    - 验证被禁用租户的用户请求返回 403，活跃租户用户无法访问其他租户数据
    - **验证: 需求 19.1、19.3、49.4**

- [x] 5. 用户和租户管理
  - [x] 5.1 实现用户管理服务
    - 创建 `User` Entity、`UserRepository`、`UserService`、`UserDTO`、`RegisterRequest`、`LoginRequest`
    - 实现用户注册（BCrypt 密码哈希、密码强度校验：≥8位含大小写数字特殊字符）
    - 实现用户登录（验证密码、生成 JWT TokenPair、连续5次失败锁定30分钟）
    - 实现令牌刷新、密码重置（邮件发送重置链接，30分钟有效期）
    - 实现用户资料管理（修改昵称、头像、联系方式）、角色分配（SUPER_ADMIN、TENANT_ADMIN、TEAM_ADMIN、USER）
    - 创建 `AuthController`（/api/auth/register、/api/auth/login、/api/auth/refresh、/api/auth/reset-password）
    - 创建 `UserController`（/api/users CRUD 端点）
    - _需求: 48.1-48.10_

  - [x] 5.2 编写属性测试：密码强度验证
    - **Property 14: 密码强度验证正确性**
    - 使用 jqwik 生成随机密码字符串，验证满足所有条件的密码被接受，不满足任一条件的被拒绝
    - **验证: 需求 48.2**

  - [x] 5.3 实现租户管理服务
    - 创建 `Tenant` Entity、`TenantRepository`、`TenantService`、`TenantDTO`
    - 实现租户 CRUD（创建、编辑、启用/禁用）
    - 实现租户成员管理（添加/移除用户、设置角色）
    - 实现资源配额管理（最大模板数、最大 API 调用次数/月、最大存储空间）
    - 实现租户资源使用情况查询
    - 禁用租户时拒绝该租户下所有用户访问（返回 403）
    - 创建 `TenantController`（/api/tenants CRUD 端点、/api/tenants/{id}/enable、/api/tenants/{id}/disable、/api/tenants/{id}/usage）
    - _需求: 49.1-49.9_

  - [x] 5.4 实现团队管理
    - 创建 `Team` Entity、`TeamRepository`、`TeamService`
    - 实现团队 CRUD，确保团队在租户范围内唯一
    - _需求: 19.6、19.7_

- [x] 6. 检查点 - 确保安全和用户管理功能正常
  - 确保所有测试通过，验证 JWT 认证、API Key 认证、多租户隔离、用户注册登录流程正常工作，如有问题请询问用户。

- [x] 7. 模板核心 CRUD 和状态机
  - [x] 7.1 实现模板 CRUD 服务
    - 创建 `Template` Entity、`TemplateRepository`、`TemplateService`、`TemplateDTO`、`CreateTemplateRequest`、`UpdateTemplateRequest`、`TemplateQueryRequest`
    - 实现模板创建（保存配置到数据库、模板文件上传到 MinIO）
    - 实现模板列表查询（分页、按名称/描述模糊搜索）
    - 实现模板详情查询、更新、删除
    - 实现模板复制（完整副本含数据源、表达式、验证规则，名称加"- 副本"后缀，状态设为 DRAFT）
    - 创建 `TemplateController`（/api/templates CRUD 端点、/api/templates/{id}/clone）
    - _需求: 1.1-1.9_

  - [x] 7.2 编写属性测试：模板持久化往返一致性
    - **Property 1: 模板持久化往返一致性**
    - 使用 jqwik 生成随机 CreateTemplateRequest，验证创建后查询返回的数据与创建时一致
    - **验证: 需求 1.1、1.2**

  - [x] 7.3 编写属性测试：模板克隆独立性
    - **Property 2: 模板克隆独立性**
    - 验证克隆模板名称为原名+"- 副本"、状态为 DRAFT、包含完整配置，修改克隆不影响原模板
    - **验证: 需求 1.7、1.8**

  - [x] 7.4 实现模板版本管理
    - 创建 `TemplateVersion` Entity、`TemplateVersionRepository`、版本管理逻辑
    - 模板修改保存时自动创建新版本（版本号严格递增）
    - 保存每个版本的完整配置（config_json）和元数据
    - 实现版本历史查询、回滚到指定版本（基于历史版本创建新版本）
    - 在 `TemplateController` 中添加版本端点（/api/templates/{id}/versions、/api/templates/{id}/rollback/{versionId}）
    - _需求: 10.1-10.6_

  - [x] 7.5 编写属性测试：模板版本单调递增与回滚正确性
    - **Property 7: 模板版本单调递增与回滚正确性**
    - 验证每次保存版本号递增，回滚后新版本内容与目标版本一致且版本号继续递增
    - **验证: 需求 10.1、10.2、10.5、10.6**

  - [x] 7.6 实现模板状态机
    - 创建 `TemplateStateMachineService`，定义状态：DRAFT、PENDING_REVIEW、REVIEWED、ACTIVE、ARCHIVED
    - 实现合法状态转换：DRAFT→PENDING_REVIEW、PENDING_REVIEW→REVIEWED、PENDING_REVIEW→DRAFT、REVIEWED→ACTIVE、ACTIVE→ARCHIVED、ARCHIVED→DRAFT
    - 支持配置是否需要审查（review_required），无需审查时允许 DRAFT→ACTIVE
    - 非法转换返回包含当前状态和目标状态的错误响应
    - 状态变更记录到审计日志
    - 在 `TemplateController` 中添加状态转换端点（/api/templates/{id}/activate、/api/templates/{id}/archive）
    - _需求: 52.1-52.7_

  - [x] 7.7 编写属性测试：模板状态机转换合法性
    - **Property 15: 模板状态机转换合法性**
    - 使用 jqwik 生成随机状态对，验证仅允许预定义的合法转换，拒绝所有非法转换
    - **验证: 需求 52.1、52.2、52.3**

  - [x] 7.8 实现模板分类与标签
    - 创建 `TemplateCategory` Entity、`TemplateTag` Entity、`TemplateTagMapping` Entity
    - 实现分类树形管理（多级分类 CRUD、排序）
    - 实现标签管理（CRUD、模板-标签关联）
    - 删除分类时将模板移动到默认分类
    - 支持按分类、标签、分类+标签组合筛选模板列表
    - 创建 `CategoryController`（/api/categories CRUD）和 `TagController`（/api/tags CRUD）
    - _需求: 31.1-31.7_

- [x] 8. 数据源集成
  - [x] 8.1 实现加密服务
    - 创建 `EncryptionService`，使用 AES-256-GCM 算法加密/解密
    - 密钥从环境变量 `ENCRYPTION_KEY` 读取
    - 实现密钥轮换功能（使用新密钥重新加密所有已存储的敏感信息）
    - 实现脱敏显示（长度≥8显示前4后4中间星号，长度<8全部星号）
    - _需求: 50.1-50.6_

  - [x] 8.2 编写属性测试：敏感信息加密往返一致性
    - **Property 3: 敏感信息加密往返一致性**
    - 使用 jqwik 生成随机字符串（含特殊字符），验证加密后解密得到原始值
    - **验证: 需求 2.10、50.1、50.2**

  - [x] 8.3 编写属性测试：敏感信息脱敏格式正确性
    - **Property 4: 敏感信息脱敏格式正确性**
    - 使用 jqwik 生成随机长度字符串，验证脱敏格式符合规则
    - **验证: 需求 2.11、50.3**

  - [x] 8.4 实现数据源核心服务
    - 创建 `DataSource` Entity、`DataSourceRepository`、`DataSourceService`、`DataSourceDTO`、`DataSourceConfig`
    - 实现数据源 CRUD（创建、查询、更新、删除）
    - 敏感信息（密码、API Key、Token）使用 EncryptionService 加密存储
    - 查看时使用 EncryptionService 脱敏显示
    - 创建 `DataSourceController`（/api/templates/{id}/data-sources CRUD、/api/data-sources/{id}/test）
    - _需求: 2.1-2.11、3.1-3.10、4.1-4.9_

  - [x] 8.5 实现 HTTP API 数据源
    - 使用 RestTemplate/WebClient 调用外部 REST API
    - 支持设置 URL、HTTP 方法、请求头、请求参数
    - 支持认证信息配置（API Key、OAuth、Basic Auth）
    - 支持响应超时时间配置（默认 5000ms）
    - 支持重试策略配置（重试次数、间隔、指数退避）
    - 实现连接测试功能
    - _需求: 2.1-2.9_

  - [x] 8.6 实现数据库数据源
    - 支持 PostgreSQL、MySQL、SQL Server、Oracle 连接
    - 使用 HikariCP 连接池管理数据库连接
    - 支持 SQL 查询执行和结果映射到模板变量
    - 使用 PreparedStatement 防止 SQL 注入
    - 实现连接测试功能
    - _需求: 3.1-3.10_

  - [x] 8.7 实现内部系统数据源
    - 通过服务名称标识内部系统
    - 使用 HTTP 客户端调用内部系统接口
    - 支持认证授权机制、超时和重试策略
    - 实现连接测试功能
    - _需求: 4.1-4.9_

  - [x] 8.8 编写属性测试：数据源错误传播完整性
    - **Property 5: 数据源错误传播完整性**
    - 模拟超时、HTTP 4xx/5xx、连接拒绝等场景，验证返回标准化错误响应且包含数据源名称
    - **验证: 需求 2.5、3.5、4.4**

  - [x] 8.9 编写属性测试：SQL 注入防护
    - **Property 10: SQL 注入防护**
    - 使用 jqwik 生成随机 SQL 注入模式字符串，验证通过 PreparedStatement 执行后作为普通字符串处理
    - **验证: 需求 33.6**

- [x] 9. 检查点 - 确保模板和数据源功能正常
  - 确保所有测试通过，验证模板 CRUD、版本管理、状态机、分类标签、数据源集成功能正常工作，如有问题请询问用户。

- [x] 10. 表达式引擎和数据处理
  - [x] 10.1 实现表达式引擎服务
    - 注意：JavaScript 表达式执行依赖 Docxtemplater Node.js 服务（任务 11），本任务先实现 Spring Boot 侧的接口和 HTTP 调用逻辑，Node.js 服务的实际实现在任务 11 中完成
    - 创建 `Expression` Entity、`ExpressionRepository`、`ExpressionEngine` 接口和实现
    - 支持 JavaScript 表达式（通过调用 Docxtemplater Node.js 服务的 /evaluate 端点在安全沙箱中执行）
    - 支持 Excel 公式（通过 Node.js 服务中的 Formula.js 库执行）
    - 支持基本算术、逻辑、比较、字符串操作
    - 支持访问嵌套对象属性和数组元素
    - 实现表达式语法验证，返回详细错误位置和原因
    - 创建 `ExpressionController`（/api/templates/{id}/expressions CRUD、/api/expressions/validate）
    - _需求: 5.1-5.9、23.1-23.8_

  - [x] 10.2 实现数据源参数化查询
    - 支持 SQL 查询中的参数占位符（`:paramName`）
    - 支持 API URL 中的路径参数（`/users/{userId}`）和查询参数
    - 支持参数默认值配置和必填/可选标记
    - 缺少必填参数时返回验证错误响应
    - 对 SQL 参数使用 PreparedStatement，对 URL 参数进行编码处理
    - _需求: 33.1-33.7_

  - [x] 10.3 实现数据缓存服务
    - 使用 Redis 缓存数据源响应（键：`datasource:{dataSourceId}:{paramHash}`）
    - 支持配置缓存策略（启用/禁用、TTL 过期时间）
    - 缓存命中时直接返回缓存数据，未命中时调用数据源并存入缓存
    - 提供手动清除缓存功能
    - _需求: 14.1-14.7_

  - [x] 10.4 实现数据验证服务
    - 支持必填字段、数据类型、数值范围、字符串长度、正则表达式验证
    - 验证失败返回包含验证错误详情的响应
    - _需求: 13.1-13.7_

  - [x] 10.5 编写属性测试：数据验证规则正确性
    - **Property 8: 数据验证规则正确性**
    - 使用 jqwik 生成随机数据和验证规则组合，验证引擎正确接受/拒绝数据
    - **验证: 需求 13.2-13.7**

  - [x] 10.6 实现数据格式转换服务
    - 支持日期格式转换、数字格式转换（小数位数、千分位）
    - 支持数据类型转换（字符串/数字/布尔值）
    - 支持自定义映射规则（字段重命名、值映射）
    - 转换失败时记录警告日志并使用原始数据
    - _需求: 7.1-7.6_

  - [x] 10.7 实现响应转换器服务
    - 支持 JSONPath 表达式从 API 响应中提取数据
    - 支持 XPath 表达式从 XML 响应中提取数据
    - 支持数据扁平化、数组分组和排序操作
    - 转换规则执行失败时返回包含规则名称和错误原因的响应
    - _需求: 42.1-42.7_

  - [x] 10.8 编写属性测试：响应数据提取正确性
    - **Property 16: 响应数据提取正确性**
    - 使用 jqwik 生成随机 JSON 对象和有效 JSONPath 表达式，验证提取结果正确
    - **验证: 需求 42.1**

  - [x] 10.9 实现数据聚合服务
    - 支持一个模板配置多个数据源
    - 并行或顺序调用所有数据源
    - 合并结果到统一数据上下文，同名字段按优先级规则决定最终值
    - _需求: 12.1-12.5_

  - [x] 10.10 实现数据管道服务
    - 创建 `DataPipelineService`，支持获取→转换→计算→验证四阶段管道
    - 支持数据源之间的依赖关系配置
    - 按依赖顺序执行各阶段，无依赖关系的数据源并行执行
    - 某阶段失败时终止后续阶段并返回错误
    - 保存时检测循环依赖，发现循环返回错误并拒绝保存
    - _需求: 34.1-34.7_

- [x] 11. Docxtemplater Node.js 服务
  - [x] 11.1 创建 Node.js 服务基础结构
    - 初始化 Node.js 项目（Express 框架）
    - 安装依赖：docxtemplater、docxtemplater-image-module、docxtemplater-chart-module、isolated-vm、formula.js、pizzip、minio
    - 实现健康检查端点 `GET /health`
    - 配置 MinIO 客户端连接
    - _需求: 20.9_

  - [x] 11.2 实现文档渲染端点
    - 实现 `POST /render` 端点，接收模板文件路径和数据，使用 Docxtemplater 渲染文档
    - 支持条件渲染（if-else 逻辑）、循环渲染（数组遍历、嵌套循环）
    - 集成 docxtemplater-image-module（静态图片、动态图片 URL、Base64）
    - 集成 docxtemplater-chart-module（柱状图、饼图、折线图）
    - 支持动态数据表格（循环标签行级填充）
    - 支持条形码和二维码组件
    - 支持文字水印和图片水印
    - _需求: 6.1-6.5、11.1-11.6、26.3-26.16_

  - [x] 11.3 实现安全沙箱表达式执行
    - 使用 `isolated-vm` 库实现安全沙箱
    - 实现 `POST /evaluate` 端点，在沙箱中执行 JavaScript 表达式
    - 配置执行时间限制（默认 5 秒）和内存限制（默认 64MB）
    - 禁止访问 fs、path、http、https、net、child_process、process、eval、Function 构造函数
    - 提供白名单安全函数和数据上下文对象注入
    - 集成 Formula.js 支持 Excel 公式（SUM、AVERAGE、IF、VLOOKUP、CONCATENATE 等）
    - 超时返回超时错误，内存溢出返回内存溢出错误
    - _需求: 51.1-51.9_

  - [x] 11.4 编写属性测试：表达式安全沙箱隔离性
    - **Property 6: 表达式安全沙箱隔离性**
    - 生成尝试访问 fs、http、net、child_process、process、eval、Function 的表达式，验证沙箱拒绝执行
    - **验证: 需求 5.9、51.3-51.6**

  - [x] 11.5 实现 PDF 转换端点
    - 实现 `POST /convert-pdf` 端点，使用 LibreOffice Headless 将 Word 转 PDF
    - 支持批量转换
    - 支持 PDF 密码保护和权限控制（禁止打印、复制、编辑）
    - _需求: 21.1-21.5、39.3、39.4_

- [x] 12. 文档生成核心
  - [x] 12.1 实现文档生成服务
    - 创建 `DocumentGeneratorService`，协调完整文档生成流程
    - 配置 Resilience4j 熔断器：对 Docxtemplater 服务调用和外部数据源调用启用熔断（失败率阈值 50%、慢调用率阈值 80%、半开状态允许 5 次调用、等待时间 30 秒）
    - 流程：接收请求 → 执行数据管道（获取数据源数据、表达式计算）→ 调用 Docxtemplater 服务渲染 → 可选 PDF 转换 → 存储到 MinIO
    - 支持同步生成（POST /api/generate/{templateId}）
    - 支持指定输出格式（WORD、PDF、BOTH）
    - 创建 `GenerateController`
    - _需求: 6.1-6.5、7.1-7.6、8.1-8.8、21.1-21.5_

  - [x] 12.2 实现文档存储策略
    - 创建 `GeneratedDocument` Entity、`GeneratedDocumentRepository`
    - 支持临时存储模式（直接返回文档内容或临时下载链接）
    - 支持持久化存储模式（上传 MinIO、保存元数据到数据库、返回永久链接）
    - 支持持久化文档过期时间配置
    - 实现临时文件自动清理机制（默认 24 小时，可配置 1-72 小时）
    - 实现历史文档列表查询（按模板、时间范围、状态筛选）
    - 创建 `DocumentController`（/api/documents 查询、/api/documents/{id}/download）
    - _需求: 25.1-25.10_

  - [x] 12.3 实现模板预览功能
    - 支持用户提供测试数据生成预览文档
    - 支持在 OnlyOffice Editor 中直接预览（无需下载）
    - 支持下载预览文档到本地
    - 支持使用真实数据源预览（可选）
    - 在 `TemplateController` 中添加预览端点（/api/templates/{id}/preview）
    - _需求: 16.1-16.6_

- [x] 13. 检查点 - 确保文档生成核心流程正常
  - 确保所有测试通过，验证完整的文档生成流程（数据获取→表达式计算→模板渲染→PDF转换→存储）正常工作，如有问题请询问用户。

- [x] 14. API 自动生成和异步批量处理
  - [x] 14.1 实现 API 自动注册
    - 模板激活时通过 Spring Boot 内置路由机制自动注册 RESTful API 端点
    - Generated API 接受 JSON 请求参数，支持路径参数和查询参数
    - 默认使用最新激活版本，支持 `?version={versionNumber}` 指定版本
    - 支持配置是否允许调用历史版本（默认允许），不允许时返回 400 错误
    - _需求: 8.1-8.8_

  - [x] 14.2 实现 API 文档自动生成
    - 集成 SpringDoc OpenAPI，自动生成 OpenAPI 3.0 规范文档
    - 包含端点路径、HTTP 方法、请求参数、响应格式
    - 提供 Swagger UI 界面展示 API 文档
    - _需求: 17.1-17.5_

  - [x] 14.3 实现异步文档生成
    - 创建 `AsyncTask` Entity、`AsyncTaskRepository`
    - 异步生成请求立即返回任务 ID
    - 创建后台任务处理文档生成
    - 提供任务状态查询 API（/api/tasks/{taskId}、/api/tasks/{taskId}/progress）
    - 任务完成更新状态为 COMPLETED，失败更新为 FAILED 并记录错误信息
    - 提供已完成文档下载 API
    - 在 `GenerateController` 中添加异步端点（/api/generate/{templateId}/async）
    - _需求: 18.1-18.6_

  - [x] 14.4 实现批量文档生成
    - 基于 Spring Batch 实现批量处理器（ItemReader → ItemProcessor → ItemWriter）
    - 支持单次最多 1000 个文档
    - 为每个数据集生成独立文档，完成后打包为 ZIP
    - 记录每个文档生成状态（成功/失败）
    - 提供批量任务进度查询 API（已完成数量/总数量）
    - 支持部分失败处理策略（继续执行或达到阈值停止）
    - 在 `GenerateController` 中添加批量端点（/api/generate/{templateId}/batch）
    - _需求: 24.1-24.9_

  - [x] 14.5 编写属性测试：批量生成完整性
    - **Property 18: 批量生成完整性**
    - 验证 N 个数据集的批量任务完成后，成功数+失败数=N，ZIP 中文档数=成功数
    - **验证: 需求 24.2、24.4、24.5、24.7**

- [x] 15. 高级模板功能
  - [x] 15.1 实现模板变量管理
    - 创建 `TemplateVariable` Entity、`TemplateVariableRepository`、`TemplateVariableService`
    - 自动扫描模板文件中的 Docxtemplater 变量（{variable}、{#loop}、{#if} 等标签）
    - 支持变量类型定义（STRING、NUMBER、DATE、BOOLEAN、ARRAY、OBJECT）
    - 支持变量默认值、描述和使用说明
    - 未绑定变量标注为"未绑定"状态
    - 支持变量与数据源字段或表达式结果的绑定映射
    - 创建变量管理端点（/api/templates/{id}/variables、/api/templates/{id}/variables/{varId}/bind）
    - _需求: 43.1-43.7_

  - [x] 15.2 实现模板覆盖率检查
    - 创建 `CoverageCheckService`
    - 扫描模板中所有 Docxtemplater 标签
    - 检查每个标签是否已绑定到数据源或表达式
    - 计算覆盖率（已绑定/总数 × 100%）
    - 覆盖率低于阈值（默认 100%）时激活模板显示警告
    - 实现反向覆盖率检查（未使用的数据源字段）
    - 模板保存时自动执行覆盖率检查
    - 支持覆盖率报告导出（JSON 或 PDF 格式）
    - 创建覆盖率端点（/api/templates/{id}/coverage）
    - _需求: 47.1-47.8_

  - [x] 15.3 编写属性测试：模板覆盖率计算正确性
    - **Property 13: 模板覆盖率计算正确性**
    - 使用 jqwik 生成随机变量集合（已绑定和未绑定），验证覆盖率计算正确
    - **验证: 需求 47.3、47.4、47.6**

  - [x] 15.4 实现模板版本对比
    - 创建 `VersionDiffService`
    - 检测两个版本之间的文本内容差异（新增、删除、修改高亮）
    - 检测模板变量差异、数据源配置差异、表达式配置差异
    - 提供差异摘要信息（变更数量统计）
    - 创建版本对比端点（/api/templates/{id}/versions/diff?versionA=X&versionB=Y）
    - _需求: 46.1-46.8_

  - [x] 15.5 实现模板导入导出
    - 创建 `TemplateImportExportService`
    - 支持上传 .docx 文件作为新模板（验证文件格式有效性）
    - 支持导出模板为 .docx 文件
    - 支持导出模板完整配置为 JSON（含数据源、表达式、验证规则）
    - 支持导入 JSON 配置还原模板（验证配置结构和数据完整性）
    - 创建导入导出端点（/api/templates/import、/api/templates/{id}/export、/api/templates/{id}/export-config、/api/templates/import-config）
    - _需求: 30.1-30.7_

  - [x] 15.6 编写属性测试：模板配置导入导出往返一致性
    - **Property 9: 模板配置导入导出往返一致性**
    - 验证模板配置导出为 JSON 后再导入，还原出与原配置等价的模板
    - **验证: 需求 30.4、30.5、30.6**

- [x] 16. 权限、协作和运维功能
  - [x] 16.1 实现权限管理服务
    - 创建 `Permission` Entity、`PermissionRepository`、`PermissionService`
    - 支持模板级权限分配（团队级别和用户级别）
    - 支持细粒度权限控制（VIEW、EDIT、DELETE、CALL_API）
    - 访问模板时验证权限，无权限返回 403
    - 权限控制在租户隔离范围内生效
    - 创建 `PermissionController`（/api/templates/{id}/permissions CRUD）
    - _需求: 9.4、27.1-27.8_

  - [x] 16.2 实现模板审查工作流
    - 创建 `TemplateReview` Entity、`TemplateReviewRepository`、`TemplateReviewService`
    - 支持发起审查请求（指定审查人）
    - 支持审查通过、条件通过（附加建议修改项列表）、驳回（填写驳回原因）
    - 支持多级审查流程（初审→终审）
    - 审查通过自动更新模板状态为 REVIEWED
    - 记录审查意见和历史
    - 集成 OnlyOffice 协同编辑的批注功能，支持审查人在 OnlyOffice Editor 中对模板添加批注和修改建议
    - 创建审查端点（/api/templates/{id}/reviews、/api/reviews/{id}/approve、/api/reviews/{id}/conditional-approve、/api/reviews/{id}/reject）
    - _需求: 45.1-45.11_

  - [x] 16.3 实现 API Key 管理
    - 创建 `ApiKey` Entity、`ApiKeyRepository`、`ApiKeyService`
    - 支持创建 API Key（生成随机 Key，存储 SHA-256 哈希）
    - 支持查询、启用/禁用、删除 API Key
    - 支持配置过期时间和限流参数
    - 创建 `ApiKeyController`（/api/api-keys CRUD、enable、disable）
    - _需求: 9.1_

  - [x] 16.4 实现限流服务
    - 创建 `RateLimitService`，使用 Redis 滑动窗口算法
    - 支持每秒/每分钟/每小时频率限制（基于 API Key 配置）
    - 支持租户月度 API 调用配额
    - 超出限制返回 HTTP 429，响应头包含 Retry-After 和 X-RateLimit-Remaining
    - 创建 `RateLimitFilter`（OncePerRequestFilter）
    - 提供使用量统计查询接口（/api/rate-limits、/api/usage-stats）
    - _需求: 36.1-36.7_

  - [x] 16.5 编写属性测试：API 限流执行正确性
    - **Property 11: API 限流执行正确性**
    - 验证调用次数超过限制时返回 429，响应头包含正确信息
    - **验证: 需求 36.1、36.3、36.4**

  - [x] 16.6 实现审计日志服务
    - 创建 `AuditLog` Entity、`AuditLogRepository`、`AuditLogService`
    - 使用 AOP 或事件机制记录所有可审计操作（模板 CRUD、权限变更、API 调用、用户登录/登出）
    - 审计记录包含操作者、操作时间、操作类型、操作详情、IP 地址
    - 独立表存储，确保不可被业务操作修改或删除
    - 支持查询（按操作类型、操作者、时间范围筛选）和导出（CSV/JSON）
    - 支持配置保留期限（默认 365 天）
    - 创建 `AuditLogController`（/api/audit-logs 查询、/api/audit-logs/export）
    - _需求: 37.1-37.8_

  - [x] 16.7 编写属性测试：审计日志完整性
    - **Property 12: 审计日志完整性**
    - 验证每个可审计操作都在审计日志表中创建了包含完整信息的记录
    - **验证: 需求 37.1-37.4**

  - [x] 16.8 实现 Webhook 通知服务
    - 创建 `WebhookConfig` Entity、`WebhookLog` Entity、`WebhookService`
    - 文档生成完成/失败时发送 HTTP POST 通知
    - 批量任务完成时发送通知
    - 请求包含 HMAC-SHA256 签名头
    - 失败时指数退避重试（最多 3 次：1s、2s、4s）
    - 记录所有通知发送状态和响应
    - 创建 Webhook 端点（/api/templates/{id}/webhooks CRUD、/api/webhooks/{id}/logs）
    - _需求: 35.1-35.7_

  - [x] 16.9 实现定时任务服务
    - 创建 `ScheduledTask` Entity、`TaskExecution` Entity、`ScheduledTaskService`
    - 支持 Cron 表达式配置调度规则
    - 支持启用/禁用定时任务
    - 触发时使用配置的数据源和参数自动生成文档
    - 记录执行历史（执行时间、结果、文档 ID）
    - 失败时记录原因并发送告警通知，支持最大重试次数配置
    - 上一次执行未完成时跳过本次触发并记录跳过日志
    - 创建定时任务端点（/api/templates/{id}/scheduled-tasks CRUD、enable、disable、/api/scheduled-tasks/{id}/executions）
    - _需求: 41.1-41.9_

- [x] 17. 文档高级功能和模板市场
  - [x] 17.1 实现文档水印服务
    - 创建 `WatermarkService`
    - 支持文字水印（配置文本、字体大小、颜色、透明度、旋转角度）
    - 支持图片水印（配置图片、位置、透明度）
    - 支持动态水印内容（使用模板变量作为水印文本）
    - 文档渲染完成后应用水印
    - _需求: 39.1-39.7_

  - [x] 17.2 实现文档合并服务
    - 创建 `DocumentMergeService`
    - 支持多个 Word 文档合并为一个
    - 支持自定义合并顺序（按请求参数中文档 ID 列表顺序）
    - 支持合并文档间自动插入分页符
    - 支持合并后自动生成目录
    - 无效文档 ID 返回错误响应
    - 支持合并后转换为 PDF
    - 创建合并端点（/api/documents/merge）
    - _需求: 40.1-40.7_

  - [x] 17.3 编写属性测试：文档合并顺序正确性
    - **Property 19: 文档合并顺序正确性**
    - 验证文档 ID 列表 [A, B, C] 合并后各部分顺序与输入一致
    - **验证: 需求 40.1、40.3、40.4**

  - [x] 17.4 实现模板测试套件
    - 创建 `TestCase` Entity、`TestResult` Entity、`TemplateTestService`
    - 支持创建测试用例（测试数据 + 预期结果）
    - 支持三种比对方式：变量值比对、文本内容比对、文件快照比对
    - 支持一键运行所有测试用例
    - 模板修改保存时提供自动运行测试选项
    - 显示测试报告（通过/失败数量、失败详情和差异位置）
    - 支持测试用例导入导出（JSON）
    - 创建测试端点（/api/templates/{id}/test-cases CRUD、/api/test-cases/{id}/run、/api/templates/{id}/test-cases/run-all）
    - _需求: 38.1-38.9_

  - [x] 17.5 实现模板市场
    - 创建 `MarketTemplate` Entity、`MarketTemplateRepository`、`TemplateMarketService`
    - 提供预置常用模板（合同、报告、发票、证书）
    - 支持从市场一键复制模板到用户工作区（创建独立副本含完整配置）
    - 支持用户分享模板到市场（确认分享范围：TENANT_INTERNAL 或 GLOBAL）
    - 支持搜索（按名称、分类、标签）
    - 显示使用次数和评分信息
    - 创建市场端点（/api/market/templates 搜索、/api/market/templates/{id}/copy、/api/templates/{id}/share）
    - _需求: 32.1-32.7_

- [x] 18. 检查点 - 确保所有后端功能正常
  - 确保所有测试通过，验证高级模板功能、权限协作、运维功能、文档高级功能和模板市场全部正常工作，如有问题请询问用户。

- [x] 19. 系统监控和仪表板
  - [x] 19.1 实现系统监控和健康检查
    - 配置 Spring Boot Actuator 健康检查端点（/actuator/health）
    - 监控各模块运行状态（模板服务、文档生成服务、数据源服务、表达式引擎）
    - 监控数据库连接池、Redis 连接状态
    - 监控 OnlyOffice Document Server 和 Docxtemplater Service 健康状态（定期调用健康检查端点）
    - 不可用时在健康检查中报告 DOWN 并发送告警通知
    - 监控 API 响应时间和吞吐量
    - _需求: 20.1-20.10_

  - [x] 19.2 实现仪表板服务
    - 创建 `DashboardService`
    - 系统概览（模板总数、活跃模板数、API 调用总量、文档总数）
    - 实时 API 调用监控（最近 1 小时调用量趋势图）
    - 文档生成成功率统计（按模板、按时间范围）
    - 数据源健康状态监控（连通性、平均响应时间）
    - 系统资源使用（JVM 内存、数据库连接池、Redis 内存）
    - 创建仪表板端点（/api/dashboard/overview、/api/dashboard/api-metrics、/api/dashboard/data-source-health、/api/dashboard/system-resources）
    - _需求: 44.1-44.7_

- [x] 20. 前端应用开发
  - [x] 20.1 实现前端认证和布局
    - 实现登录/注册页面（/views/auth/）
    - 实现 JWT Token 管理（Axios 拦截器自动附加 Authorization 头、Token 刷新逻辑）
    - 实现主布局组件（侧边栏导航、顶部栏、面包屑）
    - 实现路由守卫（未登录跳转登录页、角色权限路由控制）
    - 实现 Pinia 用户状态管理
    - _需求: 48.3、48.4_

  - [x] 20.2 实现多语言支持
    - 配置 vue-i18n，创建 en-US、zh-CN、zh-TW 语言包
    - 实现语言切换组件，保存用户语言偏好
    - 所有界面文本和错误消息使用 i18n
    - _需求: 28.1-28.7_

  - [x] 20.3 实现模板管理页面
    - 模板列表页（分页、搜索、按分类/标签筛选）
    - 模板创建/编辑表单
    - 模板详情页（状态显示、可用状态转换按钮）
    - 模板版本历史和回滚操作
    - 模板版本对比视图（并排对比，差异高亮）
    - 模板变量管理界面
    - 模板覆盖率指标展示
    - _需求: 1、10、31、43、46、47、52.7_

  - [x] 20.4 实现 OnlyOffice 编辑器集成
    - 集成 OnlyOffice Document Editor 组件
    - 实现自定义插件：变量插入（{variable}）、循环插入（{#items}...{/items}）、条件插入（{#if}...{/if}）
    - 支持编辑器语言跟随用户语言偏好切换
    - 支持模板预览（在编辑器中直接预览渲染后文档）
    - _需求: 22.1、22.4、22.7、28.7_

  - [x] 20.5 实现 Monaco 编辑器集成
    - 集成 Monaco Editor 组件
    - 实现 Docxtemplater 语法高亮和自动补全
    - 支持 OnlyOffice 和 Monaco 两种模式切换，内容双向同步
    - _需求: 22.2、22.3、22.5、22.6_

  - [x] 20.6 实现数据源配置页面
    - 数据源列表和 CRUD 表单
    - HTTP API、数据库、内部系统三种类型配置界面
    - 连接测试功能
    - 敏感信息脱敏显示
    - 响应转换规则可视化配置
    - 数据管道可视化配置界面（节点连线展示数据流向）
    - _需求: 2、3、4、34.3、42.6_

  - [x] 20.7 实现运维仪表板页面
    - 系统概览卡片（模板总数、API 调用量、文档总数等）
    - API 调用趋势图（ECharts 折线图）
    - 文档生成成功率图表
    - 数据源健康状态面板
    - 系统资源使用图表（JVM 内存、连接池、Redis）
    - 支持自动刷新（可配置间隔，默认 30 秒）和时间范围选择
    - _需求: 44.1-44.7_

  - [x] 20.8 实现管理页面
    - 租户管理页面（CRUD、启用/禁用、资源配额配置、使用情况查看）
    - 用户管理页面（列表、角色分配）
    - 权限管理页面（模板权限分配界面）
    - API Key 管理页面
    - 审计日志查询和导出页面
    - 模板审查任务列表页面（待审查、已审查、条件通过、已驳回）
    - _需求: 27.7、37.5、37.6、45.7、49_

  - [x] 20.9 实现模板市场和测试页面
    - 模板市场搜索和浏览页面
    - 一键复制和分享功能
    - 模板测试用例管理页面
    - 测试运行和结果报告展示
    - 水印和安全选项配置界面
    - 定时任务配置和执行历史页面
    - _需求: 32、38、39.5、41.6_

- [x] 21. 检查点 - 确保前端应用功能正常
  - 确保前端所有页面正常渲染，与后端 API 交互正常，OnlyOffice 和 Monaco 编辑器集成正常，如有问题请询问用户。

- [x] 22. 集成测试
  - [x] 22.1 编写 Spring Boot 集成测试
    - 使用 Testcontainers 配置 PostgreSQL、Redis、MinIO 测试容器
    - 编写数据持久化集成测试（模板 CRUD、版本管理、多租户隔离）
    - 编写 Redis 集成测试（缓存、限流计数器）
    - 编写 MinIO 集成测试（文件上传下载）
    - 编写 Spring Security 集成测试（JWT 认证、API Key 认证、权限控制完整流程）
    - _需求: 1、9、14、19、36_

  - [x] 22.2 编写 Docxtemplater 服务集成测试
    - 测试文档渲染端点（条件渲染、循环渲染、图片、图表）
    - 测试安全沙箱表达式执行
    - 测试 PDF 转换
    - _需求: 6、11、21、51_

  - [x] 22.3 编写前端组件测试
    - 使用 Vitest + Vue Test Utils 编写关键组件测试
    - 测试认证流程、模板管理、数据源配置等核心页面
    - _需求: 22、28_

- [x] 23. 最终检查点 - 确保所有测试通过
  - 确保所有单元测试、属性测试和集成测试通过，系统功能完整可用，如有问题请询问用户。

## 说明

- 每个任务引用了具体的需求编号以确保可追溯性
- 检查点任务用于阶段性验证，确保增量开发的正确性
- 属性测试验证设计文档中定义的 19 个 Correctness Properties
- 单元测试和集成测试覆盖具体示例和边界条件

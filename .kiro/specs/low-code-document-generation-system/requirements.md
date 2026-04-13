# 需求文档 - 低代码文档生成系统

## 简介

低代码文档生成系统是一个基于 Spring Boot 的单体应用，允许用户通过可视化方式设计文档模板，集成多种数据源，并自动生成可供外部调用的 RESTful API。系统采用前后端分离架构（前端 Vue 3 + TypeScript，后端 Java 17 + Spring Boot 3.2），使用 Docxtemplater 作为文档渲染引擎（通过独立 Node.js 服务调用），使用 OnlyOffice Document Editor 作为在线模板编辑器，支持复杂的数据处理和条件渲染逻辑，生成 Word 和 PDF 格式文档。

## 术语表

- **Document_Generation_System**: 低代码文档生成系统，本需求文档描述的核心系统，基于 Spring Boot 单体应用架构
- **Template_Designer**: 模板设计器，用于创建和编辑文档模板的可视化工具
- **Data_Source**: 数据源，包括外部 API、内部系统、数据库等数据提供方
- **Template**: 文档模板，定义文档结构和数据绑定规则的配置，以 .docx 格式存储
- **Expression_Engine**: 表达式引擎，用于计算和转换数据的组件，支持 JavaScript 表达式和 Excel 公式
- **Document_Generator**: 文档生成器，根据模板和数据生成最终文档的组件，底层调用 Docxtemplater 服务
- **Template_Variable**: 模板变量，在模板中使用的数据占位符，使用 Docxtemplater 语法（如 `{variable}`、`{#loop}...{/loop}`）
- **Generated_API**: 生成的 API，系统根据模板自动创建的 RESTful 接口
- **User**: 系统用户，包括模板设计者和 API 调用者
- **OnlyOffice_Editor**: OnlyOffice Document Editor，开源在线文档编辑器，提供类似 Microsoft Office 的所见即所得编辑体验
- **Monaco_Editor**: Monaco Editor，VS Code 内核的代码编辑器，用于高级用户直接编辑模板语法和表达式
- **Docxtemplater_Service**: Docxtemplater 服务，独立的 Node.js 服务，负责解析 .docx 模板并填充数据生成最终文档
- **MinIO**: MinIO 对象存储服务，S3 兼容的开源对象存储，用于存储模板文件和生成的文档
- **PDF_Converter**: PDF 转换服务，使用 LibreOffice Headless 模式将 Word 文档转换为 PDF 格式
- **Batch_Processor**: 批量处理器，基于 Spring Batch 实现的批量文档生成组件
- **Data_Pipeline**: 数据管道，定义数据从获取到转换、计算、验证的完整处理流程
- **Template_Market**: 模板市场，提供预置模板和用户共享模板的公共模板库
- **Audit_Log**: 审计日志，记录系统中所有关键操作的不可变日志记录
- **Webhook**: Webhook 通知，在特定事件发生时向外部 URL 发送 HTTP 回调通知
- **Scheduled_Task**: 定时任务，按照 Cron 表达式配置的自动文档生成任务
- **Rate_Limiter**: 限流器，控制 API 调用频率和配额的组件
- **Tenant**: 租户，组织级别的隔离单元，代表一个独立的组织或公司；一个租户下可包含多个团队（Team），租户之间的数据完全隔离
- **Team**: 团队，租户内部的协作单元，属于某个租户；团队成员共享模板和资源，团队之间可通过权限控制实现数据隔离或共享
- **Review_Workflow**: 审查工作流，模板从提交审查到审查通过或驳回的完整流程，支持多级审查（初审→终审）和条件通过状态
- **Security_Sandbox**: 安全沙箱，用于隔离执行 JavaScript 表达式的受限运行环境，限制执行时间、内存使用，禁止访问文件系统和网络
- **Template_State_Machine**: 模板状态机，定义模板生命周期中各状态之间的转换规则（DRAFT→PENDING_REVIEW→REVIEWED→ACTIVE→ARCHIVED）
- **User_Management**: 用户管理模块，负责用户注册、登录认证（JWT）、密码重置、个人资料管理和角色分配
- **Tenant_Management**: 租户管理模块，负责租户的创建、配置、成员管理和资源配额管理

## 需求

### 需求 1: 模板创建与管理

**用户故事:** 作为模板设计者，我希望能够创建和管理文档模板，以便为不同的业务场景生成文档。

#### 验收标准

1. THE Template_Designer SHALL 提供创建新模板的功能
2. WHEN 用户保存模板时，THE Document_Generation_System SHALL 将模板配置持久化到数据库
3. THE Template_Designer SHALL 提供编辑已有模板的功能
4. THE Template_Designer SHALL 提供删除模板的功能
5. THE Template_Designer SHALL 提供模板列表查询功能
6. WHEN 用户查询模板时，THE Document_Generation_System SHALL 返回模板的元数据（名称、创建时间、修改时间、状态）
7. THE Template_Designer SHALL 提供从现有模板复制创建新模板的功能（包含模板文件、数据源配置、表达式配置、验证规则的完整副本）
8. WHEN 用户复制模板时，THE Document_Generation_System SHALL 为新模板生成独立的名称（原名称加"- 副本"后缀）并将状态设置为 DRAFT
9. THE Template_Designer SHALL 提供模板全文搜索功能（支持按模板名称和描述进行模糊搜索）

### 需求 2: 外部 API 数据源集成

**用户故事:** 作为模板设计者，我希望能够配置外部 API 作为数据源，以便在文档生成时获取实时数据。

#### 验收标准

1. THE Template_Designer SHALL 提供配置外部 HTTP/REST API 的功能
2. WHEN 配置 API 数据源时，THE Document_Generation_System SHALL 支持设置 URL、HTTP 方法、请求头、请求参数
3. WHEN 配置 API 数据源时，THE Document_Generation_System SHALL 支持配置认证信息（API Key、OAuth、Basic Auth）
4. WHEN 文档生成请求触发时，THE Document_Generator SHALL 调用配置的外部 API 获取数据
5. IF 外部 API 调用失败，THEN THE Document_Generator SHALL 返回包含错误信息的响应
6. WHEN 外部 API 返回数据时，THE Document_Generator SHALL 将响应数据映射到模板变量
7. THE Template_Designer SHALL 提供测试 API 数据源连接的功能，验证 URL 可达性和认证信息有效性，并返回测试结果（成功/失败及响应摘要）
8. THE Template_Designer SHALL 支持为 API 数据源配置响应超时时间（默认 5000 毫秒）
9. THE Template_Designer SHALL 支持为 API 数据源配置重试策略（重试次数、重试间隔、指数退避开关）
10. THE Document_Generation_System SHALL 对数据源配置中的敏感信息（API Key、OAuth Token、Basic Auth 密码）使用 AES-256-GCM 加密存储
11. WHEN 用户查看数据源配置时，THE Template_Designer SHALL 对敏感信息进行脱敏显示（仅显示前 4 位和后 4 位，中间用星号替代）

### 需求 3: 数据库数据源集成

**用户故事:** 作为模板设计者，我希望能够配置数据库查询作为数据源，以便从数据库中提取数据用于文档生成。

#### 验收标准

1. THE Template_Designer SHALL 提供配置数据库连接的功能
2. WHEN 配置数据库数据源时，THE Document_Generation_System SHALL 支持 PostgreSQL、MySQL、SQL Server、Oracle 数据库连接
3. THE Template_Designer SHALL 提供编写 SQL 查询语句的功能
4. WHEN 文档生成请求触发时，THE Document_Generator SHALL 执行配置的 SQL 查询
5. IF SQL 查询执行失败，THEN THE Document_Generator SHALL 返回包含错误信息的响应
6. WHEN SQL 查询返回结果时，THE Document_Generator SHALL 将查询结果映射到模板变量
7. THE Document_Generation_System SHALL 使用连接池管理数据库连接
8. THE Template_Designer SHALL 提供测试数据库连接的功能，验证连接参数有效性并返回测试结果（成功/失败及错误原因）
9. THE Document_Generation_System SHALL 对数据库连接配置中的密码使用 AES-256-GCM 加密存储
10. WHEN 用户查看数据库连接配置时，THE Template_Designer SHALL 对密码进行脱敏显示（显示为固定长度星号）

### 需求 4: 内部系统数据源集成

**用户故事:** 作为模板设计者，我希望能够配置内部系统作为数据源，以便复用现有系统的数据和服务。

#### 验收标准

1. THE Template_Designer SHALL 提供配置内部系统接口的功能
2. WHEN 配置内部系统数据源时，THE Document_Generation_System SHALL 支持通过服务名称标识内部系统
3. WHEN 文档生成请求触发时，THE Document_Generator SHALL 通过 HTTP 客户端直接调用内部系统接口
4. IF 内部系统调用失败，THEN THE Document_Generator SHALL 返回包含错误信息的响应
5. THE Document_Generation_System SHALL 支持内部系统的认证和授权机制
6. THE Document_Generation_System SHALL 支持为内部系统接口配置超时时间和重试策略
7. THE Template_Designer SHALL 提供测试内部系统接口连接的功能，验证服务可达性和认证有效性，并返回测试结果（成功/失败及响应摘要）
8. THE Document_Generation_System SHALL 对内部系统接口配置中的认证凭据使用 AES-256-GCM 加密存储
9. WHEN 用户查看内部系统接口配置时，THE Template_Designer SHALL 对认证凭据进行脱敏显示

### 需求 5: 表达式计算

**用户故事:** 作为模板设计者，我希望能够使用表达式计算派生数据，以便基于原始数据生成新的模板变量。

#### 验收标准

1. THE Template_Designer SHALL 提供配置计算表达式的功能
2. THE Expression_Engine SHALL 支持基本算术运算（加、减、乘、除、取模）
3. THE Expression_Engine SHALL 支持逻辑运算（与、或、非）
4. THE Expression_Engine SHALL 支持比较运算（等于、不等于、大于、小于、大于等于、小于等于）
5. THE Expression_Engine SHALL 支持字符串操作（拼接、截取、替换、大小写转换）
6. THE Expression_Engine SHALL 支持访问嵌套对象属性和数组元素
7. WHEN 文档生成时，THE Expression_Engine SHALL 计算所有配置的表达式并生成新的模板变量
8. IF 表达式计算失败，THEN THE Expression_Engine SHALL 返回包含错误信息的响应
9. THE Expression_Engine SHALL 在 Security_Sandbox 中执行 JavaScript 表达式，禁止访问文件系统、网络和系统进程

### 需求 6: 条件渲染

**用户故事:** 作为模板设计者，我希望能够根据数据条件控制文档内容的显示，以便生成动态的文档。

#### 验收标准

1. THE Template_Designer SHALL 提供配置条件渲染规则的功能
2. THE Template_Designer SHALL 支持 if-else 条件逻辑配置
3. WHEN 文档生成时，THE Document_Generator SHALL 根据条件表达式的结果决定是否渲染特定内容
4. THE Expression_Engine SHALL 计算条件表达式并返回布尔值结果
5. THE Document_Generator SHALL 支持嵌套的条件渲染逻辑

### 需求 7: 数据格式转换

**用户故事:** 作为模板设计者，我希望能够转换数据格式，以便将数据源的数据适配到模板所需的格式。

#### 验收标准

1. THE Template_Designer SHALL 提供配置数据转换规则的功能
2. THE Document_Generation_System SHALL 支持日期格式转换
3. THE Document_Generation_System SHALL 支持数字格式转换（小数位数、千分位分隔符）
4. THE Document_Generation_System SHALL 支持数据类型转换（字符串、数字、布尔值之间的转换）
5. THE Document_Generation_System SHALL 支持自定义映射规则（字段重命名、值映射）
6. WHEN 数据转换失败时，THE Document_Generation_System SHALL 记录警告日志并使用原始数据

### 需求 8: API 自动生成

**用户故事:** 作为模板设计者，我希望系统能够根据模板自动生成 API，以便其他应用可以调用这些 API 生成文档。

#### 验收标准

1. WHEN 模板保存并激活时，THE Document_Generation_System SHALL 通过 Spring Boot 内置路由机制自动注册对应的 RESTful API 端点
2. THE Generated_API SHALL 遵循 RESTful 设计规范
3. THE Generated_API SHALL 接受 JSON 格式的请求参数
4. THE Generated_API SHALL 支持通过路径参数或查询参数传递数据
5. WHEN API 被调用时，THE Document_Generator SHALL 使用提供的参数和配置的数据源生成文档
6. THE Generated_API SHALL 返回生成的文档或文档下载链接
7. WHEN 模板更新并发布新版本时，THE Document_Generation_System SHALL 默认使用最新激活版本的模板配置处理 API 请求，同时支持通过请求参数 `?version={versionNumber}` 指定使用特定版本的模板
8. THE Document_Generation_System SHALL 支持为每个模板配置是否允许调用历史版本（默认允许），IF 不允许调用历史版本且请求指定了非最新版本，THEN THE Generated_API SHALL 返回 400 错误并提示使用最新版本


### 需求 9: API 认证与授权

**用户故事:** 作为系统管理员，我希望能够控制 API 的访问权限，以便保护系统资源和数据安全。

#### 验收标准

1. THE Document_Generation_System SHALL 支持 API Key 认证机制
2. THE Document_Generation_System SHALL 支持 OAuth 2.0 认证机制
3. WHEN API 请求不包含有效认证信息时，THE Document_Generation_System SHALL 通过 Spring Security 过滤器链返回 401 未授权错误
4. THE Document_Generation_System SHALL 支持基于角色的访问控制（RBAC）
5. WHEN 用户无权访问特定 API 时，THE Document_Generation_System SHALL 通过 Spring Security 过滤器链返回 403 禁止访问错误

### 需求 10: 模板版本管理

**用户故事:** 作为模板设计者，我希望能够管理模板的不同版本，以便追踪变更历史和回滚到之前的版本。

#### 验收标准

1. WHEN 模板被修改并保存时，THE Document_Generation_System SHALL 创建新的模板版本
2. THE Document_Generation_System SHALL 保存每个版本的完整配置和元数据
3. THE Template_Designer SHALL 提供查看模板版本历史的功能
4. THE Template_Designer SHALL 提供回滚到指定版本的功能
5. WHEN 模板回滚时，THE Document_Generation_System SHALL 创建基于历史版本的新版本
6. THE Document_Generation_System SHALL 记录版本创建者和创建时间

### 需求 11: 循环渲染

**用户故事:** 作为模板设计者，我希望能够遍历列表数据并重复渲染内容，以便生成包含表格或列表的文档。

#### 验收标准

1. THE Template_Designer SHALL 提供配置循环渲染的功能
2. THE Template_Designer SHALL 支持指定要遍历的数组数据源
3. WHEN 文档生成时，THE Document_Generator SHALL 遍历数组中的每个元素
4. THE Document_Generator SHALL 为每个数组元素渲染模板内容
5. THE Expression_Engine SHALL 在循环上下文中提供当前元素和索引的访问
6. THE Document_Generator SHALL 支持嵌套的循环渲染

### 需求 12: 数据聚合

**用户故事:** 作为模板设计者，我希望能够合并多个数据源的数据，以便在一个文档中使用来自不同来源的信息。

#### 验收标准

1. THE Template_Designer SHALL 支持为一个模板配置多个数据源
2. WHEN 文档生成时，THE Document_Generator SHALL 并行或顺序调用所有配置的数据源
3. THE Document_Generator SHALL 将所有数据源的结果合并到统一的数据上下文中
4. IF 多个数据源返回同名字段，THEN THE Document_Generator SHALL 使用配置的优先级规则决定最终值
5. THE Template_Designer SHALL 提供配置数据源优先级的功能

### 需求 13: 数据验证

**用户故事:** 作为模板设计者，我希望能够验证数据的有效性，以便确保生成的文档使用正确的数据。

#### 验收标准

1. THE Template_Designer SHALL 提供配置数据验证规则的功能
2. THE Document_Generation_System SHALL 支持必填字段验证
3. THE Document_Generation_System SHALL 支持数据类型验证
4. THE Document_Generation_System SHALL 支持数值范围验证
5. THE Document_Generation_System SHALL 支持字符串长度验证
6. THE Document_Generation_System SHALL 支持正则表达式验证
7. IF 数据验证失败，THEN THE Document_Generator SHALL 返回包含验证错误详情的响应

### 需求 14: 数据缓存

**用户故事:** 作为系统管理员，我希望能够缓存数据源的响应，以便提高系统性能和减少外部系统负载。

#### 验收标准

1. THE Template_Designer SHALL 提供配置数据源缓存策略的功能
2. THE Document_Generation_System SHALL 使用 Redis 作为缓存存储
3. WHEN 数据源配置了缓存时，THE Document_Generator SHALL 首先检查缓存中是否存在数据
4. IF 缓存命中，THEN THE Document_Generator SHALL 使用缓存的数据而不调用数据源
5. IF 缓存未命中，THEN THE Document_Generator SHALL 调用数据源并将结果存入缓存
6. THE Template_Designer SHALL 支持配置缓存过期时间（TTL）
7. THE Document_Generation_System SHALL 提供手动清除缓存的功能

### 需求 15: 错误处理与日志

**用户故事:** 作为系统管理员，我希望系统能够妥善处理错误并记录详细日志，以便快速定位和解决问题。

#### 验收标准

1. WHEN 系统发生错误时，THE Document_Generation_System SHALL 返回标准化的错误响应（包含错误码、错误消息、时间戳）
2. THE Document_Generation_System SHALL 记录所有错误到日志系统
3. THE Document_Generation_System SHALL 记录所有 API 请求和响应的关键信息
4. THE Document_Generation_System SHALL 记录数据源调用的性能指标（响应时间、成功率）
5. THE Document_Generation_System SHALL 支持配置日志级别（DEBUG、INFO、WARN、ERROR）
6. THE Document_Generation_System SHALL 将日志持久化到文件系统或日志服务

### 需求 16: 模板预览

**用户故事:** 作为模板设计者，我希望能够预览模板效果，以便在发布前验证模板配置的正确性。

#### 验收标准

1. THE Template_Designer SHALL 提供模板预览功能
2. WHEN 用户触发预览时，THE Template_Designer SHALL 允许用户提供测试数据
3. THE Document_Generator SHALL 使用测试数据生成预览文档
4. THE Template_Designer SHALL 支持在 OnlyOffice_Editor 中直接预览渲染后的文档（无需下载）
5. THE Template_Designer SHALL 支持将预览文档下载到本地
6. THE Template_Designer SHALL 支持使用真实数据源进行预览（可选）

### 需求 17: API 文档自动生成

**用户故事:** 作为 API 调用者，我希望能够查看自动生成的 API 文档，以便了解如何调用这些 API。

#### 验收标准

1. WHEN 模板激活并生成 API 时，THE Document_Generation_System SHALL 自动生成 API 文档
2. THE Document_Generation_System SHALL 使用 OpenAPI 3.0 规范描述 API
3. THE Document_Generation_System SHALL 通过 SpringDoc OpenAPI 提供访问 API 文档的端点
4. THE Document_Generation_System SHALL 在 API 文档中包含端点路径、HTTP 方法、请求参数、响应格式
5. THE Document_Generation_System SHALL 提供 Swagger UI 界面展示 API 文档

### 需求 18: 异步文档生成

**用户故事:** 作为 API 调用者，我希望能够异步生成大型或复杂文档，以便避免请求超时。

#### 验收标准

1. WHERE 模板配置为异步生成，THE Generated_API SHALL 立即返回任务 ID
2. WHEN 异步生成请求提交时，THE Document_Generation_System SHALL 创建后台任务
3. THE Document_Generation_System SHALL 提供查询任务状态的 API
4. WHEN 任务完成时，THE Document_Generation_System SHALL 更新任务状态为已完成
5. THE Document_Generation_System SHALL 提供下载已完成文档的 API
6. IF 任务失败，THEN THE Document_Generation_System SHALL 更新任务状态为失败并记录错误信息

### 需求 19: 多租户支持

**用户故事:** 作为系统管理员，我希望系统支持多租户，以便为不同的组织提供隔离的环境。

#### 验收标准

1. THE Document_Generation_System SHALL 支持租户（Tenant）级别的数据隔离，租户代表一个独立的组织
2. WHEN 用户登录时，THE Document_Generation_System SHALL 识别用户所属的租户
3. THE Document_Generation_System SHALL 确保用户只能访问其租户范围内的模板和数据
4. THE Document_Generation_System SHALL 在数据库中使用租户 ID 隔离数据
5. THE Document_Generation_System SHALL 支持为每个租户配置独立的数据源连接
6. THE Document_Generation_System SHALL 支持一个租户下包含多个团队（Team），团队作为租户内部的协作单元
7. THE Document_Generation_System SHALL 确保不同租户之间的数据完全隔离，同一租户内的团队之间通过权限控制实现数据隔离或共享

### 需求 20: 系统监控与健康检查

**用户故事:** 作为系统管理员，我希望能够监控系统运行状态，以便及时发现和处理问题。

#### 验收标准

1. THE Document_Generation_System SHALL 提供健康检查端点（Spring Boot Actuator /actuator/health）
2. THE Document_Generation_System SHALL 监控应用各模块的运行状态（模板服务、文档生成服务、数据源服务、表达式引擎）
3. THE Document_Generation_System SHALL 监控数据库连接池状态
4. THE Document_Generation_System SHALL 监控 Redis 连接状态
5. THE Document_Generation_System SHALL 监控 API 响应时间和吞吐量
6. THE Document_Generation_System SHALL 提供系统指标的可视化仪表板
7. IF 关键模块不可用，THEN THE Document_Generation_System SHALL 发送告警通知
8. THE Document_Generation_System SHALL 监控 OnlyOffice Document Server 的健康状态（通过定期调用其健康检查端点）
9. THE Document_Generation_System SHALL 监控 Docxtemplater_Service（Node.js 服务）的健康状态（通过定期调用其健康检查端点）
10. IF OnlyOffice Document Server 或 Docxtemplater_Service 不可用，THEN THE Document_Generation_System SHALL 在健康检查端点中报告对应服务状态为 DOWN 并发送告警通知


### 需求 21: Word 和 PDF 文档生成

**用户故事:** 作为 API 调用者，我希望能够生成 Word 和 PDF 格式的文档，以便满足不同的业务场景需求。

#### 验收标准

1. THE Document_Generator SHALL 支持生成 Word (.docx) 格式文档
2. THE Document_Generator SHALL 支持生成 PDF 格式文档
3. THE Document_Generator SHALL 支持将 Word 文档转换为 PDF 格式
4. WHEN API 请求指定输出格式时，THE Document_Generator SHALL 生成对应格式的文档
5. THE Document_Generator SHALL 保持文档格式的一致性和准确性

### 需求 22: 混合模式模板设计器

**用户故事:** 作为模板设计者，我希望能够在可视化编辑和代码编辑之间切换，以便根据需求选择最合适的设计方式。

#### 验收标准

1. THE Template_Designer SHALL 使用 OnlyOffice_Editor 提供所见即所得的可视化编辑器界面
2. THE Template_Designer SHALL 使用 Monaco_Editor 提供模板语法代码编辑器界面
3. THE Template_Designer SHALL 支持在 OnlyOffice_Editor 和 Monaco_Editor 两种模式之间切换
4. WHEN 用户在 OnlyOffice_Editor 中修改模板时，THE Template_Designer SHALL 同步更新 Monaco_Editor 中的模板语法
5. WHEN 用户在 Monaco_Editor 中修改模板时，THE Template_Designer SHALL 同步更新 OnlyOffice_Editor 的显示
6. THE Monaco_Editor SHALL 提供 Docxtemplater 语法高亮和自动补全功能
7. THE OnlyOffice_Editor SHALL 通过自定义插件支持插入 Docxtemplater 模板变量（如 `{variable}`、`{#loop}...{/loop}`、`{#if condition}...{/if}`）

### 需求 23: 双表达式引擎支持

**用户故事:** 作为模板设计者，我希望能够选择使用 JavaScript 表达式或 Excel 公式，以便根据技能水平选择最熟悉的表达式语言。

#### 验收标准

1. THE Expression_Engine SHALL 支持 JavaScript 表达式语法
2. THE Expression_Engine SHALL 支持 Excel 公式语法
3. THE Template_Designer SHALL 允许用户为每个表达式选择使用的语言类型
4. WHEN 计算表达式时，THE Expression_Engine SHALL 根据配置的语言类型选择对应的解析器
5. THE Expression_Engine SHALL 支持 JavaScript 表达式的所有标准运算符和函数
6. THE Expression_Engine SHALL 支持常用的 Excel 函数（SUM、AVERAGE、IF、VLOOKUP、CONCATENATE 等）
7. IF 表达式语法错误，THEN THE Expression_Engine SHALL 返回包含详细错误位置和原因的响应
8. THE Expression_Engine SHALL 在 Security_Sandbox 中执行 JavaScript 表达式，禁止访问文件系统、网络和系统进程

### 需求 24: 批量文档生成

**用户故事:** 作为 API 调用者，我希望能够批量生成多个文档，以便提高处理效率。

#### 验收标准

1. THE Generated_API SHALL 支持批量文档生成请求
2. THE Document_Generation_System SHALL 支持单次批量生成最多 1000 个文档
3. WHEN 批量生成请求提交时，THE Document_Generation_System SHALL 创建异步批量任务
4. THE Document_Generator SHALL 为批量任务中的每个数据集生成独立的文档
5. WHEN 所有文档生成完成时，THE Document_Generator SHALL 将所有文档打包为 ZIP 文件
6. THE Document_Generation_System SHALL 提供下载批量生成结果 ZIP 文件的 API
7. THE Document_Generation_System SHALL 在批量任务中记录每个文档的生成状态（成功、失败）
8. THE Document_Generation_System SHALL 提供批量任务进度查询 API，返回当前已完成数量和总数量
9. THE Template_Designer SHALL 支持为批量任务配置部分失败处理策略：继续执行剩余任务（默认）或在失败数量达到配置阈值时停止整个批量任务

### 需求 25: 文档存储策略

**用户故事:** 作为模板设计者，我希望能够配置文档的存储策略，以便根据业务需求选择临时存储或持久化存储。

#### 验收标准

1. THE Template_Designer SHALL 提供配置文档存储策略的功能
2. THE Document_Generation_System SHALL 支持临时存储模式（生成后立即返回，不保存）
3. THE Document_Generation_System SHALL 支持持久化存储模式（保存到 MinIO 对象存储）
4. WHERE 模板配置为临时存储，THE Document_Generator SHALL 在响应中直接返回文档内容或临时下载链接
5. WHERE 模板配置为持久化存储，THE Document_Generator SHALL 将文档上传到 MinIO 并返回永久访问链接
6. WHERE 模板配置为持久化存储，THE Document_Generation_System SHALL 保存文档元数据到数据库以支持历史查询
7. THE Document_Generation_System SHALL 支持为持久化文档配置过期时间
8. THE Document_Generation_System SHALL 提供查询历史生成文档列表的功能（支持按模板、时间范围、生成状态筛选）
9. THE Document_Generation_System SHALL 提供临时存储文档的自动清理机制，默认在生成后 24 小时自动删除临时文件和对应的临时下载链接
10. THE Template_Designer SHALL 支持配置临时存储的自动清理时间（最短 1 小时，最长 72 小时，默认 24 小时）

### 需求 26: 完整的模板组件库

**用户故事:** 作为模板设计者，我希望能够使用丰富的组件库，以便设计复杂和专业的文档模板。

#### 验收标准

1. THE OnlyOffice_Editor SHALL 原生支持文本组件（标题 H1-H6、段落、富文本）
2. THE OnlyOffice_Editor SHALL 原生支持表格组件（静态表格布局和样式编辑）
3. THE Docxtemplater_Service SHALL 支持动态数据表格组件（通过循环标签 `{#items}...{/items}` 实现行级数据填充）
4. THE Docxtemplater_Service SHALL 通过 docxtemplater-image-module 支持图片组件（静态图片、动态图片 URL、Base64 图片）
5. THE Docxtemplater_Service SHALL 通过 docxtemplater-chart-module 支持图表组件（柱状图、饼图、折线图）
6. THE OnlyOffice_Editor SHALL 原生支持页眉页脚组件（支持页码、日期等动态内容）
7. THE OnlyOffice_Editor SHALL 原生支持目录组件（自动生成文档目录）
8. THE OnlyOffice_Editor SHALL 原生支持分页符组件
9. THE Docxtemplater_Service SHALL 支持文字水印和图片水印组件
10. THE OnlyOffice_Editor SHALL 原生支持书签和超链接组件（文档内跳转、外部链接）
11. THE OnlyOffice_Editor SHALL 原生支持列表组件（有序列表、无序列表、多级列表）
12. THE Docxtemplater_Service SHALL 支持条形码和二维码组件（通过自定义模块动态生成）
13. THE OnlyOffice_Editor SHALL 原生支持分栏布局组件
14. THE OnlyOffice_Editor SHALL 原生支持脚注和尾注组件
15. THE Document_Generator SHALL 支持所有组件的数据绑定功能（通过 Docxtemplater 模板变量语法）
16. THE Document_Generator SHALL 支持所有组件的条件渲染功能（通过 Docxtemplater 条件标签）

### 需求 27: 团队协作与权限管理

**用户故事:** 作为团队管理员，我希望能够管理团队成员的权限，以便控制租户内模板和 API 的访问。

#### 验收标准

1. THE Document_Generation_System SHALL 支持租户内的团队协作模式，团队作为租户内部的协作单元
2. THE Document_Generation_System SHALL 支持为模板分配访问权限（团队级别和用户级别）
3. THE Document_Generation_System SHALL 支持细粒度权限控制（查看、编辑、删除、调用 API）
4. WHEN 用户尝试访问模板时，THE Document_Generation_System SHALL 验证用户是否具有相应权限
5. IF 用户无权访问模板，THEN THE Document_Generation_System SHALL 返回 403 禁止访问错误
6. THE Document_Generation_System SHALL 支持为 API 调用分配权限
7. THE Document_Generation_System SHALL 提供权限管理界面供团队管理员配置权限
8. THE Document_Generation_System SHALL 确保权限控制在租户隔离的范围内生效，团队管理员只能管理其所属租户内的权限

### 需求 28: 多语言界面支持

**用户故事:** 作为国际用户，我希望系统界面支持多种语言，以便使用母语操作系统。

#### 验收标准

1. THE Document_Generation_System SHALL 支持英文界面（默认语言）
2. THE Document_Generation_System SHALL 支持简体中文界面
3. THE Document_Generation_System SHALL 支持繁体中文界面
4. WHEN 用户选择界面语言时，THE Document_Generation_System SHALL 切换所有界面文本到选定语言
5. THE Document_Generation_System SHALL 保存用户的语言偏好设置，默认值为英文（en-US）
6. THE Document_Generation_System SHALL 在所有错误消息和提示中使用用户选定的语言
7. WHEN 用户切换语言时，THE OnlyOffice_Editor SHALL 同步切换编辑器界面语言为用户选定的语言

### 需求 29: 性能优化

**用户故事:** 作为系统管理员，我希望系统能够满足性能要求，以便为用户提供良好的使用体验。

#### 验收标准

1. WHEN 文档生成请求提交时，THE Document_Generator SHALL 根据文档大小在以下时间内完成文档生成：小型文档（≤ 10 页）5 秒内完成、中型文档（11-100 页）15 秒内完成、大型文档（101-1000 页）30 秒内完成
2. THE Document_Generation_System SHALL 支持小于 100 TPS 的并发请求
3. THE Document_Generator SHALL 支持生成 1000 页以内的大型文档
4. THE Document_Generation_System SHALL 使用异步处理机制处理大型文档生成请求
5. THE Document_Generation_System SHALL 使用连接池优化数据库和外部 API 调用
6. THE Document_Generation_System SHALL 监控和记录所有关键操作的性能指标

### 需求 30: 模板导入导出

**用户故事:** 作为模板设计者，我希望能够导入和导出模板文件及其配置，以便在不同环境之间迁移模板或备份模板。

#### 验收标准

1. THE Template_Designer SHALL 支持从本地上传 .docx 文件作为新模板
2. WHEN 用户上传 .docx 文件时，THE Document_Generation_System SHALL 验证文件格式的有效性
3. THE Template_Designer SHALL 支持将模板导出为 .docx 文件下载到本地
4. THE Template_Designer SHALL 支持将模板的完整配置（包含数据源、表达式、验证规则）导出为 JSON 文件
5. THE Template_Designer SHALL 支持通过导入 JSON 配置文件还原模板的完整配置
6. WHEN 导入 JSON 配置时，THE Document_Generation_System SHALL 验证配置文件的结构和数据完整性
7. IF 导入的配置文件格式无效，THEN THE Document_Generation_System SHALL 返回包含具体错误位置的验证失败响应

### 需求 31: 模板分类与标签

**用户故事:** 作为模板设计者，我希望能够对模板进行分类和标签管理，以便快速查找和组织大量模板。

#### 验收标准

1. THE Template_Designer SHALL 提供树形分类管理功能（支持多级分类的创建、编辑、删除、排序）
2. THE Template_Designer SHALL 支持为模板添加一个或多个标签
3. THE Template_Designer SHALL 支持按分类筛选模板列表
4. THE Template_Designer SHALL 支持按标签筛选模板列表
5. THE Template_Designer SHALL 支持按分类和标签组合筛选模板列表
6. WHEN 删除分类时，THE Document_Generation_System SHALL 将该分类下的模板移动到默认分类
7. THE Document_Generation_System SHALL 支持标签的创建、编辑和删除管理

### 需求 32: 模板市场

**用户故事:** 作为模板设计者，我希望能够从模板市场获取预置模板，以便快速启动文档设计工作。

#### 验收标准

1. THE Template_Market SHALL 提供预置的常用模板（合同、报告、发票、证书等）
2. THE Template_Designer SHALL 支持从 Template_Market 一键复制模板到用户的工作区
3. WHEN 用户复制模板市场中的模板时，THE Document_Generation_System SHALL 创建该模板的独立副本（包含模板文件和完整配置）
4. THE Template_Market SHALL 支持用户将自己的模板分享到模板市场
5. WHEN 用户分享模板时，THE Document_Generation_System SHALL 要求用户确认分享范围（租户内部或全局公开）
6. THE Template_Market SHALL 提供模板搜索功能（按名称、分类、标签搜索）
7. THE Template_Market SHALL 显示模板的使用次数和评分信息

### 需求 33: 数据源参数化查询

**用户故事:** 作为模板设计者，我希望能够在数据源查询中使用参数，以便根据文档生成请求动态获取数据。

#### 验收标准

1. THE Template_Designer SHALL 支持在 SQL 查询中使用参数占位符（如 `:paramName`）
2. THE Template_Designer SHALL 支持在 API URL 中使用路径参数（如 `/users/{userId}`）和查询参数
3. THE Template_Designer SHALL 支持为每个参数配置默认值
4. THE Template_Designer SHALL 支持将参数标记为必填或可选
5. WHEN 文档生成请求缺少必填参数时，THE Document_Generator SHALL 返回包含缺失参数名称的验证错误响应
6. THE Document_Generation_System SHALL 对 SQL 查询参数使用预编译语句（PreparedStatement）防止 SQL 注入攻击
7. THE Document_Generation_System SHALL 对 API URL 参数进行编码处理防止注入攻击

### 需求 34: 数据管道

**用户故事:** 作为模板设计者，我希望能够定义数据处理管道，以便对数据源的数据进行多步骤的获取、转换、计算和验证。

#### 验收标准

1. THE Template_Designer SHALL 支持定义数据处理管道，包含获取、转换、计算、验证四个阶段
2. THE Data_Pipeline SHALL 支持数据源之间的依赖关系配置（一个数据源的输出作为另一个数据源的输入参数）
3. THE Template_Designer SHALL 提供数据管道的可视化配置界面（节点连线方式展示数据流向）
4. WHEN 文档生成时，THE Document_Generator SHALL 按照管道定义的依赖顺序执行各阶段
5. IF 管道中某个阶段执行失败，THEN THE Document_Generator SHALL 终止后续阶段并返回包含失败阶段信息的错误响应
6. THE Data_Pipeline SHALL 支持并行执行无依赖关系的数据源调用
7. WHEN 用户保存数据管道配置时，THE Data_Pipeline SHALL 检测数据源之间是否存在循环依赖，IF 检测到循环依赖，THEN THE Data_Pipeline SHALL 返回包含循环路径信息的错误响应并拒绝保存

### 需求 35: Webhook 通知

**用户故事:** 作为 API 调用者，我希望在文档生成完成或失败时收到通知，以便及时处理生成结果。

#### 验收标准

1. WHEN 单个文档生成完成时，THE Document_Generation_System SHALL 向配置的 Webhook URL 发送 HTTP POST 通知
2. WHEN 批量任务完成时，THE Document_Generation_System SHALL 向配置的 Webhook URL 发送 HTTP POST 通知
3. WHEN 文档生成任务失败时，THE Document_Generation_System SHALL 向配置的 Webhook URL 发送包含错误信息的 HTTP POST 通知
4. THE Template_Designer SHALL 支持为每个模板配置 Webhook URL 和自定义 payload 模板
5. THE Document_Generation_System SHALL 在 Webhook 请求中包含签名头（HMAC-SHA256）以便接收方验证请求来源
6. IF Webhook 通知发送失败，THEN THE Document_Generation_System SHALL 按照指数退避策略重试（最多重试 3 次）
7. THE Document_Generation_System SHALL 记录所有 Webhook 通知的发送状态和响应信息

### 需求 36: API 限流与配额

**用户故事:** 作为系统管理员，我希望能够控制 API 的调用频率和配额，以便保护系统资源和确保服务质量。

#### 验收标准

1. THE Rate_Limiter SHALL 支持为每个 API Key 配置每秒、每分钟、每小时的调用频率限制
2. THE Document_Generation_System SHALL 支持为每个租户配置月度 API 调用配额
3. WHEN API 调用超出频率限制时，THE Document_Generation_System SHALL 返回 HTTP 429 Too Many Requests 响应
4. WHEN API 调用超出频率限制时，THE Document_Generation_System SHALL 在响应头中包含 Retry-After 和 X-RateLimit-Remaining 信息
5. WHEN 租户月度配额耗尽时，THE Document_Generation_System SHALL 返回 HTTP 429 响应并在消息中说明配额已用完
6. THE Document_Generation_System SHALL 提供 API 使用量统计查询接口（按 API Key、按租户、按时间范围）
7. THE Document_Generation_System SHALL 使用 Redis 存储限流计数器以支持高并发场景

### 需求 37: 审计日志

**用户故事:** 作为系统管理员，我希望能够查看系统中所有关键操作的审计日志，以便满足合规要求和安全审查。

#### 验收标准

1. THE Audit_Log SHALL 记录所有模板的创建、修改、删除操作（包含操作者、操作时间、操作内容）
2. THE Audit_Log SHALL 记录所有权限变更操作（包含授权者、被授权者、权限类型）
3. THE Audit_Log SHALL 记录所有 API 调用记录（包含调用者、端点、参数摘要、响应状态码、响应时间）
4. THE Audit_Log SHALL 记录所有用户登录和登出事件（包含用户 ID、IP 地址、登录时间、登录结果）
5. THE Document_Generation_System SHALL 提供审计日志查询界面（支持按操作类型、操作者、时间范围筛选）
6. THE Document_Generation_System SHALL 支持将审计日志导出为 CSV 或 JSON 格式
7. THE Audit_Log SHALL 使用独立的数据库表存储，确保审计记录不可被业务操作修改或删除
8. THE Document_Generation_System SHALL 支持配置审计日志的保留期限（默认 365 天）


### 需求 38: 模板测试套件

**用户故事:** 作为模板设计者，我希望能够为模板创建测试用例并运行回归测试，以便确保模板修改不会引入错误。

#### 验收标准

1. THE Template_Designer SHALL 支持为每个模板创建测试用例（包含测试数据和预期结果描述）
2. THE Template_Designer SHALL 支持一键运行模板的所有测试用例
3. WHEN 测试用例运行时，THE Document_Generator SHALL 使用测试数据生成文档并与预期结果进行比对
4. WHEN 模板被修改并保存时，THE Document_Generation_System SHALL 提供自动运行关联测试用例的选项
5. THE Template_Designer SHALL 显示测试结果报告（通过数量、失败数量、失败详情）
6. THE Document_Generation_System SHALL 支持测试用例的导入和导出（JSON 格式）
7. IF 测试用例执行失败，THEN THE Document_Generation_System SHALL 在测试报告中标注失败原因和差异位置
8. THE Document_Generation_System SHALL 支持三种预期结果比对方式：变量值比对（对比生成文档中所有模板变量的渲染结果与预期值）、文本内容比对（对比生成文档的纯文本内容与预期文本）、文件快照比对（对比生成文档的二进制哈希值与基准快照）
9. THE Template_Designer SHALL 允许用户为每个测试用例选择使用的比对方式（默认为变量值比对）

### 需求 39: 文档水印与安全

**用户故事:** 作为模板设计者，我希望能够为生成的文档添加水印和安全保护，以便防止文档被未授权使用。

#### 验收标准

1. THE Document_Generator SHALL 支持为生成的文档添加文字水印（支持配置水印文本、字体大小、颜色、透明度、旋转角度）
2. THE Document_Generator SHALL 支持为生成的文档添加图片水印（支持配置水印图片、位置、透明度）
3. THE PDF_Converter SHALL 支持为 PDF 文档设置打开密码
4. THE PDF_Converter SHALL 支持为 PDF 文档设置权限控制（禁止打印、禁止复制文本、禁止编辑）
5. THE Template_Designer SHALL 提供水印和安全选项的配置界面
6. WHEN 文档生成请求指定水印配置时，THE Document_Generator SHALL 在文档渲染完成后应用水印
7. THE Document_Generator SHALL 支持动态水印内容（如使用模板变量作为水印文本）

### 需求 40: 文档合并

**用户故事:** 作为 API 调用者，我希望能够将多个生成的文档合并为一个文档，以便生成综合性报告。

#### 验收标准

1. THE Document_Generator SHALL 支持将多个已生成的 Word 文档合并为一个文档
2. THE Document_Generation_System SHALL 提供合并文档的 API 端点
3. THE Document_Generator SHALL 支持自定义合并顺序（通过请求参数指定文档 ID 列表的顺序）
4. THE Document_Generator SHALL 支持在合并文档之间自动插入分页符
5. THE Document_Generator SHALL 支持合并后自动生成文档目录
6. WHEN 合并请求中包含无效的文档 ID 时，THE Document_Generator SHALL 返回包含无效文档 ID 列表的错误响应
7. THE Document_Generator SHALL 支持将合并后的文档转换为 PDF 格式

### 需求 41: 定时生成任务

**用户故事:** 作为模板设计者，我希望能够配置定时任务自动生成文档，以便实现报表的定期自动生成。

#### 验收标准

1. THE Template_Designer SHALL 支持为模板配置定时生成任务
2. THE Scheduled_Task SHALL 支持使用 Cron 表达式配置调度规则
3. THE Template_Designer SHALL 支持启用和禁用定时任务
4. WHEN 定时任务触发时，THE Document_Generation_System SHALL 使用配置的数据源和参数自动生成文档
5. THE Document_Generation_System SHALL 记录定时任务的执行历史（执行时间、执行结果、生成的文档 ID）
6. THE Template_Designer SHALL 提供定时任务执行历史的查询界面
7. IF 定时任务执行失败，THEN THE Document_Generation_System SHALL 记录失败原因并根据配置发送告警通知
8. THE Document_Generation_System SHALL 支持为定时任务配置最大重试次数
9. IF 定时任务触发时上一次执行尚未完成，THEN THE Document_Generation_System SHALL 跳过本次触发并记录跳过日志（包含跳过原因和上一次执行的任务 ID）

### 需求 42: 数据源响应转换器

**用户故事:** 作为模板设计者，我希望能够从 API 响应中提取和转换特定数据，以便将复杂的响应结构适配到模板变量。

#### 验收标准

1. THE Template_Designer SHALL 支持使用 JSONPath 表达式从 API 响应中提取特定数据
2. THE Template_Designer SHALL 支持使用 XPath 表达式从 XML 响应中提取特定数据
3. THE Document_Generation_System SHALL 支持将嵌套的响应数据扁平化为单层键值对
4. THE Document_Generation_System SHALL 支持对数组数据进行分组操作（按指定字段分组）
5. THE Document_Generation_System SHALL 支持对数组数据进行排序操作（按指定字段升序或降序）
6. THE Template_Designer SHALL 提供响应转换规则的可视化配置界面
7. WHEN 响应转换规则执行失败时，THE Document_Generation_System SHALL 返回包含转换规则名称和错误原因的响应

### 需求 43: 模板变量管理

**用户故事:** 作为模板设计者，我希望能够统一管理模板中使用的所有变量，以便清晰了解模板的数据依赖关系。

#### 验收标准

1. THE Template_Designer SHALL 提供模板变量的统一管理界面（列表展示所有变量及其来源）
2. THE Template_Designer SHALL 支持为变量定义类型（字符串、数字、日期、布尔值、数组、对象）
3. THE Template_Designer SHALL 支持为变量配置默认值
4. THE Template_Designer SHALL 支持为变量添加描述和使用说明
5. WHEN 模板文件保存时，THE Document_Generation_System SHALL 自动扫描模板内容检测所有使用的 Docxtemplater 变量
6. IF 模板中使用了未定义的变量，THEN THE Document_Generation_System SHALL 在变量管理界面中标注该变量为"未绑定"状态
7. THE Template_Designer SHALL 支持将变量与数据源字段或表达式结果进行绑定映射

### 需求 44: 操作仪表板

**用户故事:** 作为系统管理员，我希望能够通过仪表板全面了解系统运行状况，以便做出运维决策。

#### 验收标准

1. THE Document_Generation_System SHALL 提供系统概览仪表板（展示模板总数、活跃模板数、API 调用总量、生成文档总数）
2. THE Document_Generation_System SHALL 提供实时 API 调用监控（展示最近 1 小时的调用量趋势图）
3. THE Document_Generation_System SHALL 提供文档生成成功率统计（按模板、按时间范围展示成功率和失败率）
4. THE Document_Generation_System SHALL 提供数据源健康状态监控（展示每个数据源的连通性和平均响应时间）
5. THE Document_Generation_System SHALL 提供系统资源使用情况监控（JVM 内存使用、数据库连接池使用率、Redis 内存使用）
6. THE Document_Generation_System SHALL 支持仪表板数据的自动刷新（可配置刷新间隔，默认 30 秒）
7. THE Document_Generation_System SHALL 支持仪表板数据的时间范围选择（最近 1 小时、24 小时、7 天、30 天）

### 需求 45: 模板交叉审查

**用户故事:** 作为团队管理员，我希望能够对模板进行交叉审查，以便确保模板质量和规范性。

#### 验收标准

1. THE Template_Designer SHALL 支持发起模板审查请求（指定审查人）
2. THE Document_Generation_System SHALL 支持审查工作流（提交审查 → 审查中 → 审查通过/条件通过/驳回）
3. THE Template_Designer SHALL 支持审查人在 OnlyOffice_Editor 中对模板添加批注和修改建议
4. THE Document_Generation_System SHALL 记录审查意见和审查历史
5. WHEN 审查人驳回模板时，THE Document_Generation_System SHALL 要求审查人填写驳回原因
6. WHEN 模板通过审查时，THE Document_Generation_System SHALL 自动将模板状态更新为"已审核"
7. THE Template_Designer SHALL 提供审查任务列表界面（待审查、已审查、条件通过、已驳回）
8. THE Document_Generation_System SHALL 支持配置模板发布前必须通过审查的规则
9. THE Document_Generation_System SHALL 支持多级审查流程（如初审 → 终审）
10. THE Document_Generation_System SHALL 支持"条件通过"审查状态，审查人可标记模板为条件通过并附加建议修改项列表
11. WHEN 模板被标记为条件通过时，THE Document_Generation_System SHALL 允许模板设计者查看建议修改项并选择是否修改后重新提交审查或直接进入下一审查阶段

### 需求 46: 模板版本对比

**用户故事:** 作为模板设计者，我希望能够对比模板的不同版本之间的差异，以便了解每次修改的具体内容。

#### 验收标准

1. THE Template_Designer SHALL 支持选择两个模板版本进行对比
2. THE Document_Generation_System SHALL 检测并展示两个版本之间的文本内容差异（新增、删除、修改的内容高亮显示）
3. THE Document_Generation_System SHALL 检测并展示两个版本之间的模板变量差异（新增、删除、修改的变量）
4. THE Document_Generation_System SHALL 检测并展示两个版本之间的数据源配置差异
5. THE Document_Generation_System SHALL 检测并展示两个版本之间的表达式配置差异
6. THE Template_Designer SHALL 提供并排对比视图（左侧旧版本、右侧新版本）
7. THE Template_Designer SHALL 提供差异摘要信息（变更数量统计）
8. THE Document_Generation_System SHALL 支持从对比视图中直接回滚到旧版本

### 需求 47: 模板覆盖率检查

**用户故事:** 作为模板设计者，我希望能够检查模板中所有变量和组件的数据绑定覆盖率，以便确保模板配置的完整性。

#### 验收标准

1. THE Document_Generation_System SHALL 自动扫描模板文件中所有 Docxtemplater 标签（变量、循环、条件等）
2. THE Document_Generation_System SHALL 检查每个模板标签是否已绑定到数据源字段或表达式结果
3. THE Document_Generation_System SHALL 计算模板的数据绑定覆盖率（已绑定标签数 / 总标签数 × 100%）
4. THE Template_Designer SHALL 在模板编辑界面中展示覆盖率指标和未绑定标签列表
5. IF 模板覆盖率低于配置的阈值（默认 100%），THEN THE Document_Generation_System SHALL 在模板激活时显示警告
6. THE Document_Generation_System SHALL 检查数据源字段是否在模板中被使用（反向覆盖率：检测未使用的数据源字段）
7. THE Template_Designer SHALL 提供覆盖率报告的导出功能（JSON 或 PDF 格式）
8. THE Document_Generation_System SHALL 在模板保存时自动执行覆盖率检查并更新覆盖率指标

### 需求 48: 用户管理

**用户故事:** 作为系统管理员，我希望能够管理用户的注册、登录和个人资料，以便控制系统访问和用户身份。

#### 验收标准

1. THE User_Management SHALL 提供用户注册功能（用户名、邮箱、密码）
2. THE User_Management SHALL 对注册密码执行强度校验（最少 8 位，包含大小写字母、数字和特殊字符）
3. THE User_Management SHALL 提供基于 JWT 的用户登录认证功能
4. WHEN 用户登录成功时，THE User_Management SHALL 返回 JWT 访问令牌（Access Token，有效期 2 小时）和刷新令牌（Refresh Token，有效期 7 天）
5. THE User_Management SHALL 提供通过刷新令牌获取新访问令牌的功能
6. THE User_Management SHALL 提供密码重置功能（通过注册邮箱发送重置链接，链接有效期 30 分钟）
7. THE User_Management SHALL 提供用户个人资料管理功能（修改昵称、头像、联系方式）
8. THE User_Management SHALL 支持为用户分配角色（SUPER_ADMIN、TENANT_ADMIN、TEAM_ADMIN、USER）
9. WHEN 用户连续 5 次登录失败时，THE User_Management SHALL 锁定该用户账户 30 分钟
10. THE User_Management SHALL 使用 BCrypt 算法对用户密码进行哈希存储

### 需求 49: 租户管理

**用户故事:** 作为超级管理员，我希望能够创建和管理租户，以便为不同组织提供独立的系统环境。

#### 验收标准

1. THE Tenant_Management SHALL 提供创建新租户的功能（租户名称、联系人、联系邮箱）
2. THE Tenant_Management SHALL 提供编辑租户基本信息的功能
3. THE Tenant_Management SHALL 提供启用和禁用租户的功能
4. WHEN 租户被禁用时，THE Document_Generation_System SHALL 拒绝该租户下所有用户的访问请求并返回 403 响应
5. THE Tenant_Management SHALL 提供租户成员管理功能（添加用户到租户、从租户移除用户、设置用户在租户内的角色）
6. THE Tenant_Management SHALL 支持为每个租户配置资源配额（最大模板数量、最大 API 调用次数/月、最大存储空间）
7. THE Tenant_Management SHALL 提供租户资源使用情况查询功能（当前模板数量、本月 API 调用次数、已用存储空间）
8. IF 租户资源使用达到配额上限，THEN THE Document_Generation_System SHALL 拒绝超出配额的操作并返回包含配额信息的错误响应
9. THE Tenant_Management SHALL 提供租户列表查询功能（支持按名称、状态筛选）

### 需求 50: 数据源敏感信息安全

**用户故事:** 作为安全管理员，我希望数据源配置中的敏感信息得到加密保护，以便防止凭据泄露。

#### 验收标准

1. THE Document_Generation_System SHALL 对数据源配置中的所有敏感信息（密码、API Key、OAuth Token、Bearer Token、Basic Auth 凭据）使用 AES-256-GCM 算法加密后存储到数据库
2. WHEN 系统读取数据源配置用于文档生成时，THE Document_Generation_System SHALL 在内存中解密敏感信息，解密后的明文不写入日志或持久化存储
3. WHEN 用户通过 API 或界面查看数据源配置时，THE Document_Generation_System SHALL 对敏感信息进行脱敏显示（仅显示前 4 位和后 4 位，中间用星号替代；长度不足 8 位的全部显示为星号）
4. THE Document_Generation_System SHALL 支持加密密钥轮换功能，管理员可触发密钥轮换操作
5. WHEN 加密密钥轮换时，THE Document_Generation_System SHALL 使用新密钥重新加密所有已存储的敏感信息，并在轮换完成前保持旧密钥可用
6. THE Document_Generation_System SHALL 将加密密钥存储在独立的密钥管理配置中（如环境变量或外部密钥管理服务），禁止将密钥硬编码在源代码或配置文件中

### 需求 51: 表达式安全沙箱

**用户故事:** 作为安全管理员，我希望 JavaScript 表达式在安全沙箱中执行，以便防止恶意代码对系统造成损害。

#### 验收标准

1. THE Security_Sandbox SHALL 限制 JavaScript 表达式的执行时间（默认上限 5 秒，可配置）
2. THE Security_Sandbox SHALL 限制 JavaScript 表达式的内存使用（默认上限 64MB，可配置）
3. THE Security_Sandbox SHALL 禁止 JavaScript 表达式访问文件系统（禁用 fs、path 等模块）
4. THE Security_Sandbox SHALL 禁止 JavaScript 表达式发起网络请求（禁用 http、https、net、fetch 等模块和全局函数）
5. THE Security_Sandbox SHALL 禁止 JavaScript 表达式访问系统进程（禁用 child_process、process.exit 等）
6. THE Security_Sandbox SHALL 禁止 JavaScript 表达式访问 eval、Function 构造函数等动态代码执行能力
7. IF JavaScript 表达式执行超出时间限制，THEN THE Security_Sandbox SHALL 终止执行并返回超时错误响应
8. IF JavaScript 表达式执行超出内存限制，THEN THE Security_Sandbox SHALL 终止执行并返回内存溢出错误响应
9. THE Security_Sandbox SHALL 提供白名单机制，仅允许表达式访问预定义的安全函数和数据上下文对象

### 需求 52: 模板状态机

**用户故事:** 作为模板设计者，我希望模板有明确的生命周期状态和转换规则，以便规范模板从创建到发布的流程。

#### 验收标准

1. THE Template_State_Machine SHALL 定义以下模板状态：DRAFT（草稿）、PENDING_REVIEW（待审查）、REVIEWED（已审核）、ACTIVE（已激活）、ARCHIVED（已归档）
2. THE Template_State_Machine SHALL 允许以下状态转换：DRAFT → PENDING_REVIEW（提交审查）、PENDING_REVIEW → REVIEWED（审查通过）、PENDING_REVIEW → DRAFT（审查驳回，退回修改）、REVIEWED → ACTIVE（激活发布）、ACTIVE → ARCHIVED（归档）、ARCHIVED → DRAFT（从归档恢复为草稿）
3. THE Template_State_Machine SHALL 禁止所有未定义的状态转换，并在尝试非法转换时返回包含当前状态和目标状态的错误响应
4. WHERE 系统配置为审查必须通过才能发布，THE Template_State_Machine SHALL 禁止从 DRAFT 直接转换到 ACTIVE
5. WHERE 系统配置为无需审查即可发布，THE Template_State_Machine SHALL 允许从 DRAFT 直接转换到 ACTIVE（跳过 PENDING_REVIEW 和 REVIEWED 状态）
6. WHEN 模板状态发生转换时，THE Document_Generation_System SHALL 记录状态变更到审计日志（包含操作者、原状态、目标状态、转换时间）
7. THE Template_Designer SHALL 在模板详情界面中展示当前状态和可用的状态转换操作按钮
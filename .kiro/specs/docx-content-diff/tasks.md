# Implementation Plan: docx-content-diff

## Overview

为现有片段版本比较系统增加 .docx 文件的实际文本内容差异对比能力。后端使用 JDK ZipInputStream + SAXParser 提取文本，java-diff-utils 计算差异；前端在 SegmentVersionDialog 中渲染统一 diff 视图。

## Tasks

- [x] 1. 添加 Maven 依赖与新增 DTO/Record 类
  - [x] 1.1 在 `backend/pom.xml` 中添加 `io.github.java-diff-utils:java-diff-utils:4.15` 依赖
    - _Requirements: 2.1_
  - [x] 1.2 创建 `com.docgen.dto.ContentDiffLine` 类，包含 DiffType 枚举（EQUAL/ADDED/REMOVED/MODIFIED）、oldLineNumber、newLineNumber、oldText、newText 字段
    - _Requirements: 2.6_
  - [x] 1.3 创建 `com.docgen.dto.ExtractedText` record，包含 text 和 truncated 字段
    - _Requirements: 6.1_
  - [x] 1.4 创建 `com.docgen.dto.ContentDiffResult` record，包含 lines（List\<ContentDiffLine\>）、contentChanged、truncated 字段
    - _Requirements: 3.3, 3.4, 3.6_
  - [x] 1.5 扩展 `SegmentVersionDiffResult`，新增 contentDiffs（List\<ContentDiffLine\>）、contentChanged（boolean）、truncated（boolean）字段及 getter/setter
    - _Requirements: 3.3, 3.4, 3.6_
  - [x] 1.6 在 `ErrorCode` 中添加 `CONTENT_DIFF_EXTRACTION_FAILED` 常量
    - _Requirements: 1.5_

- [x] 2. 实现 DocxTextExtractor 组件
  - [x] 2.1 创建 `com.docgen.service.DocxTextExtractor`，实现 `extractText(InputStream docxStream, int maxBytes)` 方法
    - 使用 ZipInputStream 遍历 ZIP 条目，找到 `word/document.xml`、`word/header*.xml`、`word/footer*.xml`
    - 使用 SAXParser 解析 XML，监听 `<w:p>` 和 `<w:t>` 元素提取文本
    - 段落内所有 `<w:t>` 文本拼接为一行，段落间以换行分隔，空段落跳过
    - 超过 maxBytes 时截断并追加 `[... content truncated ...]` 标记
    - 无 `word/document.xml` 或非有效 ZIP 时抛出 BusinessException(CONTENT_DIFF_EXTRACTION_FAILED)
    - _Requirements: 1.1, 1.2, 1.3, 1.5, 1.6, 6.1_
  - [x] 2.2 实现 `extractTextFromMinio(String filePath)` 方法
    - 从 MinIO 读取文件流，调用 extractText
    - 文件不存在时抛出 BusinessException(SEGMENT_FILE_NOT_FOUND)
    - _Requirements: 1.1, 1.4_
  - [x] 2.3 编写 Property 1 属性测试：SAX 文本提取保留所有段落文本内容
    - **Property 1: SAX 文本提取保留所有段落文本内容**
    - 使用 jqwik 生成随机 OpenXML 片段（含多 `<w:r>` 拆分的 `<w:t>` 节点），验证提取结果包含所有原始文本且段落正确分行
    - **Validates: Requirements 1.2, 1.3**
  - [x] 2.4 编写 Property 6 属性测试：文本提取截断边界
    - **Property 6: 文本提取截断边界**
    - 使用 jqwik 生成超过 500KB 的文本内容，验证 truncated=true 且文本长度不超过 500KB + 截断标记长度
    - **Validates: Requirements 6.1**
  - [x] 2.5 编写 DocxTextExtractor 单元测试
    - 测试正常 .docx 提取、多段落多 run 文档、空文档、非 .docx 文件异常、超大文件截断
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 6.1_

- [x] 3. 实现 ContentDiffService 服务
  - [x] 3.1 创建 `com.docgen.service.ContentDiffService`，实现 `computeContentDiff(String oldFilePath, String newFilePath)` 方法
    - 调用 DocxTextExtractor 提取两个版本的文本
    - 使用 java-diff-utils 的 `DiffUtils.diff()` 计算逐行差异
    - 将 Patch\<String\> 的 Delta 转换为 ContentDiffLine 列表（INSERT→ADDED, DELETE→REMOVED, CHANGE→MODIFIED, 未变更→EQUAL）
    - 差异行数超过 2000 时截断并设置 truncated=true
    - 任一文件的 truncated 标志传播到最终结果
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 6.2_
  - [x] 3.2 实现内部 `computeLineDiff(String oldText, String newText)` 方法封装 java-diff-utils 调用逻辑
    - 文本按 `\n` 分割为行列表
    - 遍历 Patch.getDeltas() 生成 ContentDiffLine，未被 delta 覆盖的行标记为 EQUAL
    - 正确设置 oldLineNumber 和 newLineNumber
    - _Requirements: 2.1, 2.2, 2.6_
  - [x] 3.3 编写 Property 2 属性测试：Diff 往返重建
    - **Property 2: Diff 往返重建**
    - 使用 jqwik 生成随机文本对，验证从 diff 结果可重建 oldText 和 newText
    - **Validates: Requirements 2.1, 2.2, 2.4, 2.6**
  - [x] 3.4 编写 Property 3 属性测试：自身 Diff 恒等性
    - **Property 3: 自身 Diff 恒等性**
    - 使用 jqwik 生成随机文本，验证自身 diff 全为 EQUAL 且行数等于原文行数
    - **Validates: Requirements 2.3**
  - [x] 3.5 编写 Property 4 属性测试：contentChanged 标志一致性
    - **Property 4: contentChanged 标志一致性**
    - 使用 jqwik 生成随机文本对，验证 contentChanged 为 true 当且仅当两文本不相等
    - **Validates: Requirements 3.4**
  - [x] 3.6 编写 Property 7 属性测试：Diff 行数截断边界
    - **Property 7: Diff 行数截断边界**
    - 使用 jqwik 生成产生超过 2000 行 diff 的文本对，验证 truncated=true 且行数不超过 2000
    - **Validates: Requirements 6.2**
  - [x] 3.7 编写 ContentDiffService 单元测试
    - 测试正常 diff、相同文本、空文本、MinIO 读取失败优雅降级、行数截断
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 6.2, 6.3_

- [x] 4. 扩展 SegmentVersionService 和 Controller
  - [x] 4.1 修改 `SegmentVersionService.compareSegmentVersions()` 方法签名，增加 `boolean includeContentDiff` 参数
    - 始终调用 ContentDiffService 计算 contentChanged 标志
    - includeContentDiff=true 时填充 contentDiffs 列表
    - includeContentDiff=false 时 contentDiffs 返回空列表
    - 使用 try-catch 包裹 ContentDiffService 调用，异常时记录 warn 日志并返回降级结果（空 contentDiffs、contentChanged=false）
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 6.3_
  - [x] 4.2 修改 `CompositeTemplateController.compareSegmentVersions()` 方法，增加 `@RequestParam(defaultValue = "false") boolean includeContentDiff` 参数并传递给 Service
    - _Requirements: 3.1, 3.2_
  - [x] 4.3 编写 SegmentVersionService 集成测试
    - 测试 includeContentDiff=true/false 两种场景、异常降级
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 6.3_

- [x] 5. Checkpoint - 后端功能验证
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. 前端 TypeScript 接口与 API 扩展
  - [x] 6.1 在 `frontend/src/api/composite-templates.ts` 中新增 `ContentDiffLine` 接口定义
    - 包含 type、oldLineNumber、newLineNumber、oldText、newText 字段
    - _Requirements: 2.6, 3.3_
  - [x] 6.2 扩展 `SegmentVersionDiffResult` 接口，新增 contentDiffs、contentChanged、truncated 字段
    - _Requirements: 3.3, 3.4, 3.6_
  - [x] 6.3 修改 `compareSegmentVersions` 函数签名，增加 `includeContentDiff` 可选参数（默认 false），传递到 API 请求 params 中
    - _Requirements: 3.2_

- [x] 7. 前端内容差异视图实现
  - [x] 7.1 修改 `SegmentVersionDialog.vue` 的 `handleCompare` 方法，调用 API 时传递 `includeContentDiff: true`
    - _Requirements: 4.1_
  - [x] 7.2 在 `SegmentVersionDialog.vue` 中实现内容差异展示区域
    - contentChanged=true 时在元数据 diff 表格下方渲染统一 diff 视图
    - contentChanged=false 时显示"内容无差异"成功提示（el-tag type="success"）
    - truncated=true 时在 diff 视图顶部显示截断警告（el-alert type="warning"）
    - 显示"模板文本对比（非所见即所得）"提示标签
    - _Requirements: 4.1, 4.2, 4.7, 4.8, 4.9_
  - [x] 7.3 实现 diff 行渲染逻辑
    - REMOVED 行：红色背景 + "-" 前缀
    - ADDED 行：绿色背景 + "+" 前缀
    - MODIFIED 行：旧文本红色背景 + 新文本绿色背景，上下堆叠
    - EQUAL 行：浅灰背景 + 空格前缀
    - 每行显示旧行号和新行号
    - 使用等宽字体，最大高度 500px 超出滚动
    - _Requirements: 4.3, 4.4, 4.5, 4.6, 5.1, 5.2, 5.3_
  - [x] 7.4 实现连续 EQUAL 行折叠逻辑
    - 超过 5 行连续 EQUAL 行时折叠，显示"... N 行无变更 ..."
    - 点击可展开显示完整内容
    - _Requirements: 5.4_
  - [x] 7.5 编写 Property 5 属性测试（fast-check）：连续 EQUAL 行折叠逻辑
    - **Property 5: 连续 EQUAL 行折叠逻辑**
    - 使用 fast-check 生成随机 diff 结果，验证超过 5 行连续 EQUAL 行被正确折叠，展开后恢复原始行
    - **Validates: Requirements 5.4**
  - [x] 7.6 编写 SegmentVersionDialog 组件单元测试（Vitest）
    - 测试 includeContentDiff=true 参数传递、contentChanged 渲染、truncated 警告、折叠展开
    - _Requirements: 4.1, 4.2, 4.7, 4.9, 5.4_

- [x] 8. i18n 国际化支持
  - [x] 8.1 在 `zh-CN.json`、`zh-TW.json`、`en-US.json` 中添加 `workspace.segment.contentDiff` 命名空间下的所有 i18n key
    - title、noChanges、truncatedWarning、textDiffNote、collapsedLines、expandLines、lineOld、lineNew
    - _Requirements: 5.5_

- [x] 9. Final checkpoint - 全功能验证
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- All tasks are required
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- 后端使用 Java 17 + Spring Boot 3.2，前端使用 Vue 3 + TypeScript + Element Plus
- PBT 后端使用 jqwik，前端使用 fast-check

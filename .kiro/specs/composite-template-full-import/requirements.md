# 需求: 组合模板完整部署包导入导出

## 背景

当前系统的组合模板 (COMPOSITE) 导入导出存在以下缺陷:

1. **导出 ZIP 缺少参数定义**: `CompositeImportExportService.exportAsZip()` 导出的 ZIP 包含 `config.json`、`segments/*.docx`、`test-data.json`、`coverage-report.json`，但**不包含参数定义树** (`parameters.json`)
2. **导入 ZIP 不恢复参数**: `importFromZip()` 只恢复模板元数据、assembly_config 和测试数据，**不恢复参数定义**
3. **导出 config.json 缺少关键字段**: 不包含 `outputFormat`、`storageStrategy`、`async`、`reviewRequired` 等模板属性
4. **前端模板列表页缺少 ZIP 导入入口**: 只有 `.docx` 导入和 `.json` 配置导入按钮，没有组合模板 ZIP 包导入按钮
5. **导出限制过严**: 当前要求模板必须是 ACTIVE 状态才能导出 ZIP，但开发/测试阶段 DRAFT 状态也需要导出
6. **缺少 header/footer .docx 文件导出**: assembly_config 中的 `headerFilePath` 和 `footerFilePath` 引用的文件未包含在 ZIP 中

## 需求列表

### R1: 导出 ZIP 包含完整参数定义树
- 导出 ZIP 中新增 `parameters.json` 文件
- 包含完整的参数树结构 (嵌套 children)
- 每个参数包含: name, parameterType, dataType, required, defaultValue, description, sortOrder, expressionText, expressionType, validationRules, children
- 不包含 id、templateId、parentId、version、createdAt、updatedAt 等运行时字段

### R2: 导入 ZIP 恢复参数定义树
- 导入时读取 `parameters.json`，递归创建参数定义
- 保持树结构 (parent-child 关系)
- 保留 parameterType (REQUEST/DERIVED)、expressionText、expressionType、validationRules
- 如果 `parameters.json` 不存在则跳过 (向后兼容)

### R3: 导出 config.json 包含完整模板属性
- config.json 中增加: outputFormat, storageStrategy, async, reviewRequired, templateType
- 导入时恢复这些属性

### R4: 导出包含 header/footer .docx 文件
- 扫描 assembly_config 中所有 segment 的 headerFilePath 和 footerFilePath
- 将引用的 .docx 文件从 MinIO 下载并打包到 ZIP 的 `headers/` 和 `footers/` 目录
- 导入时上传这些文件到 MinIO 并更新 assembly_config 中的路径引用

### R5: 放宽导出状态限制
- DRAFT 和 ACTIVE 状态的组合模板都可以导出 ZIP
- 导出时记录源模板的状态，但导入后始终为 DRAFT 状态

### R6: 前端模板列表页增加 ZIP 导入按钮
- 在模板列表页的导入按钮区域增加"导入组合模板包"按钮
- 接受 `.zip` 文件
- 调用 `POST /api/composite-templates/import` 接口
- 导入成功后刷新列表并显示成功提示

### R7: 导出 ZIP 包含渲染配置 (watermark/barcode)
- 如果模板有关联的渲染配置 (watermark、barcodes)，导出到 `render-config.json`
- 导入时作为模板的附加元数据存储 (可选，不存在则跳过)

---

### R7 implementation status (audit WS-03-T04, 2026-04-26) — English

**Status:** **Deferred.** `render-config.json` is **not** part of composite ZIP export or import in the current codebase.

**Authoritative rationale and follow-up criteria:** see `docs/audits/full-project-review-2026-04-26/17-composite-r7-render-config-scope.md`.

**Summary:** Implementation is postponed until a reviewed JSON schema, bounded parsing under ZIP import limits, SSRF-safe handling of any embedded resource references, and a clear persistence model exist. Until then, **WS-03-T05** and **WS-03-T06** in the audit task package remain **out of scope**.

## 验收标准

1. 从系统导出一个包含参数、segment、测试数据的 COMPOSITE 模板 ZIP
2. 在另一个租户/环境中导入该 ZIP
3. 导入后的模板具有完整的参数树、assembly_config、测试数据
4. 导入后的模板可以正常渲染文档
5. 前端可以通过按钮直接导入 ZIP 包

# 任务列表

## Task 1: 扩展 CompositeExportConfig 内部类
- [x] 在 `CompositeImportExportService.CompositeExportConfig` 中新增字段: `outputFormat`, `storageStrategy`, `async`, `reviewRequired`, `sourceStatus`
- [x] 在 `CompositeExportConfig.SegmentExportEntry` 中新增字段: `headerFileName`, `footerFileName`, `pageNumberFormat`, `pageNumberStart`
- [x] 新增内部类 `ParameterExportEntry` (name, parameterType, dataType, required, defaultValue, description, sortOrder, expressionText, expressionType, validationRules, children)

## Task 2: 修改 exportAsZip() — 导出参数和 header/footer
- [x] 移除 ACTIVE 状态限制，改为 DRAFT 或 ACTIVE
- [x] 在 `buildExportConfig()` 中填充新增的模板属性字段
- [x] 在 `buildExportConfig()` 中填充 SegmentExportEntry 的 headerFileName/footerFileName/pageNumberFormat/pageNumberStart
- [x] 注入 `ParameterService` 依赖
- [x] 导出 `parameters.json`: 调用 `getParameterTree()` 转换为 `ParameterExportEntry` 列表并写入 ZIP
- [x] 导出 header/footer .docx: 扫描所有 segment 的 headerFilePath/footerFilePath，去重下载，写入 ZIP 的 `headers/` 和 `footers/` 目录

## Task 3: 修改 importFromZip() — 导入参数和 header/footer
- [x] 解析 ZIP 中的 `parameters.json` 文件
- [x] 解析 ZIP 中的 `headers/*.docx` 和 `footers/*.docx` 文件
- [x] 上传 header/footer 文件到 MinIO，建立 fileName → filePath 映射
- [x] 在 `rebuildAssemblyConfig()` 中恢复 headerFilePath/footerFilePath/pageNumberFormat/pageNumberStart
- [x] 恢复模板属性: outputFormat, storageStrategy, async, reviewRequired
- [x] 导入参数树: 递归创建 ParameterDefinition 实体，保留所有元数据

## Task 4: 前端 — 模板列表页增加 ZIP 导入按钮
- [x] 在 `frontend/src/views/templates/Index.vue` 中增加"导入组合模板包"按钮和隐藏 file input (accept=".zip")
- [x] 添加 `handleImportZip()` 处理函数，调用 `importCompositeFromZip()`
- [x] 在三语言 i18n 文件中增加 `template.importCompositePackage` 键

## Task 5: 编译验证
- [x] 后端编译通过 (`mvn compile`)
- [x] 前端编译通过 (`vue-tsc --noEmit`)

## Task 6: R7 `render-config.json` (DEFERRED — WS-03-T04, 2026-04-26)

**English decision record:** `docs/audits/full-project-review-2026-04-26/17-composite-r7-render-config-scope.md`

- [ ] **Deferred:** Do not implement `render-config.json` in composite ZIP export/import until an approved JSON schema, persistence model on `Template` (or related entity), and SSRF-safe validation rules exist.
- [ ] When scope reopens, implement using follow-up task cards (audit **WS-03-T05** / **WS-03-T06** or their replacements) and extend the ZIP allowlist under `composite-import.zip` limits.

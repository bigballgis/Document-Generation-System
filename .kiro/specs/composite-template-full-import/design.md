# 设计: 组合模板完整部署包导入导出

## ZIP 包结构

```
composite-template.zip
├── config.json              # 模板元数据 + assembly_config (含 header/footer 路径映射)
├── parameters.json          # 参数定义树 (完整元数据)
├── segments/
│   ├── Cover_Page.docx
│   ├── Part_A_Definitions.docx
│   └── ...
├── headers/
│   ├── standard-header.docx
│   └── ...
├── footers/
│   ├── standard-footer.docx
│   └── ...
├── test-data.json           # 测试用例
└── render-config.json       # 渲染配置 (watermark/barcode, 可选)
```

## 数据格式

### config.json (扩展后)

```json
{
  "templateName": "...",
  "templateDescription": "...",
  "outputFormat": "WORD",
  "storageStrategy": "TEMP",
  "async": false,
  "reviewRequired": false,
  "sourceStatus": "ACTIVE",
  "segments": [
    {
      "segmentName": "Cover Page",
      "segmentType": "COVER",
      "position": 1,
      "enabled": true,
      "pageBreakBefore": false,
      "conditionExpression": null,
      "dataScope": null,
      "headerFileName": "standard-header",
      "footerFileName": "standard-footer",
      "pageNumberFormat": null,
      "pageNumberStart": null
    }
  ]
}
```

### parameters.json

```json
[
  {
    "name": "bank",
    "parameterType": "REQUEST",
    "dataType": "OBJECT",
    "required": true,
    "defaultValue": null,
    "description": "银行信息",
    "sortOrder": 0,
    "expressionText": null,
    "expressionType": null,
    "validationRules": null,
    "children": [
      {
        "name": "legal_name",
        "parameterType": "REQUEST",
        "dataType": "STRING",
        "required": true,
        ...
        "children": []
      }
    ]
  }
]
```

## 后端修改

### 1. CompositeExportConfig 扩展

文件: `CompositeImportExportService.java` 内部类 `CompositeExportConfig`

新增字段:
- `outputFormat`, `storageStrategy`, `async`, `reviewRequired`, `sourceStatus`

`SegmentExportEntry` 新增字段:
- `headerFileName`, `footerFileName`, `pageNumberFormat`, `pageNumberStart`

### 2. 参数导出 DTO

新增内部类 `ParameterExportEntry`:
```java
static class ParameterExportEntry {
    String name;
    String parameterType;
    String dataType;
    boolean required;
    String defaultValue;
    String description;
    int sortOrder;
    String expressionText;
    String expressionType;
    Map<String, Object> validationRules;
    List<ParameterExportEntry> children;
}
```

### 3. exportAsZip() 修改

在 `CompositeImportExportService.exportAsZip()` 中:

1. 移除 ACTIVE 状态限制，改为 DRAFT 或 ACTIVE 都可导出
2. 导出 `parameters.json`: 调用 `ParameterService.getParameterTree()` 获取参数树，转换为 `ParameterExportEntry` 列表
3. 导出 header/footer .docx: 扫描 assembly_config 中所有 segment 的 headerFilePath/footerFilePath，去重后下载并打包
4. 在 config.json 中记录 header/footer 的文件名映射 (headerFileName/footerFileName)
5. 在 SegmentExportEntry 中增加 pageNumberFormat/pageNumberStart

### 4. importFromZip() 修改

在 `CompositeImportExportService.importFromZip()` 中:

1. 解析 `parameters.json` → 递归创建参数定义
2. 解析 `headers/*.docx` 和 `footers/*.docx` → 上传到 MinIO
3. 重建 assembly_config 时，将 headerFileName/footerFileName 映射回 MinIO 路径
4. 恢复 outputFormat、storageStrategy 等模板属性
5. 解析 `render-config.json` (可选) → 存储为模板描述的一部分或单独字段

### 5. 参数树导入方法

在 `ParameterService` 中新增:
```java
@Transactional
public void importParameterTree(Long templateId, List<ParameterExportEntry> entries, Long parentId)
```

递归创建参数，保留所有元数据 (parameterType, expressionText, validationRules 等)。

### 6. 依赖注入

`CompositeImportExportService` 需要注入 `ParameterService` (新增依赖)。

## 前端修改

### 1. 模板列表页增加 ZIP 导入按钮

文件: `frontend/src/views/templates/Index.vue`

在现有导入按钮旁增加:
```html
<el-button @click="importZipInput?.click()">
  {{ $t('template.importCompositePackage') }}
</el-button>
<input ref="importZipInput" type="file" accept=".zip" style="display: none"
       @change="handleImportZip" />
```

### 2. 导入处理函数

```typescript
async function handleImportZip(event: Event) {
  const file = input.files?.[0]
  if (!file) return
  await importCompositeFromZip(file)
  ElMessage.success(t('message.importSuccess'))
  fetchTemplates()
}
```

### 3. i18n 新增

三语言文件中增加 `template.importCompositePackage` 键。

## 不修改的部分

- 数据库 schema: 无需新增表或字段
- Flyway 迁移: 无需
- SecurityConfig: 现有 `/api/composite-templates/**` 已有权限配置
- Docker/docker-compose: 无需修改

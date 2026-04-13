---
description: 国际化规范，包括支持语言、i18n key 命名、前端翻译使用和后端错误消息映射
inclusion: auto
fileMatchPattern: '**/i18n/**,**/*.vue,**/views/**/*.vue,**/components/**/*.vue'
---

# 国际化 (i18n) 规范

## 支持语言

| 语言 | 代码 | 文件 | 说明 |
|------|------|------|------|
| English | en-US | `frontend/src/i18n/en-US.json` | 默认语言 |
| 简体中文 | zh-CN | `frontend/src/i18n/zh-CN.json` | |
| 繁体中文 | zh-TW | `frontend/src/i18n/zh-TW.json` | |

## 前端 i18n 规则

- 所有用户可见文本必须使用 i18n key，禁止硬编码中文或英文
- 使用 `$t('key')` 或 `t('key')` (Composition API) 引用翻译
- key 命名格式: `{module}.{page}.{element}`，如 `template.list.createButton`
- 新增功能时必须同时更新三个语言文件
- 错误消息也必须国际化

## key 命名示例

```json
{
  "segment": {
    "list": {
      "title": "段落列表",
      "createButton": "创建段落",
      "searchPlaceholder": "搜索段落名称..."
    },
    "form": {
      "name": "段落名称",
      "description": "描述"
    },
    "error": {
      "deleteReferenced": "无法删除被引用的段落"
    }
  }
}
```

## 后端 i18n

- 后端错误消息通过 ErrorCode 返回，前端根据 code 映射为本地化文本
- 后端日志使用英文（不做国际化）

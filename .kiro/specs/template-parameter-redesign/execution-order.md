# Execution Order: Template Parameter Redesign

本功能按功能边界拆分为 4 个子 Spec，串行执行。

## 子 Spec 列表

| 顺序 | 子 Spec | 目录 | 描述 | 依赖 |
|------|---------|------|------|------|
| 1 | backend-core | backend-core/ | 数据库迁移 + Entity + DTO + Repository + ParameterService + TemplateScanService + ErrorCode | 无 |
| 2 | backend-validation-coverage | backend-validation-coverage/ | ParameterValidationService + 覆盖率重构 + DocumentGeneratorService 简化 + Parameter Schema | backend-core |
| 3 | backend-cleanup | backend-cleanup/ | 移除旧代码 (DataSource/Expression/TemplateVariable) + 更新依赖服务 + 配置更新 | backend-validation-coverage |
| 4 | frontend | frontend/ | ParameterTableTab + 前端组件 + Store/Router/i18n 更新 | backend-core |

## 需求覆盖映射

| 需求 | 子 Spec |
|------|---------|
| Req 1: 参数表数据模型 | backend-core |
| Req 2: 参数表 CRUD API | backend-core |
| Req 3: 参数与模板占位符自动关联 | backend-core |
| Req 5: 衍生参数表达式集成 | backend-validation-coverage |
| Req 6: 文档生成时参数验证 | backend-validation-coverage |
| Req 7: API 参数文档自动生成 | backend-validation-coverage |
| Req 8: 基于测试用例的覆盖率检查 | backend-validation-coverage |
| Req 9: 数据源彻底移除与数据迁移 | backend-core (迁移脚本) |
| Req 10: 旧架构代码彻底移除 | backend-cleanup |
| Req 4: 参数表低代码管理界面 | frontend |

## 执行规则

- 每个子 Spec 独立执行完整的 tasks.md 流程
- 前一个子 Spec 完成后才开始下一个
- 每个子 Spec 完成后验证编译和测试通过

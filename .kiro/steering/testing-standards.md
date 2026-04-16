---
inclusion: auto
name: testing-standards
description: 测试标准，包括 PBT (jqwik/fast-check)、单元测试、集成测试和前端测试规范
---

# 测试标准

## PBT 必须使用场景

往返一致性、不变量验证、幂等性、排序/合并正确性、安全属性、状态机转换

## jqwik (后端)

- 类名: `XxxPropertyTest.java`
- 路径: `backend/src/test/java/com/docgen/property/`
- 参考: #[[file:backend/src/test/java/com/docgen/property/TemplateStateMachinePropertyTest.java]]

```java
@Property(tries = 100)
void propertyName(@ForAll @From("providerName") Type input) {
    assertThat(service.method(input)).satisfies(r -> ...);
}
@Provide Arbitrary<Type> providerName() { return Arbitraries.of(...); }
```

## fast-check (前端)

- 文件名: `*.property.test.ts`
- 路径: `frontend/src/__tests__/`
- 参考: #[[file:frontend/src/__tests__/parameterPath.property.test.ts]]

## 单元测试

- 后端: JUnit 5 + Mockito，路径对应源码，每个 Service 覆盖正常/异常/边界
- 前端: Vitest + @vue/test-utils，路径: `frontend/src/__tests__/`

## 集成测试

- 后端: Testcontainers (PostgreSQL)，路径: `backend/src/test/java/com/docgen/integration/`
- 前端: `frontend/src/__tests__/integration/`

## 运行命令

| 范围 | 命令 | cwd |
|------|------|-----|
| 后端全部 | `./mvnw test` | `backend/` |
| 后端单文件 | `./mvnw test -Dtest=XxxTest` | `backend/` |
| 前端全部 | `npx vitest --run` | `frontend/` |
| 前端单文件 | `npx vitest --run src/__tests__/Xxx.test.ts` | `frontend/` |
| Node 服务 | `npm test` | `docxtemplater-service/` |

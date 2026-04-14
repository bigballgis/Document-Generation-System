---
inclusion: auto
name: testing-standards
description: 测试标准，包括 PBT (jqwik/fast-check)、单元测试、集成测试和前端测试规范
---

# 测试标准

## PBT 必须使用场景

往返一致性、不变量验证、幂等性、排序/合并正确性、安全属性、状态机转换

## jqwik 规范

```java
@Property(tries = 100)
void propertyName(@ForAll @From("providerName") Type input) {
    var result = service.method(input);
    assertThat(result).satisfies(r -> ...);
}

@Provide
Arbitrary<Type> providerName() { return Arbitraries.of(...); }
```

- 类名: `XxxPropertyTest.java`，路径: `backend/src/test/java/com/docgen/property/`
- 属性方法描述被验证的属性，Provider 描述生成的数据

## 单元测试

JUnit 5 + Mockito，路径对应源码，每个 Service 覆盖正常/异常/边界

## 集成测试

Testcontainers (PostgreSQL)，路径: `backend/src/test/java/com/docgen/integration/`

## 前端测试

Vitest + @vue/test-utils，路径: `frontend/src/__tests__/`，覆盖渲染/交互/API mock

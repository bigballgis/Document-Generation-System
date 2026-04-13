---
description: 测试标准，包括 PBT (jqwik/fast-check)、单元测试、集成测试和前端测试规范
inclusion: auto
fileMatchPattern: '**/*Test*.java,**/*test*.ts,**/*spec*.ts'
---

# 测试标准

## Property-Based Testing (PBT) 规范

### 何时使用 PBT

以下场景必须使用 PBT：
- 数据转换的往返一致性 (round-trip)
- 不变量验证 (invariants)
- 幂等性验证
- 排序/合并操作的正确性
- 安全属性（加密、隔离、注入防护）
- 状态机转换的合法性

### jqwik 编写规范

```java
@Property(tries = 100)
void propertyName(@ForAll @From("providerName") Type input) {
    // Arrange & Act
    var result = service.method(input);
    // Assert - 验证属性而非具体值
    assertThat(result).satisfies(r -> ...);
}

@Provide
Arbitrary<Type> providerName() {
    return Arbitraries.of(...);
}
```

### 命名规范

- 测试类: `XxxPropertyTest.java`
- 属性方法: 描述被验证的属性，如 `segmentOrderPreservedAfterAssembly`
- Provider 方法: 描述生成的数据，如 `validSegments`

## 单元测试规范

- 使用 JUnit 5 + Mockito
- 测试类路径与源码路径对应
- 每个 Service 类至少覆盖：正常流程、异常流程、边界条件

## 集成测试规范

- 使用 Testcontainers (PostgreSQL)
- 测试文件路径: `backend/src/test/java/com/docgen/integration/`
- 验证完整的请求-响应链路

## 前端测试规范

- 使用 Vitest + @vue/test-utils
- 测试文件路径: `frontend/src/__tests__/`
- 组件测试覆盖：渲染、用户交互、API 调用 mock

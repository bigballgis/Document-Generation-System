package com.docgen.property;

import com.docgen.service.AssemblyEngineService;
import com.docgen.service.ExpressionEngine;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.minio.MinioClient;
import net.jqwik.api.*;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Property-based tests for DataScope isolation (inlined in AssemblyEngineService).
 *
 * <p><b>Validates: Requirements 5.3, 5.4</b></p>
 *
 * <p>Verifies that when a DataScope is configured, the resolved data contains only
 * the mapped subset of the global data context, and no unmapped global variables leak through.</p>
 */
@Tag("Feature: template-segmentation, Property 4: dataScopeIsolation")
class DataScopeIsolationPropertyTest {

    private final AssemblyEngineService service;

    DataScopeIsolationPropertyTest() {
        ExpressionEngine expressionEngine = mock(ExpressionEngine.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        CircuitBreaker cb = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
                .circuitBreaker("test-dsi-cb-" + UUID.randomUUID());
        MinioClient minioClient = mock(MinioClient.class);
        service = new AssemblyEngineService(expressionEngine, restTemplate, cb, minioClient);
    }

    private Map<String, Object> invokeResolveDataScope(
            Map<String, Object> globalData, Map<String, String> dataScope) throws Exception {
        Method method = AssemblyEngineService.class.getDeclaredMethod(
                "resolveDataScope", Map.class, Map.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(service, globalData, dataScope);
        return result;
    }

    /**
     * Property 4: dataScopeIsolation — resolved data contains only mapped keys.
     *
     * For any random global data and DataScope mapping, the resolved data must:
     * 1. Contain exactly the local keys defined in the DataScope
     * 2. Each local key maps to the correct global value
     * 3. No unmapped global keys appear in the resolved data
     */
    @Property(tries = 100)
    void resolvedDataContainsOnlyMappedSubset(
            @ForAll("globalDataMaps") Map<String, Object> globalData,
            @ForAll("dataScopeMappings") Map<String, String> dataScope
    ) throws Exception {
        Assume.that(!dataScope.isEmpty());

        Map<String, Object> result = invokeResolveDataScope(globalData, dataScope);

        // Result must contain exactly the local keys from dataScope
        assertEquals(dataScope.size(), result.size(),
                "Resolved data must have exactly the same number of keys as the DataScope mapping");
        assertEquals(dataScope.keySet(), result.keySet(),
                "Resolved data keys must match DataScope local keys exactly");

        // Each mapped value must match the global data or be null if global key missing
        for (Map.Entry<String, String> mapping : dataScope.entrySet()) {
            String localKey = mapping.getKey();
            String globalKey = mapping.getValue();

            if (globalData.containsKey(globalKey)) {
                assertEquals(globalData.get(globalKey), result.get(localKey),
                        "Local key '" + localKey + "' must map to global value of '" + globalKey + "'");
            } else {
                assertNull(result.get(localKey),
                        "Local key '" + localKey + "' must be null when global key '" + globalKey + "' is missing");
            }
        }

        // No unmapped global keys should leak into the result
        Set<String> globalKeysNotInScope = new java.util.HashSet<>(globalData.keySet());
        globalKeysNotInScope.removeAll(dataScope.values());
        for (String unmappedGlobalKey : globalKeysNotInScope) {
            // The unmapped global key should not appear as a key in the result
            // (unless it happens to coincide with a local key name, which is fine — the value comes from mapping)
            if (!dataScope.containsKey(unmappedGlobalKey)) {
                assertFalse(result.containsKey(unmappedGlobalKey),
                        "Unmapped global key '" + unmappedGlobalKey + "' must not appear in resolved data");
            }
        }
    }

    /**
     * Property 4: dataScopeIsolation — null or empty dataScope returns full global data.
     *
     * When DataScope is null or empty, the full global data context is returned (backward compatible).
     */
    @Property(tries = 100)
    void nullOrEmptyDataScopeReturnsFullGlobalData(
            @ForAll("globalDataMaps") Map<String, Object> globalData,
            @ForAll("nullOrEmptyDataScope") Map<String, String> dataScope
    ) throws Exception {
        Map<String, Object> result = invokeResolveDataScope(globalData, dataScope);

        assertSame(globalData, result,
                "When DataScope is null or empty, the original global data reference must be returned");
    }

    /**
     * Property 4: dataScopeIsolation — missing global keys produce null values.
     *
     * When a DataScope references global keys that don't exist, those local keys
     * must be set to null in the resolved data.
     */
    @Property(tries = 100)
    void missingGlobalKeysProduceNullValues(
            @ForAll("disjointDataAndScope") DisjointDataAndScope input
    ) throws Exception {
        Map<String, Object> result = invokeResolveDataScope(input.globalData, input.dataScope);

        // All local keys should be present but with null values
        assertEquals(input.dataScope.size(), result.size());
        for (String localKey : input.dataScope.keySet()) {
            assertTrue(result.containsKey(localKey),
                    "Local key '" + localKey + "' must be present in resolved data");
            assertNull(result.get(localKey),
                    "Local key '" + localKey + "' must be null when its global key doesn't exist");
        }
    }


    static class DisjointDataAndScope {
        final Map<String, Object> globalData;
        final Map<String, String> dataScope;

        DisjointDataAndScope(Map<String, Object> globalData, Map<String, String> dataScope) {
            this.globalData = globalData;
            this.dataScope = dataScope;
        }

        @Override
        public String toString() {
            return "DisjointDataAndScope{globalData=" + globalData + ", dataScope=" + dataScope + "}";
        }
    }


    @Provide
    Arbitrary<Map<String, Object>> globalDataMaps() {
        Arbitrary<String> keys = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(15);
        Arbitrary<Object> values = Arbitraries.oneOf(
                Arbitraries.strings().alpha().ofMaxLength(20).map(s -> (Object) s),
                Arbitraries.integers().between(-1000, 1000).map(i -> (Object) i),
                Arbitraries.of(true, false).map(b -> (Object) b)
        );
        return Arbitraries.maps(keys, values).ofMinSize(1).ofMaxSize(10);
    }

    @Provide
    Arbitrary<Map<String, String>> dataScopeMappings() {
        Arbitrary<String> localKeys = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10)
                .map(s -> "local_" + s);
        Arbitrary<String> globalKeys = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(15);
        return Arbitraries.maps(localKeys, globalKeys).ofMinSize(1).ofMaxSize(5);
    }

    @Provide
    Arbitrary<Map<String, String>> nullOrEmptyDataScope() {
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.just(new HashMap<>())
        );
    }

    @Provide
    Arbitrary<DisjointDataAndScope> disjointDataAndScope() {
        // Generate global data with keys prefixed "g_" and dataScope referencing keys prefixed "x_"
        // This ensures the global keys in dataScope never exist in globalData
        Arbitrary<Map<String, Object>> globalData = Arbitraries.maps(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(8).map(s -> "g_" + s),
                Arbitraries.strings().alpha().ofMaxLength(10).map(s -> (Object) s)
        ).ofMinSize(1).ofMaxSize(5);

        Arbitrary<Map<String, String>> dataScope = Arbitraries.maps(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(8).map(s -> "local_" + s),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(8).map(s -> "x_" + s)
        ).ofMinSize(1).ofMaxSize(5);

        return Combinators.combine(globalData, dataScope).as(DisjointDataAndScope::new);
    }
}


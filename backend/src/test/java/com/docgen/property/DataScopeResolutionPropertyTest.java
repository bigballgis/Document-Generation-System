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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Property-based test for DataScope resolution logic inlined in AssemblyEngineService.
 *
 * <p><b>Validates: Requirements 3.4, 3.5</b></p>
 *
 * <p>Property 3: DataScope resolution mapping correctness — for any globalData and
 * dataScope mapping, resolved data contains exactly the mapped keys with correct values.</p>
 */
@Tag("Feature: remove-segment-library, Property 3: dataScopeResolutionMappingCorrectness")
class DataScopeResolutionPropertyTest {

    private AssemblyEngineService createService() {
        ExpressionEngine expressionEngine = mock(ExpressionEngine.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        CircuitBreaker cb = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
                .circuitBreaker("test-ds-cb-" + UUID.randomUUID());
        MinioClient minioClient = mock(MinioClient.class);
        return new AssemblyEngineService(expressionEngine, restTemplate, cb, minioClient);
    }

    private Map<String, Object> invokeResolveDataScope(
            AssemblyEngineService service,
            Map<String, Object> globalData,
            Map<String, String> dataScope) throws Exception {
        Method method = AssemblyEngineService.class.getDeclaredMethod(
                "resolveDataScope", Map.class, Map.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(service, globalData, dataScope);
        return result;
    }

    /**
     * Property 3: dataScopeResolutionMappingCorrectness
     *
     * When dataScope is non-null and non-empty, the resolved map SHALL contain
     * exactly the keys defined in dataScope, where each localKey maps to
     * globalData.get(globalKey) or null if globalKey is absent.
     */
    @Property(tries = 100)
    void dataScopeResolutionMappingCorrectness(
            @ForAll("dataScopeInputs") DataScopeInput input
    ) throws Exception {
        AssemblyEngineService service = createService();
        Map<String, Object> resolved = invokeResolveDataScope(service, input.globalData, input.dataScope);

        if (input.dataScope == null || input.dataScope.isEmpty()) {
            // When dataScope is null or empty, full globalData is returned
            assertSame(input.globalData, resolved,
                    "When dataScope is null/empty, resolved data should be the same globalData reference");
        } else {
            // Resolved map should contain exactly the keys from dataScope
            assertEquals(input.dataScope.keySet(), resolved.keySet(),
                    "Resolved data should contain exactly the keys defined in dataScope");

            // Each localKey should map to globalData.get(globalKey) or null
            for (Map.Entry<String, String> mapping : input.dataScope.entrySet()) {
                String localKey = mapping.getKey();
                String globalKey = mapping.getValue();
                Object expected = input.globalData.getOrDefault(globalKey, null);
                assertEquals(expected, resolved.get(localKey),
                        "localKey '" + localKey + "' should map to globalData['" + globalKey + "']");
            }
        }
    }


    static class DataScopeInput {
        final Map<String, Object> globalData;
        final Map<String, String> dataScope;

        DataScopeInput(Map<String, Object> globalData, Map<String, String> dataScope) {
            this.globalData = globalData;
            this.dataScope = dataScope;
        }

        @Override
        public String toString() {
            return "DataScopeInput{globalData=" + globalData + ", dataScope=" + dataScope + "}";
        }
    }


    @Provide
    Arbitrary<DataScopeInput> dataScopeInputs() {
        Arbitrary<Map<String, Object>> globalDataArb = Arbitraries.integers().between(1, 8)
                .flatMap(count -> {
                    return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10)
                            .list().ofSize(count).uniqueElements()
                            .flatMap(keys -> {
                                return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
                                        .list().ofSize(count)
                                        .map(values -> {
                                            Map<String, Object> map = new HashMap<>();
                                            for (int i = 0; i < count; i++) {
                                                map.put(keys.get(i), values.get(i));
                                            }
                                            return map;
                                        });
                            });
                });

        return globalDataArb.flatMap(globalData -> {
            List<String> globalKeys = new ArrayList<>(globalData.keySet());

            // Generate dataScope: some mappings point to existing keys, some to non-existent keys
            Arbitrary<Map<String, String>> dataScopeArb = Arbitraries.oneOf(
                    // Case 1: null dataScope
                    Arbitraries.just((Map<String, String>) null),
                    // Case 2: empty dataScope
                    Arbitraries.just(Collections.<String, String>emptyMap()),
                    // Case 3: non-empty dataScope with mix of existing and non-existing global keys
                    Arbitraries.integers().between(1, 5).flatMap(dsCount -> {
                        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(8)
                                .list().ofSize(dsCount).uniqueElements()
                                .flatMap(localKeys -> {
                                    // For each local key, randomly pick an existing global key or a non-existent one
                                    return Arbitraries.of(true, false).list().ofSize(dsCount)
                                            .flatMap(useExisting -> {
                                                return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10)
                                                        .list().ofSize(dsCount)
                                                        .map(randomKeys -> {
                                                            Map<String, String> ds = new HashMap<>();
                                                            for (int i = 0; i < dsCount; i++) {
                                                                String globalKey;
                                                                if (useExisting.get(i) && !globalKeys.isEmpty()) {
                                                                    globalKey = globalKeys.get(i % globalKeys.size());
                                                                } else {
                                                                    globalKey = "nonexistent_" + randomKeys.get(i);
                                                                }
                                                                ds.put(localKeys.get(i), globalKey);
                                                            }
                                                            return ds;
                                                        });
                                            });
                                });
                    })
            );

            return dataScopeArb.map(ds -> new DataScopeInput(globalData, ds));
        });
    }
}


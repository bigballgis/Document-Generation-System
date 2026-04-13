package com.docgen.service;

import com.docgen.entity.DataSource;
import com.docgen.exception.BusinessException;
import com.docgen.repository.DataSourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataAggregationServiceTest {

    @Mock
    private DataSourceRepository dataSourceRepository;
    @Mock
    private HttpApiDataSourceService httpApiDataSourceService;
    @Mock
    private DatabaseDataSourceService databaseDataSourceService;
    @Mock
    private InternalSystemDataSourceService internalSystemDataSourceService;

    private DataAggregationService service;

    @BeforeEach
    void setUp() {
        service = new DataAggregationService(
                dataSourceRepository,
                httpApiDataSourceService,
                databaseDataSourceService,
                internalSystemDataSourceService);
    }

    // ── aggregateData ──

    @Test
    void aggregateData_noDataSources_returnsEmptyMap() {
        when(dataSourceRepository.findByTemplateIdOrderByPriorityDesc(1L))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = service.aggregateData(1L, Map.of());

        assertTrue(result.isEmpty());
    }

    @Test
    void aggregateData_singleHttpApiSource() {
        DataSource ds = makeDataSource(1L, "api1", "HTTP_API", "{}", 10);
        when(dataSourceRepository.findByTemplateIdOrderByPriorityDesc(1L))
                .thenReturn(List.of(ds));
        when(httpApiDataSourceService.fetchData(eq("{}"), any()))
                .thenReturn(Map.of("name", "Alice", "age", 30));

        Map<String, Object> result = service.aggregateData(1L, Map.of());

        assertEquals("Alice", result.get("name"));
        assertEquals(30, result.get("age"));
    }

    @Test
    void aggregateData_singleDatabaseSource_singleRow() {
        DataSource ds = makeDataSource(2L, "db1", "DATABASE", "{\"query\":\"SELECT 1\"}", 5);
        when(dataSourceRepository.findByTemplateIdOrderByPriorityDesc(1L))
                .thenReturn(List.of(ds));
        when(databaseDataSourceService.fetchData(any(), any()))
                .thenReturn(List.of(Map.of("id", 1, "status", "active")));

        Map<String, Object> result = service.aggregateData(1L, Map.of());

        assertEquals(1, result.get("id"));
        assertEquals("active", result.get("status"));
    }

    @Test
    void aggregateData_singleDatabaseSource_multipleRows() {
        DataSource ds = makeDataSource(2L, "orders", "DATABASE", "{}", 5);
        when(dataSourceRepository.findByTemplateIdOrderByPriorityDesc(1L))
                .thenReturn(List.of(ds));
        List<Map<String, Object>> rows = List.of(
                Map.of("id", 1), Map.of("id", 2));
        when(databaseDataSourceService.fetchData(any(), any())).thenReturn(rows);

        Map<String, Object> result = service.aggregateData(1L, Map.of());

        // Multi-row results stored under data source name
        assertTrue(result.containsKey("orders"));
        assertEquals(rows, result.get("orders"));
    }

    @Test
    void aggregateData_singleInternalSystemSource() {
        DataSource ds = makeDataSource(3L, "sys1", "INTERNAL_SYSTEM", "{}", 1);
        when(dataSourceRepository.findByTemplateIdOrderByPriorityDesc(1L))
                .thenReturn(List.of(ds));
        when(internalSystemDataSourceService.fetchData(any(), any()))
                .thenReturn(Map.of("dept", "Engineering"));

        Map<String, Object> result = service.aggregateData(1L, Map.of());

        assertEquals("Engineering", result.get("dept"));
    }

    @Test
    void aggregateData_multipleSourcesConflictingKeys_higherPriorityWins() {
        // ds1 has priority 10 (higher), ds2 has priority 5 (lower)
        DataSource ds1 = makeDataSource(1L, "primary", "HTTP_API", "{\"url\":\"a\"}", 10);
        DataSource ds2 = makeDataSource(2L, "secondary", "HTTP_API", "{\"url\":\"b\"}", 5);
        when(dataSourceRepository.findByTemplateIdOrderByPriorityDesc(1L))
                .thenReturn(List.of(ds1, ds2)); // ordered by priority desc

        when(httpApiDataSourceService.fetchData(eq("{\"url\":\"a\"}"), any()))
                .thenReturn(Map.of("name", "FromPrimary", "extra", "primary-only"));
        when(httpApiDataSourceService.fetchData(eq("{\"url\":\"b\"}"), any()))
                .thenReturn(Map.of("name", "FromSecondary", "other", "secondary-only"));

        Map<String, Object> result = service.aggregateData(1L, Map.of());

        // Higher priority wins for "name"
        assertEquals("FromPrimary", result.get("name"));
        // Non-conflicting keys from both sources present
        assertEquals("primary-only", result.get("extra"));
        assertEquals("secondary-only", result.get("other"));
    }

    @Test
    void aggregateData_multipleSourcesNoConflict_allFieldsMerged() {
        DataSource ds1 = makeDataSource(1L, "api", "HTTP_API", "{\"url\":\"a\"}", 10);
        DataSource ds2 = makeDataSource(2L, "sys", "INTERNAL_SYSTEM", "{\"url\":\"b\"}", 5);
        when(dataSourceRepository.findByTemplateIdOrderByPriorityDesc(1L))
                .thenReturn(List.of(ds1, ds2));

        when(httpApiDataSourceService.fetchData(any(), any()))
                .thenReturn(Map.of("field1", "val1"));
        when(internalSystemDataSourceService.fetchData(any(), any()))
                .thenReturn(Map.of("field2", "val2"));

        Map<String, Object> result = service.aggregateData(1L, Map.of());

        assertEquals("val1", result.get("field1"));
        assertEquals("val2", result.get("field2"));
    }

    @Test
    void aggregateData_dataSourceFailure_propagatesException() {
        DataSource ds = makeDataSource(1L, "failing", "HTTP_API", "{}", 10);
        when(dataSourceRepository.findByTemplateIdOrderByPriorityDesc(1L))
                .thenReturn(List.of(ds));
        when(httpApiDataSourceService.fetchData(any(), any()))
                .thenThrow(new BusinessException("DATASOURCE_TIMEOUT", "超时",
                        org.springframework.http.HttpStatus.GATEWAY_TIMEOUT));

        assertThrows(BusinessException.class, () -> service.aggregateData(1L, Map.of()));
    }

    @Test
    void aggregateData_passesParametersToDataSources() {
        DataSource ds = makeDataSource(1L, "api", "HTTP_API", "{}", 10);
        when(dataSourceRepository.findByTemplateIdOrderByPriorityDesc(1L))
                .thenReturn(List.of(ds));
        Map<String, Object> params = Map.of("userId", "123");
        when(httpApiDataSourceService.fetchData(any(), eq(params)))
                .thenReturn(Map.of("result", "ok"));

        Map<String, Object> result = service.aggregateData(1L, params);

        assertEquals("ok", result.get("result"));
        verify(httpApiDataSourceService).fetchData(any(), eq(params));
    }

    // ── fetchFromDataSource ──

    @Test
    void fetchFromDataSource_unsupportedType_throwsException() {
        DataSource ds = makeDataSource(1L, "bad", "UNKNOWN_TYPE", "{}", 1);

        assertThrows(BusinessException.class,
                () -> service.fetchFromDataSource(ds, Map.of()));
    }

    // ── mergeResults ──

    @Test
    void mergeResults_emptyList_returnsEmptyMap() {
        Map<String, Object> result = service.mergeResults(Collections.emptyList());
        assertTrue(result.isEmpty());
    }

    @Test
    void mergeResults_priorityOrdering() {
        // results list is ordered by priority desc: [high, low]
        DataSource high = makeDataSource(1L, "high", "HTTP_API", "{}", 10);
        DataSource low = makeDataSource(2L, "low", "HTTP_API", "{}", 1);

        List<DataAggregationService.FetchResult> results = List.of(
                new DataAggregationService.FetchResult(high, Map.of("key", "high-value")),
                new DataAggregationService.FetchResult(low, Map.of("key", "low-value", "only-low", "x"))
        );

        Map<String, Object> merged = service.mergeResults(results);

        assertEquals("high-value", merged.get("key"));
        assertEquals("x", merged.get("only-low"));
    }

    // ── Helper ──

    private DataSource makeDataSource(Long id, String name, String type, String configJson, int priority) {
        DataSource ds = new DataSource();
        ds.setId(id);
        ds.setName(name);
        ds.setType(type);
        ds.setConfigJson(configJson);
        ds.setPriority(priority);
        ds.setTemplateId(1L);
        return ds;
    }
}

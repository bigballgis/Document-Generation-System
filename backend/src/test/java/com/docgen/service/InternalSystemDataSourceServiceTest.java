package com.docgen.service;

import com.docgen.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalSystemDataSourceServiceTest {

    @Mock
    private HttpApiDataSourceService httpApiDataSourceService;

    private InternalSystemDataSourceService service;

    @BeforeEach
    void setUp() {
        service = new InternalSystemDataSourceService(httpApiDataSourceService);
    }

    // ── fetchData ──

    @Test
    void fetchData_delegatesToHttpService() {
        String configJson = "{\"serviceName\":\"user-service\",\"url\":\"http://user-service:8080/api/users\",\"method\":\"GET\"}";
        Map<String, Object> expected = Map.of("id", 1, "name", "Alice");
        when(httpApiDataSourceService.parseConfig(configJson)).thenReturn(
                Map.of("serviceName", "user-service", "url", "http://user-service:8080/api/users", "method", "GET"));
        when(httpApiDataSourceService.fetchData(configJson, Collections.emptyMap())).thenReturn(expected);

        Map<String, Object> result = service.fetchData(configJson, Collections.emptyMap());

        assertEquals(expected, result);
        verify(httpApiDataSourceService).fetchData(configJson, Collections.emptyMap());
    }

    @Test
    void fetchData_withParameters_delegatesToHttpService() {
        String configJson = "{\"serviceName\":\"order-service\",\"url\":\"http://order-service:8080/api/orders\",\"method\":\"POST\"}";
        Map<String, Object> params = Map.of("userId", "123");
        Map<String, Object> expected = Map.of("orderId", 42);
        when(httpApiDataSourceService.parseConfig(configJson)).thenReturn(
                Map.of("serviceName", "order-service"));
        when(httpApiDataSourceService.fetchData(configJson, params)).thenReturn(expected);

        Map<String, Object> result = service.fetchData(configJson, params);

        assertEquals(expected, result);
        verify(httpApiDataSourceService).fetchData(configJson, params);
    }

    @Test
    void fetchData_propagatesException() {
        String configJson = "{\"serviceName\":\"failing-service\",\"url\":\"http://failing:8080/api\",\"method\":\"GET\"}";
        when(httpApiDataSourceService.parseConfig(configJson)).thenReturn(
                Map.of("serviceName", "failing-service"));
        when(httpApiDataSourceService.fetchData(eq(configJson), any()))
                .thenThrow(new BusinessException("DATASOURCE_TIMEOUT", "HTTP API调用超时", HttpStatus.GATEWAY_TIMEOUT));

        assertThrows(BusinessException.class, () -> service.fetchData(configJson, Collections.emptyMap()));
    }

    @Test
    void fetchData_missingServiceName_stillWorks() {
        String configJson = "{\"url\":\"http://some-service:8080/api\",\"method\":\"GET\"}";
        Map<String, Object> expected = Map.of("status", "ok");
        when(httpApiDataSourceService.parseConfig(configJson)).thenReturn(
                Map.of("url", "http://some-service:8080/api"));
        when(httpApiDataSourceService.fetchData(configJson, Collections.emptyMap())).thenReturn(expected);

        Map<String, Object> result = service.fetchData(configJson, Collections.emptyMap());

        assertEquals(expected, result);
    }

    // ── testConnection ──

    @Test
    void testConnection_success() {
        String configJson = "{\"serviceName\":\"user-service\",\"url\":\"http://user-service:8080/api/health\",\"method\":\"GET\"}";
        Map<String, Object> expected = Map.of("success", true, "message", "连接成功，HTTP 200");
        when(httpApiDataSourceService.parseConfig(configJson)).thenReturn(
                Map.of("serviceName", "user-service"));
        when(httpApiDataSourceService.testConnection(configJson)).thenReturn(expected);

        Map<String, Object> result = service.testConnection(configJson);

        assertTrue((Boolean) result.get("success"));
        verify(httpApiDataSourceService).testConnection(configJson);
    }

    @Test
    void testConnection_failure() {
        String configJson = "{\"serviceName\":\"dead-service\",\"url\":\"http://dead-service:8080/api\",\"method\":\"GET\"}";
        Map<String, Object> expected = Map.of("success", false, "message", "连接超时或不可达: timeout");
        when(httpApiDataSourceService.parseConfig(configJson)).thenReturn(
                Map.of("serviceName", "dead-service"));
        when(httpApiDataSourceService.testConnection(configJson)).thenReturn(expected);

        Map<String, Object> result = service.testConnection(configJson);

        assertFalse((Boolean) result.get("success"));
        assertEquals("连接超时或不可达: timeout", result.get("message"));
    }

    @Test
    void testConnection_nullConfig_handlesGracefully() {
        when(httpApiDataSourceService.testConnection(null))
                .thenReturn(Map.of("success", false, "message", "配置为空"));

        Map<String, Object> result = service.testConnection(null);

        assertFalse((Boolean) result.get("success"));
    }
}

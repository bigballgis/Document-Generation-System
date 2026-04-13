package com.docgen.service;

import com.docgen.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HttpApiDataSourceServiceTest {

    @Mock
    private EncryptionService encryptionService;

    private HttpApiDataSourceService service;

    @BeforeEach
    void setUp() {
        service = new HttpApiDataSourceService(encryptionService);
    }

    // ── parseConfig ──

    @Test
    void parseConfig_validJson_returnMap() {
        Map<String, Object> result = service.parseConfig("{\"url\":\"https://api.example.com\",\"method\":\"GET\"}");
        assertEquals("https://api.example.com", result.get("url"));
        assertEquals("GET", result.get("method"));
    }

    @Test
    void parseConfig_invalidJson_throws() {
        assertThrows(BusinessException.class, () -> service.parseConfig("not-json"));
    }

    // ── resolveMethod ──

    @Test
    void resolveMethod_defaultsToGet() {
        assertEquals(HttpMethod.GET, service.resolveMethod(Map.of()));
    }

    @Test
    void resolveMethod_post() {
        assertEquals(HttpMethod.POST, service.resolveMethod(Map.of("method", "POST")));
    }

    @Test
    void resolveMethod_put() {
        assertEquals(HttpMethod.PUT, service.resolveMethod(Map.of("method", "PUT")));
    }

    @Test
    void resolveMethod_caseInsensitive() {
        assertEquals(HttpMethod.POST, service.resolveMethod(Map.of("method", "post")));
    }

    // ── buildUrl ──

    @Test
    void buildUrl_simpleUrl() {
        String url = service.buildUrl(Map.of("url", "https://api.example.com/data", "method", "GET"), Collections.emptyMap());
        assertEquals("https://api.example.com/data", url);
    }

    @Test
    void buildUrl_withConfigParams() {
        Map<String, Object> config = Map.of(
                "url", "https://api.example.com/data",
                "method", "GET",
                "params", Map.of("key", "value")
        );
        String url = service.buildUrl(config, Collections.emptyMap());
        assertEquals("https://api.example.com/data?key=value", url);
    }

    @Test
    void buildUrl_withRuntimeParams() {
        Map<String, Object> config = Map.of("url", "https://api.example.com/data", "method", "GET");
        String url = service.buildUrl(config, Map.of("page", "1"));
        assertEquals("https://api.example.com/data?page=1", url);
    }

    @Test
    void buildUrl_emptyUrl_throws() {
        assertThrows(BusinessException.class, () -> service.buildUrl(Map.of("method", "GET"), Collections.emptyMap()));
    }

    @Test
    void buildUrl_postDoesNotAppendParams() {
        Map<String, Object> config = Map.of(
                "url", "https://api.example.com/data",
                "method", "POST",
                "params", Map.of("key", "value")
        );
        String url = service.buildUrl(config, Collections.emptyMap());
        assertEquals("https://api.example.com/data", url);
    }

    // ── applyAuth ──

    @Test
    void applyAuth_apiKey() {
        when(encryptionService.decrypt("ENC_KEY")).thenReturn("my-api-key");
        HttpHeaders headers = new HttpHeaders();
        service.applyAuth(headers, Map.of("authType", "API_KEY", "apiKey", "ENC_KEY"));
        assertEquals("my-api-key", headers.getFirst("X-API-Key"));
    }

    @Test
    void applyAuth_oauth() {
        when(encryptionService.decrypt("ENC_TOKEN")).thenReturn("bearer-token");
        HttpHeaders headers = new HttpHeaders();
        service.applyAuth(headers, Map.of("authType", "OAUTH", "token", "ENC_TOKEN"));
        assertEquals("Bearer bearer-token", headers.getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void applyAuth_basic() {
        when(encryptionService.decrypt("ENC_PASS")).thenReturn("secret");
        HttpHeaders headers = new HttpHeaders();
        service.applyAuth(headers, Map.of("authType", "BASIC", "username", "user", "password", "ENC_PASS"));
        String authHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);
        assertNotNull(authHeader);
        assertTrue(authHeader.startsWith("Basic "));
    }

    @Test
    void applyAuth_none() {
        HttpHeaders headers = new HttpHeaders();
        service.applyAuth(headers, Map.of("authType", "NONE"));
        assertNull(headers.getFirst(HttpHeaders.AUTHORIZATION));
        assertNull(headers.getFirst("X-API-Key"));
    }

    @Test
    void applyAuth_defaultNone() {
        HttpHeaders headers = new HttpHeaders();
        service.applyAuth(headers, Map.of());
        assertNull(headers.getFirst(HttpHeaders.AUTHORIZATION));
    }

    // ── buildRestTemplate ──

    @Test
    void buildRestTemplate_defaultTimeout() {
        RestTemplate rt = service.buildRestTemplate(Map.of());
        assertNotNull(rt);
    }

    @Test
    void buildRestTemplate_customTimeout() {
        RestTemplate rt = service.buildRestTemplate(Map.of("timeout", 10000));
        assertNotNull(rt);
    }

    // ── buildHttpEntity ──

    @Test
    void buildHttpEntity_getRequest_noBody() {
        HttpEntity<Object> entity = service.buildHttpEntity(
                Map.of("method", "GET"), Collections.emptyMap());
        assertNull(entity.getBody());
    }

    @Test
    void buildHttpEntity_postRequest_withParameters() {
        Map<String, Object> params = Map.of("name", "test");
        HttpEntity<Object> entity = service.buildHttpEntity(
                Map.of("method", "POST"), params);
        assertEquals(params, entity.getBody());
    }

    @Test
    void buildHttpEntity_customHeaders() {
        Map<String, Object> config = Map.of(
                "method", "GET",
                "headers", Map.of("Accept", "application/json")
        );
        HttpEntity<Object> entity = service.buildHttpEntity(config, Collections.emptyMap());
        assertEquals("application/json", entity.getHeaders().getFirst("Accept"));
    }

    // ── testConnection ──

    @Test
    void testConnection_success() {
        String configJson = "{\"url\":\"https://httpbin.org/get\",\"method\":\"GET\",\"timeout\":2000}";
        // The service will try to connect — we test the structure of the result
        Map<String, Object> result = service.testConnection(configJson);
        assertNotNull(result);
        assertTrue(result.containsKey("success"));
        assertTrue(result.containsKey("message"));
    }

    @Test
    void testConnection_invalidUrl_returnsFalse() {
        String configJson = "{\"url\":\"https://this-does-not-exist-12345.invalid/api\",\"method\":\"GET\",\"timeout\":1000}";
        Map<String, Object> result = service.testConnection(configJson);
        assertFalse((Boolean) result.get("success"));
    }

    @Test
    void testConnection_emptyUrl_returnsFalse() {
        String configJson = "{\"method\":\"GET\"}";
        Map<String, Object> result = service.testConnection(configJson);
        assertFalse((Boolean) result.get("success"));
    }

    // ── executeWithRetry ──

    @Test
    void executeWithRetry_successOnFirstAttempt() {
        RestTemplate mockRt = mock(RestTemplate.class);
        ResponseEntity<String> response = new ResponseEntity<>("{\"data\":\"ok\"}", HttpStatus.OK);
        when(mockRt.exchange(any(java.net.URI.class), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(response);

        Map<String, Object> result = service.executeWithRetry(
                mockRt, "https://api.example.com", HttpMethod.GET,
                new HttpEntity<>(null), 0, 1000, false);

        assertEquals("ok", result.get("data"));
        verify(mockRt, times(1)).exchange(any(java.net.URI.class), eq(HttpMethod.GET), any(), eq(String.class));
    }

    @Test
    void executeWithRetry_retriesOnFailure() {
        RestTemplate mockRt = mock(RestTemplate.class);
        when(mockRt.exchange(any(java.net.URI.class), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("timeout"))
                .thenReturn(new ResponseEntity<>("{\"ok\":true}", HttpStatus.OK));

        Map<String, Object> result = service.executeWithRetry(
                mockRt, "https://api.example.com", HttpMethod.GET,
                new HttpEntity<>(null), 1, 10, false);

        assertEquals(true, result.get("ok"));
        verify(mockRt, times(2)).exchange(any(java.net.URI.class), eq(HttpMethod.GET), any(), eq(String.class));
    }

    @Test
    void executeWithRetry_allRetriesExhausted_throws() {
        RestTemplate mockRt = mock(RestTemplate.class);
        when(mockRt.exchange(any(java.net.URI.class), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("timeout"));

        assertThrows(BusinessException.class, () ->
                service.executeWithRetry(
                        mockRt, "https://api.example.com", HttpMethod.GET,
                        new HttpEntity<>(null), 1, 10, false));

        verify(mockRt, times(2)).exchange(any(java.net.URI.class), eq(HttpMethod.GET), any(), eq(String.class));
    }

    @Test
    void executeWithRetry_emptyBody_returnsEmptyMap() {
        RestTemplate mockRt = mock(RestTemplate.class);
        when(mockRt.exchange(any(java.net.URI.class), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("", HttpStatus.OK));

        Map<String, Object> result = service.executeWithRetry(
                mockRt, "https://api.example.com", HttpMethod.GET,
                new HttpEntity<>(null), 0, 1000, false);

        assertTrue(result.isEmpty());
    }

    @Test
    void executeWithRetry_nullBody_returnsEmptyMap() {
        RestTemplate mockRt = mock(RestTemplate.class);
        when(mockRt.exchange(any(java.net.URI.class), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        Map<String, Object> result = service.executeWithRetry(
                mockRt, "https://api.example.com", HttpMethod.GET,
                new HttpEntity<>(null), 0, 1000, false);

        assertTrue(result.isEmpty());
    }
}

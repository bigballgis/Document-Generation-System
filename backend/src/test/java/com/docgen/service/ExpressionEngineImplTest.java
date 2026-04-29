package com.docgen.service;

import com.docgen.dto.ExpressionValidationResult;
import com.docgen.entity.ExpressionType;
import com.docgen.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpressionEngineImplTest {

    @Mock
    private RestTemplate restTemplate;

    private ExpressionEngineImpl engine;

    private static final String SERVICE_URL = "http://localhost:3000";

    @BeforeEach
    void setUp() {
        reset(restTemplate);
        engine = new ExpressionEngineImpl(restTemplate, SERVICE_URL);
    }


    @Test
    void evaluate_javascript_success() {
        Map<String, Object> responseBody = Map.of("success", true, "result", 42);
        when(restTemplate.exchange(
                eq(SERVICE_URL + "/evaluate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        Object result = engine.evaluate("data.price * data.quantity", ExpressionType.JAVASCRIPT,
                Map.of("price", 6, "quantity", 7));

        assertEquals(42, result);

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq(SERVICE_URL + "/evaluate"),
                eq(HttpMethod.POST),
                captor.capture(),
                eq(Map.class));
        assertEquals("javascript", captor.getValue().getBody().get("type"));
    }

    @Test
    void evaluate_excelFormula_success() {
        Map<String, Object> responseBody = Map.of("success", true, "result", 15.0);
        when(restTemplate.exchange(
                eq(SERVICE_URL + "/evaluate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        Object result = engine.evaluate("SUM(1,2,3,4,5)", ExpressionType.EXCEL_FORMULA, Map.of());

        assertEquals(15.0, result);

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq(SERVICE_URL + "/evaluate"),
                eq(HttpMethod.POST),
                captor.capture(),
                eq(Map.class));
        assertEquals("excel", captor.getValue().getBody().get("type"));
    }

    @Test
    void evaluate_serviceReturnsFailure_throwsBusinessException() {
        Map<String, Object> responseBody = Map.of("success", false, "error", "ReferenceError: x is not defined");
        when(restTemplate.exchange(
                eq(SERVICE_URL + "/evaluate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> engine.evaluate("x + 1", ExpressionType.JAVASCRIPT, Map.of()));

        assertTrue(ex.getMessage().contains("ReferenceError"));
    }

    @Test
    void evaluate_serviceReturnsFailureWithStructuredError_includesCodeAndMessage() {
        Map<String, Object> errorObj = Map.of(
                "code", "UNKNOWN_EXPRESSION_TYPE",
                "message", "type must be \"javascript\" or \"excel\"");
        Map<String, Object> responseBody = Map.of("success", false, "error", errorObj);
        when(restTemplate.exchange(
                eq(SERVICE_URL + "/evaluate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> engine.evaluate("1+1", ExpressionType.JAVASCRIPT, Map.of()));

        assertTrue(ex.getMessage().contains("UNKNOWN_EXPRESSION_TYPE"));
        assertTrue(ex.getMessage().contains("javascript"));
    }

    @Test
    void evaluate_serviceReturnsNull_throwsBusinessException() {
        when(restTemplate.exchange(
                eq(SERVICE_URL + "/evaluate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        assertThrows(BusinessException.class,
                () -> engine.evaluate("1+1", ExpressionType.JAVASCRIPT, Map.of()));
    }

    @Test
    void evaluate_serviceUnavailable_throwsBusinessException() {
        when(restTemplate.exchange(
                eq(SERVICE_URL + "/evaluate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)))
                .thenThrow(new RestClientException("Connection refused"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> engine.evaluate("1+1", ExpressionType.JAVASCRIPT, Map.of()));

        assertTrue(ex.getMessage().contains("Connection refused"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getHttpStatus());
    }

    @Test
    void evaluate_nullContext_usesEmptyMap() {
        Map<String, Object> responseBody = Map.of("success", true, "result", 2);
        when(restTemplate.exchange(
                eq(SERVICE_URL + "/evaluate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        Object result = engine.evaluate("1+1", ExpressionType.JAVASCRIPT, null);

        assertEquals(2, result);
    }


    @Test
    void evaluateAll_success() {
        // First expression
        Map<String, Object> response1 = Map.of("success", true, "result", 100);
        // Second expression (uses result of first)
        Map<String, Object> response2 = Map.of("success", true, "result", 90);

        when(restTemplate.exchange(
                eq(SERVICE_URL + "/evaluate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)))
                .thenReturn(new ResponseEntity<>(response1, HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(response2, HttpStatus.OK));

        List<ExpressionEngine.ExpressionConfig> configs = List.of(
                new ExpressionEngine.ExpressionConfig("subtotal", "data.price * data.qty", ExpressionType.JAVASCRIPT),
                new ExpressionEngine.ExpressionConfig("total", "subtotal - data.discount", ExpressionType.JAVASCRIPT)
        );

        Map<String, Object> results = engine.evaluateAll(configs, Map.of("price", 10, "qty", 10, "discount", 10));

        assertEquals(2, results.size());
        assertEquals(100, results.get("subtotal"));
        assertEquals(90, results.get("total"));
    }

    @Test
    void evaluateAll_failureInMiddle_throws() {
        Map<String, Object> response1 = Map.of("success", true, "result", 100);
        Map<String, Object> response2 = Map.of("success", false, "error", "Division by zero");

        when(restTemplate.exchange(
                eq(SERVICE_URL + "/evaluate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)))
                .thenReturn(new ResponseEntity<>(response1, HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(response2, HttpStatus.OK));

        List<ExpressionEngine.ExpressionConfig> configs = List.of(
                new ExpressionEngine.ExpressionConfig("a", "1+1", ExpressionType.JAVASCRIPT),
                new ExpressionEngine.ExpressionConfig("b", "1/0", ExpressionType.JAVASCRIPT)
        );

        assertThrows(BusinessException.class, () -> engine.evaluateAll(configs, Map.of()));
    }


    @Test
    void validateExpression_valid() {
        Map<String, Object> responseBody = Map.of("success", true, "result", 0);
        when(restTemplate.exchange(
                eq(SERVICE_URL + "/evaluate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        ExpressionValidationResult result = engine.validateExpression("1 + 2", ExpressionType.JAVASCRIPT);

        assertTrue(result.isValid());
        assertNull(result.getErrorMessage());

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq(SERVICE_URL + "/evaluate"),
                eq(HttpMethod.POST),
                captor.capture(),
                eq(Map.class));
        assertEquals("javascript", captor.getValue().getBody().get("type"));
    }

    @Test
    void validateExpression_invalid() {
        Map<String, Object> responseBody = Map.of("success", false, "error", "Unexpected token", "errorPosition", 5);
        when(restTemplate.exchange(
                eq(SERVICE_URL + "/evaluate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        ExpressionValidationResult result = engine.validateExpression("1 +* 2", ExpressionType.JAVASCRIPT);

        assertFalse(result.isValid());
        assertEquals("Unexpected token", result.getErrorMessage());
        assertEquals(5, result.getErrorPosition());
    }

    @Test
    void validateExpression_serviceUnavailable() {
        when(restTemplate.exchange(
                eq(SERVICE_URL + "/evaluate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)))
                .thenThrow(new RestClientException("Connection refused"));

        ExpressionValidationResult result = engine.validateExpression("1+1", ExpressionType.JAVASCRIPT);

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("表达式服务不可用"));
    }

    @Test
    void validateExpression_nullBody() {
        when(restTemplate.exchange(
                eq(SERVICE_URL + "/evaluate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        ExpressionValidationResult result = engine.validateExpression("1+1", ExpressionType.JAVASCRIPT);

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("验证服务返回空结果"));
    }
}


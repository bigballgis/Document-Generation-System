package com.docgen.exception;

import com.docgen.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleBusinessException_returnsCorrectStatusAndBody() {
        BusinessException ex = new BusinessException(
                ErrorCode.TEMPLATE_NOT_FOUND, "Template not found", HttpStatus.NOT_FOUND);

        ResponseEntity<ErrorResponse> response = handler.handleBusiness(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.TEMPLATE_NOT_FOUND, response.getBody().getError().code());
        assertEquals("Template not found", response.getBody().getError().message());
        assertNotNull(response.getBody().getError().traceId());
        assertNotNull(response.getBody().getError().timestamp());
    }

    @Test
    void handleResourceNotFoundException_returns404() {
        ResourceNotFoundException ex = new ResourceNotFoundException(
                ErrorCode.TEMPLATE_NOT_FOUND, "Template not found");

        ResponseEntity<ErrorResponse> response = handler.handleBusiness(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(ErrorCode.TEMPLATE_NOT_FOUND, response.getBody().getError().code());
    }

    @Test
    void handleAccessDeniedException_returns403() {
        AccessDeniedException ex = new AccessDeniedException("无权操作此模板");

        ResponseEntity<ErrorResponse> response = handler.handleBusiness(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(ErrorCode.AUTH_ACCESS_DENIED, response.getBody().getError().code());
    }

    @Test
    void handleSpringAccessDenied_returns403() {
        org.springframework.security.access.AccessDeniedException ex =
                new org.springframework.security.access.AccessDeniedException("Forbidden");

        ResponseEntity<ErrorResponse> response = handler.handleSpringAccessDenied(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(ErrorCode.AUTH_ACCESS_DENIED, response.getBody().getError().code());
        assertEquals("Access denied", response.getBody().getError().message());
    }

    @Test
    void handleRateLimitExceeded_returns429WithHeaders() {
        RateLimitExceededException ex = new RateLimitExceededException("请求过于频繁", 30);

        ResponseEntity<ErrorResponse> response = handler.handleRateLimit(ex);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals(ErrorCode.RATE_LIMIT_EXCEEDED, response.getBody().getError().code());
        assertEquals("30", response.getHeaders().getFirst("Retry-After"));
        assertEquals("0", response.getHeaders().getFirst("X-RateLimit-Remaining"));
    }

    @Test
    void handleValidationException_returns400WithDetails() {
        Map<String, Object> details = Map.of("name", "名称不能为空");
        ValidationException ex = new ValidationException("参数验证失败", details);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ErrorCode.VALIDATION_FAILED, response.getBody().getError().code());
        assertNotNull(response.getBody().getError().details());
        assertEquals("名称不能为空", response.getBody().getError().details().get("name"));
    }

    @Test
    void handleMethodArgumentNotValid_returns400WithFieldErrors() {
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "email", "Invalid email format"));

        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleMethodArgumentNotValid(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ErrorCode.VALIDATION_FAILED, response.getBody().getError().code());
        assertEquals("Request validation failed", response.getBody().getError().message());
        assertEquals("Invalid email format", response.getBody().getError().details().get("email"));
    }

    @Test
    void handleGenericException_returns500() {
        Exception ex = new RuntimeException("unexpected");

        ResponseEntity<ErrorResponse> response = handler.handleGeneric(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(ErrorCode.INTERNAL_ERROR, response.getBody().getError().code());
        assertEquals("Internal server error", response.getBody().getError().message());
    }

    @Test
    void traceId_isUniquePerCall() {
        BusinessException ex = new BusinessException(
                ErrorCode.GENERATE_FAILED, "Generation failed", HttpStatus.INTERNAL_SERVER_ERROR);

        String traceId1 = handler.handleBusiness(ex).getBody().getError().traceId();
        String traceId2 = handler.handleBusiness(ex).getBody().getError().traceId();

        assertNotEquals(traceId1, traceId2);
    }
}

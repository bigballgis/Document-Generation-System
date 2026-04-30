package com.docgen.exception;

import com.docgen.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Centralised exception handler that translates exceptions into
 * the standardized {@link ErrorResponse} envelope.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);


    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
        String traceId = generateTraceId();
        log.warn("Validation error [traceId={}]: {}", traceId, ex.getMessage());
        ErrorResponse response = new ErrorResponse(
                ex.getErrorCode(), ex.getMessage(), traceId, ex.getDetails());
        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException ex) {
        String traceId = generateTraceId();
        log.warn("Rate limit exceeded [traceId={}]: {}", traceId, ex.getMessage());
        ErrorResponse response = new ErrorResponse(
                ex.getErrorCode(), ex.getMessage(), traceId);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .header("X-RateLimit-Remaining", "0")
                .body(response);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        String traceId = generateTraceId();
        log.warn("Business error [traceId={}]: {} - {}", traceId, ex.getErrorCode(), ex.getMessage());
        ErrorResponse response = new ErrorResponse(
                ex.getErrorCode(), ex.getMessage(), traceId);
        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }


    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleSpringAccessDenied(
            org.springframework.security.access.AccessDeniedException ex) {
        String traceId = generateTraceId();
        log.warn("Access denied [traceId={}]: {}", traceId, ex.getMessage());
        ErrorResponse response = new ErrorResponse(
                ErrorCode.AUTH_ACCESS_DENIED, "Access denied", traceId);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }


    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String traceId = generateTraceId();
        String message = String.format("Invalid value '%s' for parameter '%s'", ex.getValue(), ex.getName());
        log.warn("Type mismatch [traceId={}]: {}", traceId, message);
        ErrorResponse response = new ErrorResponse(
                ErrorCode.VALIDATION_TYPE_MISMATCH, message, traceId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {
        String traceId = generateTraceId();
        Map<String, Object> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        log.warn("Validation error [traceId={}]: {}", traceId, fieldErrors);
        ErrorResponse response = new ErrorResponse(
                ErrorCode.VALIDATION_FAILED, "Request validation failed", traceId, fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        String traceId = generateTraceId();
        log.error("Unexpected error [traceId={}]", traceId, ex);
        ErrorResponse response = new ErrorResponse(
                ErrorCode.INTERNAL_ERROR, "Internal server error", traceId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString();
    }
}


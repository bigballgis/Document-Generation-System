package com.docgen.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Standardized error response DTO.
 * Wraps error details in an "error" envelope per the API contract.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private ErrorBody error;

    public ErrorResponse() {
    }

    public ErrorResponse(String code, String message) {
        this.error = new ErrorBody(code, message, Instant.now().toString(), null, null);
    }

    public ErrorResponse(String code, String message, String traceId) {
        this.error = new ErrorBody(code, message, Instant.now().toString(), traceId, null);
    }

    public ErrorResponse(String code, String message, String traceId, Map<String, Object> details) {
        this.error = new ErrorBody(code, message, Instant.now().toString(), traceId, details);
    }

    public ErrorBody getError() {
        return error;
    }

    public void setError(ErrorBody error) {
        this.error = error;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorBody(
            String code,
            String message,
            String timestamp,
            String traceId,
            Map<String, Object> details
    ) {
    }
}

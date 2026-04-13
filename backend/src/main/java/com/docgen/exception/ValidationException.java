package com.docgen.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Thrown when input validation fails. Maps to HTTP 400.
 * Carries a details map describing individual field errors.
 */
public class ValidationException extends BusinessException {

    private final Map<String, Object> details;

    public ValidationException(String message, Map<String, Object> details) {
        super(ErrorCode.VALIDATION_FAILED, message, HttpStatus.BAD_REQUEST);
        this.details = details;
    }

    public ValidationException(String errorCode, String message, Map<String, Object> details) {
        super(errorCode, message, HttpStatus.BAD_REQUEST);
        this.details = details;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}

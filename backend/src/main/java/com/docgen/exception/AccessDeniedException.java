package com.docgen.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when the current user lacks permission for the requested operation.
 * Maps to HTTP 403.
 */
public class AccessDeniedException extends BusinessException {

    public AccessDeniedException(String message) {
        super(ErrorCode.AUTH_ACCESS_DENIED, message, HttpStatus.FORBIDDEN);
    }

    public AccessDeniedException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.FORBIDDEN);
    }
}

package com.docgen.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an API call exceeds the configured rate limit.
 * Maps to HTTP 429 with Retry-After header.
 */
public class RateLimitExceededException extends BusinessException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(ErrorCode.RATE_LIMIT_EXCEEDED, message, HttpStatus.TOO_MANY_REQUESTS);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public RateLimitExceededException(String errorCode, String message, long retryAfterSeconds) {
        super(errorCode, message, HttpStatus.TOO_MANY_REQUESTS);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}

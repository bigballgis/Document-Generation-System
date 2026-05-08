package com.docgen.security.url;

/**
 * Result of validating an outbound URL against the SSRF mitigation policy.
 */
public record UrlValidationResult(boolean allowed, String reasonCode, String message) {

    public static UrlValidationResult ok() {
        return new UrlValidationResult(true, null, null);
    }

    public static UrlValidationResult rejected(String reasonCode, String message) {
        return new UrlValidationResult(false, reasonCode, message);
    }
}

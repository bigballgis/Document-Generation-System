package com.docgen.dto;

import java.time.Instant;

/**
 * Carries validated API Key metadata extracted from the api_keys table.
 * Used by {@link com.docgen.filter.ApiKeyAuthenticationFilter} to build a
 * {@link UserPrincipal} and populate the SecurityContext.
 */
public record ApiKeyInfo(
        Long id,
        Long tenantId,
        Long userId,
        String role,
        Long teamId,
        int rateLimitPerSecond,
        int rateLimitPerMinute,
        int rateLimitPerHour,
        boolean enabled,
        Instant expiresAt
) {}

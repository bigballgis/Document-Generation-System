package com.docgen.filter;

import com.docgen.dto.ApiKeyInfo;
import com.docgen.dto.UserPrincipal;
import com.docgen.exception.RateLimitExceededException;
import com.docgen.service.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;

/**
 * Rate limiting filter that enforces per-API-Key frequency limits
 * and per-tenant monthly quotas.
 * <p>
 * Placed before {@link ApiKeyAuthenticationFilter} in the security filter chain.
 * For API Key requests, it extracts the key from the {@code X-API-Key} header,
 * looks up the rate limit config, and delegates to {@link RateLimitService}.
 * <p>
 * For JWT-authenticated requests (no API Key header), rate limiting is skipped
 * since those are typically interactive user sessions.
 * <p>
 * On rate limit exceeded, returns HTTP 429 with {@code Retry-After} and
 * {@code X-RateLimit-Remaining} headers.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final String API_KEY_HEADER = "X-API-Key";

    private final RateLimitService rateLimitService;
    private final com.docgen.service.ApiKeyValidationService apiKeyValidationService;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitService rateLimitService,
                           com.docgen.service.ApiKeyValidationService apiKeyValidationService,
                           ObjectMapper objectMapper) {
        this.rateLimitService = rateLimitService;
        this.apiKeyValidationService = apiKeyValidationService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String rawApiKey = request.getHeader(API_KEY_HEADER);

        if (rawApiKey != null && !rawApiKey.isBlank()) {
            // API Key request — apply rate limiting
            String keyHash = sha256Hex(rawApiKey);
            var infoOpt = apiKeyValidationService.validateApiKey(keyHash);

            if (infoOpt.isPresent()) {
                ApiKeyInfo info = infoOpt.get();
                try {
                    // Check per-key frequency limits
                    rateLimitService.checkRateLimit(
                            info.id(),
                            info.rateLimitPerSecond(),
                            info.rateLimitPerMinute(),
                            info.rateLimitPerHour());

                    // Check tenant monthly quota
                    rateLimitService.checkMonthlyQuota(info.tenantId());

                    // Add remaining count header
                    long remaining = rateLimitService.getMinRemaining(
                            info.id(),
                            info.rateLimitPerSecond(),
                            info.rateLimitPerMinute(),
                            info.rateLimitPerHour());
                    response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));

                } catch (RateLimitExceededException ex) {
                    writeRateLimitResponse(response, ex);
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Skip rate limiting for public endpoints
        return path.startsWith("/api/auth/")
                || path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui/")
                || path.startsWith("/api-docs/")
                || path.startsWith("/v3/api-docs/");
    }

    private void writeRateLimitResponse(HttpServletResponse response,
                                         RateLimitExceededException ex) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Retry-After", String.valueOf(ex.getRetryAfterSeconds()));
        response.setHeader("X-RateLimit-Remaining", "0");

        Map<String, Object> body = Map.of(
                "error", Map.of(
                        "code", "RATE_LIMIT_EXCEEDED",
                        "message", ex.getMessage(),
                        "timestamp", Instant.now().toString()
                )
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}

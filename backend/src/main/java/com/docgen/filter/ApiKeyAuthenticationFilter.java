package com.docgen.filter;

import com.docgen.dto.ApiKeyInfo;
import com.docgen.dto.UserPrincipal;
import com.docgen.service.ApiKeyValidationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Extracts the API key from the {@code X-API-Key} request header, hashes it
 * with SHA-256, and validates it against the {@code api_keys} table via
 * {@link ApiKeyValidationService}.
 * <p>
 * If the key is valid (hash matches, enabled, not expired) a
 * {@link UserPrincipal} is created and placed into the
 * {@link SecurityContextHolder}. If the key is missing or invalid the filter
 * simply continues the chain so that other authentication mechanisms (JWT,
 * OAuth2) can try.
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final ApiKeyValidationService apiKeyValidationService;

    public ApiKeyAuthenticationFilter(ApiKeyValidationService apiKeyValidationService) {
        this.apiKeyValidationService = apiKeyValidationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Only attempt API-key auth when no authentication is already set
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String rawKey = request.getHeader(API_KEY_HEADER);

            if (StringUtils.hasText(rawKey)) {
                String keyHash = sha256Hex(rawKey);

                apiKeyValidationService.validateApiKey(keyHash).ifPresent(info -> {
                    UserPrincipal principal = new UserPrincipal(
                            info.userId(),
                            info.tenantId(),
                            info.role(),
                            info.teamId(),
                            String.valueOf(info.userId()));

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    principal, null, principal.getAuthorities());
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Compute the hex-encoded SHA-256 digest of the given input.
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available on every JVM
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

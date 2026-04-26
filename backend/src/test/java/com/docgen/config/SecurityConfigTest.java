package com.docgen.config;

import com.docgen.filter.ApiKeyAuthenticationFilter;
import com.docgen.filter.JwtAuthenticationFilter;
import com.docgen.filter.OnlyOfficeCallbackIpFilter;
import com.docgen.filter.RateLimitFilter;
import com.docgen.filter.TenantIsolationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link SecurityConfig}.
 */
class SecurityConfigTest {

    private final JwtAuthenticationFilter jwtFilter = mock(JwtAuthenticationFilter.class);
    private final ApiKeyAuthenticationFilter apiKeyFilter = mock(ApiKeyAuthenticationFilter.class);
    private final TenantIsolationFilter tenantFilter = mock(TenantIsolationFilter.class);
    private final RateLimitFilter rateLimitFilter = mock(RateLimitFilter.class);
    private final OnlyOfficeCallbackIpFilter onlyOfficeCallbackIpFilter = mock(OnlyOfficeCallbackIpFilter.class);

    private final SecurityConfig config = new SecurityConfig(
            jwtFilter, apiKeyFilter, tenantFilter, rateLimitFilter, onlyOfficeCallbackIpFilter);

    @Test
    void passwordEncoderReturnsBCrypt() {
        PasswordEncoder encoder = config.passwordEncoder();
        assertInstanceOf(BCryptPasswordEncoder.class, encoder);
    }

    @Test
    void passwordEncoderHashesAndVerifies() {
        PasswordEncoder encoder = config.passwordEncoder();
        String raw = "S3cureP@ss!";
        String hash = encoder.encode(raw);

        assertNotEquals(raw, hash);
        assertTrue(encoder.matches(raw, hash));
        assertFalse(encoder.matches("wrong", hash));
    }

    @Test
    void corsConfigurationAllowsExpectedSettings() {
        CorsConfigurationSource source = config.corsConfigurationSource();
        CorsConfiguration cors = source.getCorsConfiguration(
                new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/templates"));

        assertNotNull(cors);
        assertEquals(1, cors.getAllowedOriginPatterns().size());
        assertEquals("*", cors.getAllowedOriginPatterns().get(0));
        assertTrue(cors.getAllowedMethods().containsAll(
                java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")));
        assertEquals(java.util.List.of("*"), cors.getAllowedHeaders());
        assertTrue(cors.getAllowCredentials());
        assertEquals(3600L, cors.getMaxAge());
    }
}

package com.docgen.filter;

import com.docgen.dto.ApiKeyInfo;
import com.docgen.exception.RateLimitExceededException;
import com.docgen.service.ApiKeyValidationService;
import com.docgen.service.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private ApiKeyValidationService apiKeyValidationService;

    @Mock
    private FilterChain filterChain;

    private RateLimitFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        filter = new RateLimitFilter(rateLimitService, apiKeyValidationService, objectMapper);
    }

    @Test
    void noApiKeyHeader_passesThrough() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/templates");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void validApiKey_withinLimits_passesThrough() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/templates");
        request.addHeader("X-API-Key", "test-key-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ApiKeyInfo info = new ApiKeyInfo(1L, 10L, 42L, "USER", null, 10, 100, 1000, true, null);
        when(apiKeyValidationService.validateApiKey(anyString())).thenReturn(Optional.of(info));
        when(rateLimitService.getMinRemaining(eq(1L), eq(10), eq(100), eq(1000))).thenReturn(99L);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(rateLimitService).checkRateLimit(1L, 10, 100, 1000);
        verify(rateLimitService).checkMonthlyQuota(10L);
        assertEquals("99", response.getHeader("X-RateLimit-Remaining"));
    }

    @Test
    void validApiKey_rateLimitExceeded_returns429() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/templates");
        request.addHeader("X-API-Key", "test-key-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ApiKeyInfo info = new ApiKeyInfo(1L, 10L, 42L, "USER", null, 10, 100, 1000, true, null);
        when(apiKeyValidationService.validateApiKey(anyString())).thenReturn(Optional.of(info));
        doThrow(new RateLimitExceededException("Rate limit exceeded: 10 requests per second", 1L))
                .when(rateLimitService).checkRateLimit(1L, 10, 100, 1000);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertEquals(429, response.getStatus());
        assertEquals("1", response.getHeader("Retry-After"));
        assertEquals("0", response.getHeader("X-RateLimit-Remaining"));
        assertTrue(response.getContentAsString().contains("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void validApiKey_quotaExhausted_returns429() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/templates");
        request.addHeader("X-API-Key", "test-key-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ApiKeyInfo info = new ApiKeyInfo(1L, 10L, 42L, "USER", null, 10, 100, 1000, true, null);
        when(apiKeyValidationService.validateApiKey(anyString())).thenReturn(Optional.of(info));
        doThrow(new RateLimitExceededException("RATE_LIMIT_QUOTA_EXHAUSTED",
                "Monthly API quota exhausted. Quota: 1000", 3600L))
                .when(rateLimitService).checkMonthlyQuota(10L);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertEquals(429, response.getStatus());
        assertEquals("3600", response.getHeader("Retry-After"));
    }

    @Test
    void invalidApiKey_passesThrough() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/templates");
        request.addHeader("X-API-Key", "invalid-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(apiKeyValidationService.validateApiKey(anyString())).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(rateLimitService, never()).checkRateLimit(anyLong(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void shouldNotFilter_publicEndpoints() {
        assertTrue(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/auth/login")));
        assertTrue(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/actuator/health")));
        assertTrue(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/swagger-ui/index.html")));
        assertTrue(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/v3/api-docs/swagger-config")));
        assertFalse(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/templates")));
    }
}

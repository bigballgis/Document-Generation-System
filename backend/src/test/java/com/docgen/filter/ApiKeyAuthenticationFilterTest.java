package com.docgen.filter;

import com.docgen.dto.ApiKeyInfo;
import com.docgen.dto.UserPrincipal;
import com.docgen.service.ApiKeyValidationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ApiKeyAuthenticationFilterTest {

    private ApiKeyValidationService validationService;
    private ApiKeyAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        validationService = mock(ApiKeyValidationService.class);
        filter = new ApiKeyAuthenticationFilter(validationService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validApiKey_setsSecurityContext() throws ServletException, IOException {
        String rawKey = "my-secret-api-key";
        String expectedHash = ApiKeyAuthenticationFilter.sha256Hex(rawKey);

        ApiKeyInfo info = new ApiKeyInfo(
                1L, 10L, 42L, "USER", 5L,
                10, 100, 1000, true,
                Instant.now().plusSeconds(3600));

        when(validationService.validateApiKey(expectedHash)).thenReturn(Optional.of(info));

        request.addHeader("X-API-Key", rawKey);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertInstanceOf(UserPrincipal.class, auth.getPrincipal());

        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        assertEquals(42L, principal.getUserId());
        assertEquals(10L, principal.getTenantId());
        assertEquals("USER", principal.getRole());
        assertEquals(5L, principal.getTeamId());
    }

    @Test
    void missingHeader_continuesChainWithoutAuth() throws ServletException, IOException {
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(validationService);
    }

    @Test
    void emptyHeader_continuesChainWithoutAuth() throws ServletException, IOException {
        request.addHeader("X-API-Key", "");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(validationService);
    }

    @Test
    void invalidApiKey_continuesChainWithoutAuth() throws ServletException, IOException {
        request.addHeader("X-API-Key", "bad-key");
        when(validationService.validateApiKey(anyString())).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void existingAuthentication_skipsApiKeyCheck() throws ServletException, IOException {
        // Pre-set an authentication (e.g. from JWT filter)
        UserPrincipal existing = new UserPrincipal(99L, 1L, "ADMIN", 2L, "99");
        var existingAuth = new org.springframework.security.authentication
                .UsernamePasswordAuthenticationToken(existing, null, existing.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(existingAuth);

        request.addHeader("X-API-Key", "some-key");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(validationService);
        // Original auth should remain
        assertSame(existingAuth, SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void sha256Hex_producesCorrectHash() {
        // Known SHA-256 of empty string
        String hash = ApiKeyAuthenticationFilter.sha256Hex("");
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash);
    }
}

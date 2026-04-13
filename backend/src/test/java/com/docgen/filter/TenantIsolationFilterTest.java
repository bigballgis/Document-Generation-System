package com.docgen.filter;

import com.docgen.dto.UserPrincipal;
import com.docgen.util.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantIsolationFilterTest {

    private TenantIsolationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new TenantIsolationFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void authenticatedUser_setsTenantContext() throws ServletException, IOException {
        UserPrincipal principal = new UserPrincipal(1L, 42L, "USER", 5L, "user1");
        setAuthentication(principal);

        AtomicReference<Long> capturedTenantId = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedTenantId.set(TenantContext.getCurrentTenantId());
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(42L, capturedTenantId.get());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void noAuthentication_tenantContextRemainsNull() throws ServletException, IOException {
        AtomicReference<Long> capturedTenantId = new AtomicReference<>(999L);
        doAnswer(invocation -> {
            capturedTenantId.set(TenantContext.getCurrentTenantId());
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(capturedTenantId.get());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void nonUserPrincipal_tenantContextRemainsNull() throws ServletException, IOException {
        // Authentication with a plain String principal (not UserPrincipal)
        var auth = new UsernamePasswordAuthenticationToken("anonymous", null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        AtomicReference<Long> capturedTenantId = new AtomicReference<>(999L);
        doAnswer(invocation -> {
            capturedTenantId.set(TenantContext.getCurrentTenantId());
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(capturedTenantId.get());
    }

    @Test
    void tenantContext_clearedAfterFilterChain() throws ServletException, IOException {
        UserPrincipal principal = new UserPrincipal(1L, 42L, "USER", 5L, "user1");
        setAuthentication(principal);

        filter.doFilterInternal(request, response, filterChain);

        // After the filter completes, TenantContext must be cleared
        assertNull(TenantContext.getCurrentTenantId());
    }

    @Test
    void tenantContext_clearedEvenWhenFilterChainThrows() throws ServletException, IOException {
        UserPrincipal principal = new UserPrincipal(1L, 42L, "USER", 5L, "user1");
        setAuthentication(principal);

        doThrow(new ServletException("boom")).when(filterChain).doFilter(request, response);

        assertThrows(ServletException.class,
                () -> filter.doFilterInternal(request, response, filterChain));

        // Must still be cleared
        assertNull(TenantContext.getCurrentTenantId());
    }

    private void setAuthentication(UserPrincipal principal) {
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}

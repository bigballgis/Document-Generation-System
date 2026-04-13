package com.docgen.filter;

import com.docgen.dto.UserPrincipal;
import com.docgen.util.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Extracts the tenant ID from the authenticated {@link UserPrincipal} and
 * stores it in {@link TenantContext} so that downstream components
 * (e.g. Hibernate tenant filter) can enforce data isolation.
 * <p>
 * The {@code TenantContext} is always cleared in the {@code finally} block
 * to prevent tenant ID leakage across pooled threads.
 */
@Component
public class TenantIsolationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
                TenantContext.setCurrentTenantId(principal.getTenantId());
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}

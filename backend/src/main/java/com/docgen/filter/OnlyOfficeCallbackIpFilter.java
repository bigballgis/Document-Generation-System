package com.docgen.filter;

import com.docgen.config.OnlyOfficeCallbackProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional ingress restriction for OnlyOffice callback POSTs using {@link IpAddressMatcher}
 * against {@link HttpServletRequest#getRemoteAddr()}.
 *
 * <p>Place reverse proxies so that {@code getRemoteAddr()} reflects the Document Server hop, or
 * enable Spring forwarded-header handling ({@code server.forward-headers-strategy}) when trusted.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OnlyOfficeCallbackIpFilter extends OncePerRequestFilter {

    private static final String TEMPLATE_CALLBACK = "/api/templates/";
    private static final String COMPOSITE_SEGMENT = "/segments/";
    private static final String CALLBACK_SUFFIX = "/onlyoffice-callback";

    private final List<IpAddressMatcher> allowMatchers;

    public OnlyOfficeCallbackIpFilter(OnlyOfficeCallbackProperties properties) {
        List<IpAddressMatcher> matchers = new ArrayList<>();
        for (String cidr : properties.getAllowedSourceCidrs()) {
            if (cidr == null) {
                continue;
            }
            String trimmed = cidr.trim();
            if (!trimmed.isEmpty()) {
                matchers.add(new IpAddressMatcher(trimmed));
            }
        }
        this.allowMatchers = List.copyOf(matchers);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (allowMatchers.isEmpty()) {
            return true;
        }
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        return !isOnlyOfficeCallbackPath(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        for (IpAddressMatcher matcher : allowMatchers) {
            if (matcher.matches(request)) {
                filterChain.doFilter(request, response);
                return;
            }
        }
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "OnlyOffice callback source not allowed");
    }

    static boolean isOnlyOfficeCallbackPath(String requestUri) {
        if (requestUri == null || !requestUri.endsWith(CALLBACK_SUFFIX)) {
            return false;
        }
        if (requestUri.contains(COMPOSITE_SEGMENT)) {
            return requestUri.contains("/api/composite-templates/");
        }
        return requestUri.contains(TEMPLATE_CALLBACK);
    }
}

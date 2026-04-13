package com.docgen.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Micrometer metrics configuration for API response time and throughput monitoring.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config().commonTags("application", "docgen-backend");
    }

    /**
     * Filter that records per-endpoint response time and request count metrics.
     */
    @Component
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    public static class ApiMetricsFilter extends OncePerRequestFilter {

        private final MeterRegistry meterRegistry;

        public ApiMetricsFilter(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                         HttpServletResponse response,
                                         FilterChain filterChain) throws ServletException, IOException {
            String path = request.getRequestURI();
            // Only track /api/** endpoints
            if (!path.startsWith("/api/")) {
                filterChain.doFilter(request, response);
                return;
            }

            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                filterChain.doFilter(request, response);
            } finally {
                String method = request.getMethod();
                String status = String.valueOf(response.getStatus());
                // Normalize path to avoid high-cardinality: replace numeric IDs
                String normalizedPath = path.replaceAll("/\\d+", "/{id}");

                sample.stop(Timer.builder("api.request.duration")
                        .description("API request duration")
                        .tag("method", method)
                        .tag("uri", normalizedPath)
                        .tag("status", status)
                        .register(meterRegistry));

                meterRegistry.counter("api.request.count",
                        "method", method,
                        "uri", normalizedPath,
                        "status", status).increment();
            }
        }
    }
}

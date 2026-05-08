package com.docgen.config;

import com.docgen.filter.ApiKeyAuthenticationFilter;
import com.docgen.filter.JwtAuthenticationFilter;
import com.docgen.filter.OnlyOfficeCallbackIpFilter;
import com.docgen.filter.RateLimitFilter;
import com.docgen.filter.TenantIsolationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration for the document generation system.
 *
 * <p>Configures a stateless security filter chain with the following order:
 * <ol>
 *   <li>{@link RateLimitFilter} — rate limiting based on API Key frequency limits and tenant quotas</li>
 *   <li>{@link ApiKeyAuthenticationFilter} — API Key authentication for external API calls</li>
 *   <li>{@link JwtAuthenticationFilter} — JWT Bearer token authentication for frontend sessions</li>
 *   <li>{@link TenantIsolationFilter} — extracts tenant ID from the authenticated principal</li>
 * </ol>
 *
 * <p>Public endpoints include {@code /api/auth/}, Ant-style path patterns for actuator health and
 * metrics, Swagger and OpenAPI routes, and OnlyOffice Document Server callbacks under
 * {@code /api/templates/{templateId}/onlyoffice-callback} and composite segment callbacks.
 * Those callback paths are {@code permitAll} because Document Server cannot authenticate as an
 * application user; requests are protected by OnlyOffice JWT validation and outbound URL policy
 * inside the service layer, and return a non-zero JSON {@code error} field when validation fails.
 * When {@code onlyoffice.callback.allowed-source-cidrs} is non-empty, {@link OnlyOfficeCallbackIpFilter}
 * rejects callback POSTs whose {@link jakarta.servlet.http.HttpServletRequest#getRemoteAddr()} is
 * outside all listed CIDRs before other filters run.
 * Tenant administration routes require the {@code SUPER_ADMIN} role; other {@code /api/} routes
 * require authentication.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    private final TenantIsolationFilter tenantIsolationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final OnlyOfficeCallbackIpFilter onlyOfficeCallbackIpFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          ApiKeyAuthenticationFilter apiKeyAuthenticationFilter,
                          TenantIsolationFilter tenantIsolationFilter,
                          RateLimitFilter rateLimitFilter,
                          OnlyOfficeCallbackIpFilter onlyOfficeCallbackIpFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
        this.tenantIsolationFilter = tenantIsolationFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.onlyOfficeCallbackIpFilter = onlyOfficeCallbackIpFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Filter chain order: OnlyOffice callback IP (optional) → RateLimit → ApiKey → JWT → TenantIsolation
            .addFilterBefore(onlyOfficeCallbackIpFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(apiKeyAuthenticationFilter, RateLimitFilter.class)
            .addFilterAfter(jwtAuthenticationFilter, ApiKeyAuthenticationFilter.class)
            .addFilterAfter(tenantIsolationFilter, JwtAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/metrics", "/actuator/metrics/**", "/actuator/prometheus").permitAll()
                .requestMatchers("/swagger-ui/**", "/api-docs/**", "/v3/api-docs/**").permitAll()
                // OnlyOffice callback is invoked by Document Server without JWT
                .requestMatchers("/api/templates/*/onlyoffice-callback").permitAll()
                .requestMatchers("/api/composite-templates/*/segments/*/onlyoffice-callback").permitAll()
                .requestMatchers("/api/tenants/*/teams", "/api/tenants/*/teams/**").hasAnyRole("SUPER_ADMIN", "TENANT_ADMIN")
                .requestMatchers("/api/tenants/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            );
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

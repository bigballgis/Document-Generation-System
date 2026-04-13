package com.docgen.config;

import com.docgen.filter.ApiKeyAuthenticationFilter;
import com.docgen.filter.JwtAuthenticationFilter;
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
 * <p>Public endpoints: {@code /api/auth/**}, {@code /actuator/health},
 * {@code /swagger-ui/**}, {@code /api-docs/**}, {@code /v3/api-docs/**}.
 * The {@code /api/tenants/**} endpoints require the {@code SUPER_ADMIN} role.
 * All other {@code /api/**} endpoints require authentication.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    private final TenantIsolationFilter tenantIsolationFilter;
    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          ApiKeyAuthenticationFilter apiKeyAuthenticationFilter,
                          TenantIsolationFilter tenantIsolationFilter,
                          RateLimitFilter rateLimitFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
        this.tenantIsolationFilter = tenantIsolationFilter;
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Filter chain order: RateLimit → ApiKey → JWT → TenantIsolation
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

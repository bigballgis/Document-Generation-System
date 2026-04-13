package com.docgen.filter;

import com.docgen.dto.UserPrincipal;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Converts an OAuth 2.0 JWT token into a Spring Security
 * {@link AbstractAuthenticationToken} backed by a {@link UserPrincipal}.
 *
 * <p>Claim mapping:
 * <ul>
 *   <li>{@code sub}       → userId</li>
 *   <li>{@code tenant_id} → tenantId</li>
 *   <li>{@code role}      → role (defaults to {@code USER})</li>
 *   <li>{@code team_id}   → teamId</li>
 * </ul>
 *
 * <p>This converter is registered as a bean by {@link com.docgen.config.OAuth2Config}
 * and will be wired into the security filter chain by the SecurityConfig (task 4.5).</p>
 */
public class OAuth2TokenConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String DEFAULT_ROLE = "USER";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long userId = parseAsLong(jwt.getClaimAsString("sub"));
        Long tenantId = parseAsLong(jwt.getClaimAsString("tenant_id"));
        String role = jwt.getClaimAsString("role");
        Long teamId = parseAsLong(jwt.getClaimAsString("team_id"));

        if (role == null || role.isBlank()) {
            role = DEFAULT_ROLE;
        }

        String username = userId != null ? String.valueOf(userId) : jwt.getSubject();

        UserPrincipal principal = new UserPrincipal(userId, tenantId, role, teamId, username);

        return new UsernamePasswordAuthenticationToken(principal, jwt, principal.getAuthorities());
    }

    /**
     * Safely parses a string claim value to Long.
     * Returns {@code null} when the value is null, blank, or not a valid number.
     */
    private Long parseAsLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

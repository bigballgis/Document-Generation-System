package com.docgen.filter;

import com.docgen.dto.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OAuth2TokenConverterTest {

    private OAuth2TokenConverter converter;

    @BeforeEach
    void setUp() {
        converter = new OAuth2TokenConverter();
    }

    @Test
    void convert_allClaimsPresent_mapsToUserPrincipal() {
        Jwt jwt = buildJwt(Map.of(
                "sub", "42",
                "tenant_id", "10",
                "role", "TENANT_ADMIN",
                "team_id", "5"
        ));

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertNotNull(token);
        UserPrincipal principal = (UserPrincipal) token.getPrincipal();
        assertEquals(42L, principal.getUserId());
        assertEquals(10L, principal.getTenantId());
        assertEquals("TENANT_ADMIN", principal.getRole());
        assertEquals(5L, principal.getTeamId());
        assertEquals("42", principal.getUsername());
        assertTrue(principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_TENANT_ADMIN")));
    }

    @Test
    void convert_missingRole_defaultsToUser() {
        Jwt jwt = buildJwt(Map.of("sub", "7", "tenant_id", "1"));

        AbstractAuthenticationToken token = converter.convert(jwt);

        UserPrincipal principal = (UserPrincipal) token.getPrincipal();
        assertEquals("USER", principal.getRole());
    }

    @Test
    void convert_blankRole_defaultsToUser() {
        Jwt jwt = buildJwt(Map.of("sub", "7", "tenant_id", "1", "role", "  "));

        AbstractAuthenticationToken token = converter.convert(jwt);

        UserPrincipal principal = (UserPrincipal) token.getPrincipal();
        assertEquals("USER", principal.getRole());
    }

    @Test
    void convert_missingOptionalClaims_returnsNulls() {
        Jwt jwt = buildJwt(Map.of("sub", "99"));

        AbstractAuthenticationToken token = converter.convert(jwt);

        UserPrincipal principal = (UserPrincipal) token.getPrincipal();
        assertEquals(99L, principal.getUserId());
        assertNull(principal.getTenantId());
        assertNull(principal.getTeamId());
    }

    @Test
    void convert_nonNumericSub_userIdIsNull_usernameIsSub() {
        Jwt jwt = buildJwt(Map.of("sub", "alice@example.com"));

        AbstractAuthenticationToken token = converter.convert(jwt);

        UserPrincipal principal = (UserPrincipal) token.getPrincipal();
        assertNull(principal.getUserId());
        assertEquals("alice@example.com", principal.getUsername());
    }

    @Test
    void convert_credentialIsOriginalJwt() {
        Jwt jwt = buildJwt(Map.of("sub", "1"));

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertSame(jwt, token.getCredentials());
    }

    private Jwt buildJwt(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));
        claims.forEach(builder::claim);
        return builder.build();
    }
}

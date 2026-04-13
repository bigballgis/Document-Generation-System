package com.docgen.util;

import com.docgen.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-key-for-testing-purposes-only-256bit");
        props.setAccessTokenExpiration(7200000L);
        props.setRefreshTokenExpiration(604800000L);
        provider = new JwtTokenProvider(props);
    }

    @Test
    void generateAndValidateAccessToken() {
        String token = provider.generateAccessToken(1L, 10L, "USER", 100L);

        assertTrue(provider.validateToken(token));
        assertEquals(1L, provider.getUserIdFromToken(token));
        assertEquals(10L, provider.getTenantIdFromToken(token));
        assertEquals("USER", provider.getRoleFromToken(token));
        assertEquals(100L, provider.getTeamIdFromToken(token));
    }

    @Test
    void generateAndValidateRefreshToken() {
        String token = provider.generateRefreshToken(42L);

        assertTrue(provider.validateToken(token));
        assertEquals(42L, provider.getUserIdFromToken(token));
    }

    @Test
    void invalidTokenReturnsFalse() {
        assertFalse(provider.validateToken("not.a.valid.token"));
        assertFalse(provider.validateToken(""));
        assertFalse(provider.validateToken(null));
    }

    @Test
    void expiredTokenReturnsFalse() {
        JwtProperties shortLived = new JwtProperties();
        shortLived.setSecret("test-secret-key-for-testing-purposes-only-256bit");
        shortLived.setAccessTokenExpiration(0L); // immediate expiry
        shortLived.setRefreshTokenExpiration(604800000L);
        JwtTokenProvider shortProvider = new JwtTokenProvider(shortLived);

        String token = shortProvider.generateAccessToken(1L, 10L, "USER", 100L);
        assertFalse(shortProvider.validateToken(token));
    }

    @Test
    void tokenFromDifferentSecretIsInvalid() {
        JwtProperties otherProps = new JwtProperties();
        otherProps.setSecret("another-secret-key-that-is-at-least-256-bits!");
        otherProps.setAccessTokenExpiration(7200000L);
        otherProps.setRefreshTokenExpiration(604800000L);
        JwtTokenProvider otherProvider = new JwtTokenProvider(otherProps);

        String token = provider.generateAccessToken(1L, 10L, "USER", 100L);
        assertFalse(otherProvider.validateToken(token));
    }
}

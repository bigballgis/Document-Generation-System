package com.docgen.config;

import com.docgen.filter.OAuth2TokenConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;

/**
 * Conditionally enables OAuth 2.0 resource server support when
 * {@code oauth2.enabled=true}. Configures a {@link JwtDecoder} using
 * either the JWK Set URI or the OIDC issuer URI provided in
 * application properties.
 *
 * <p>When disabled (the default), no OAuth2-related beans are created
 * and the application relies solely on its internal JWT / API Key
 * authentication mechanisms.</p>
 */
@Configuration
@ConditionalOnProperty(name = "oauth2.enabled", havingValue = "true")
public class OAuth2Config {

    private final OAuth2Properties oauth2Properties;

    public OAuth2Config(OAuth2Properties oauth2Properties) {
        this.oauth2Properties = oauth2Properties;
    }

    /**
     * Creates a {@link JwtDecoder} for validating external OAuth 2.0 access tokens.
     * Prefers the explicit JWK Set URI; falls back to OIDC issuer discovery.
     */
    @Bean
    public JwtDecoder oauthJwtDecoder() {
        if (StringUtils.hasText(oauth2Properties.getJwkSetUri())) {
            return NimbusJwtDecoder.withJwkSetUri(oauth2Properties.getJwkSetUri()).build();
        }
        if (StringUtils.hasText(oauth2Properties.getIssuerUri())) {
            return JwtDecoders.fromIssuerLocation(oauth2Properties.getIssuerUri());
        }
        throw new IllegalStateException(
                "OAuth2 is enabled but neither oauth2.jwk-set-uri nor oauth2.issuer-uri is configured");
    }

    /**
     * Provides the converter that maps OAuth 2.0 JWT claims to a {@link com.docgen.dto.UserPrincipal}.
     */
    @Bean
    public OAuth2TokenConverter oauth2TokenConverter() {
        return new OAuth2TokenConverter();
    }
}

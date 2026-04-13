package com.docgen.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for optional OAuth 2.0 resource server support.
 * Reads from the {@code oauth2.*} namespace in application.yml.
 */
@Component
@ConfigurationProperties(prefix = "oauth2")
public class OAuth2Properties {

    private boolean enabled = false;
    private String issuerUri = "";
    private String jwkSetUri = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIssuerUri() {
        return issuerUri;
    }

    public void setIssuerUri(String issuerUri) {
        this.issuerUri = issuerUri;
    }

    public String getJwkSetUri() {
        return jwkSetUri;
    }

    public void setJwkSetUri(String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
    }
}

package com.docgen.security.url;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for outbound URL validation used to mitigate SSRF risks.
 */
@ConfigurationProperties(prefix = "url-policy")
public class UrlPolicyProperties {

    /**
     * When true, permits loopback, link-local, site-local, and CGNAT IPv4 ranges.
     * Intended for local development only.
     */
    private boolean allowPrivateAddresses = false;

    /**
     * Lowercased hostnames or literal IP strings permitted in outbound URLs.
     * Deny-by-default: empty list rejects all hosts.
     */
    private List<String> allowedHosts = new ArrayList<>();

    /**
     * Explicit TCP ports permitted for outbound URLs (after applying scheme defaults for omitted ports).
     */
    private List<Integer> allowedPorts = new ArrayList<>(List.of(80, 443));

    /**
     * When true, {@link OutboundUrlPolicy#validatePublicEgressHttpUrl(String)} only allows {@code https} URLs.
     */
    private boolean webhookRequireHttps = true;

    public boolean isAllowPrivateAddresses() {
        return allowPrivateAddresses;
    }

    public void setAllowPrivateAddresses(boolean allowPrivateAddresses) {
        this.allowPrivateAddresses = allowPrivateAddresses;
    }

    public List<String> getAllowedHosts() {
        return allowedHosts;
    }

    public void setAllowedHosts(List<String> allowedHosts) {
        this.allowedHosts = allowedHosts;
    }

    public List<Integer> getAllowedPorts() {
        return allowedPorts;
    }

    public void setAllowedPorts(List<Integer> allowedPorts) {
        this.allowedPorts = allowedPorts;
    }

    public boolean isWebhookRequireHttps() {
        return webhookRequireHttps;
    }

    public void setWebhookRequireHttps(boolean webhookRequireHttps) {
        this.webhookRequireHttps = webhookRequireHttps;
    }
}

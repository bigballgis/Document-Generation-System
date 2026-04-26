package com.docgen.security.url;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutboundUrlPolicyTest {

    private UrlPolicyProperties properties;
    private OutboundUrlPolicy policy;

    @BeforeEach
    void setUp() {
        properties = new UrlPolicyProperties();
        properties.setAllowPrivateAddresses(false);
        properties.setAllowedHosts(new ArrayList<>());
        properties.setAllowedPorts(new ArrayList<>(List.of(80, 443)));
        policy = new OutboundUrlPolicy(properties);
    }

    @Test
    void rejects_localhost_whenNotAllowlisted() {
        UrlValidationResult result = policy.validateHttpUrl("http://localhost/");
        assertThat(result.allowed()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("HOST_NOT_ALLOWED");
    }

    @Test
    void rejects_localhost_whenAllowlisted_butResolvesToLoopback() {
        properties.setAllowedHosts(List.of("localhost"));

        UrlValidationResult result = policy.validateHttpUrl("http://localhost/");
        assertThat(result.allowed()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("DISALLOWED_ADDRESS");
    }

    @Test
    void rejects_loopbackLiteral_whenAllowlisted() {
        properties.setAllowedHosts(List.of("127.0.0.1"));

        UrlValidationResult result = policy.validateHttpUrl("http://127.0.0.1/");
        assertThat(result.allowed()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("DISALLOWED_ADDRESS");
    }

    @Test
    void rejects_private_ipv4_whenAllowlisted() {
        properties.setAllowedHosts(List.of("10.0.0.1"));

        UrlValidationResult result = policy.validateHttpUrl("http://10.0.0.1/");
        assertThat(result.allowed()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("DISALLOWED_ADDRESS");
    }

    @Test
    void rejects_metadata_ipv4_evenWhenAllowlisted() {
        properties.setAllowedHosts(List.of("169.254.169.254"));

        UrlValidationResult result = policy.validateHttpUrl("http://169.254.169.254/latest/meta-data");
        assertThat(result.allowed()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("BLOCKED_METADATA_ENDPOINT");
    }

    @Test
    void allows_configured_public_literal_host() {
        properties.setAllowedHosts(List.of("8.8.8.8"));

        UrlValidationResult result = policy.validateHttpUrl("http://8.8.8.8/");
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void allows_https_default_port() {
        properties.setAllowedHosts(List.of("8.8.8.8"));

        UrlValidationResult result = policy.validateHttpUrl("https://8.8.8.8/");
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void rejects_documentation_net_evenWhenAllowlisted() {
        properties.setAllowedHosts(List.of("192.0.2.1"));

        UrlValidationResult result = policy.validateHttpUrl("http://192.0.2.1/");
        assertThat(result.allowed()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("DISALLOWED_ADDRESS");
    }

    @Test
    void rejects_unsupported_scheme() {
        properties.setAllowedHosts(List.of("8.8.8.8"));

        UrlValidationResult result = policy.validateHttpUrl("ftp://8.8.8.8/");
        assertThat(result.allowed()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("UNSUPPORTED_SCHEME");
    }

    @Test
    void rejects_userinfo() {
        properties.setAllowedHosts(List.of("8.8.8.8"));

        UrlValidationResult result = policy.validateHttpUrl("http://user:pass@8.8.8.8/");
        assertThat(result.allowed()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("USERINFO_NOT_ALLOWED");
    }

    @Test
    void rejects_non_allowlisted_port() {
        properties.setAllowedHosts(List.of("8.8.8.8"));
        properties.setAllowedPorts(List.of(80));

        UrlValidationResult result = policy.validateHttpUrl("http://8.8.8.8:8080/");
        assertThat(result.allowed()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("PORT_NOT_ALLOWED");
    }

    @Test
    void allows_loopback_whenDevFlagEnabled() {
        properties.setAllowPrivateAddresses(true);
        properties.setAllowedHosts(List.of("127.0.0.1"));

        UrlValidationResult result = policy.validateHttpUrl("http://127.0.0.1/");
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void validateHttpUrlWithMergedHosts_allowsExtraHostsWithoutGlobalAllowlist() {
        properties.setAllowedHosts(List.of());

        UrlValidationResult result = policy.validateHttpUrlWithMergedHosts("http://8.8.8.8/", List.of("8.8.8.8"));
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void validatePublicEgress_allowsHttpsPublicHost() {
        UrlValidationResult result = policy.validatePublicEgressHttpUrl("https://8.8.8.8/");
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void validatePublicEgress_rejectsHttp_whenHttpsRequired() {
        UrlValidationResult result = policy.validatePublicEgressHttpUrl("http://8.8.8.8/");
        assertThat(result.allowed()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("HTTPS_REQUIRED");
    }
}

package com.docgen.security.url;

import org.springframework.util.StringUtils;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Validates outbound HTTP(S) URLs before the application performs server-side fetches.
 *
 * <p>Policy highlights:
 * <ul>
 *   <li>Deny-by-default host allowlist (with optional merged hosts for known integrations)</li>
 *   <li>Scheme allowlist: http and https only</li>
 *   <li>Port allowlist with scheme-default handling for omitted ports</li>
 *   <li>DNS resolution with validation of all resolved addresses</li>
 *   <li>Rejects private/link-local/loopback/multicast/CGNAT ranges unless explicitly allowed for dev</li>
 *   <li>Optional public-egress mode for tenant-controlled URLs (for example webhooks)</li>
 * </ul>
 */
public final class OutboundUrlPolicy {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private final UrlPolicyProperties properties;

    public OutboundUrlPolicy(UrlPolicyProperties properties) {
        this.properties = properties;
    }

    public UrlValidationResult validateHttpUrl(String rawUrl) {
        return validateHttpUrlWithMergedHosts(rawUrl, List.of());
    }

    /**
     * Validates an outbound URL using the configured allowlist merged with additional hostnames
     * (for example OnlyOffice Document Server hosts that are not globally allowlisted).
     */
    public UrlValidationResult validateHttpUrlWithMergedHosts(String rawUrl, Iterable<String> extraAllowedHosts) {
        ParseStep step = parseUriBasics(rawUrl);
        if (step instanceof ParseStep.Failure failure) {
            return failure.result();
        }
        ParsedOutboundUrl parsed = ((ParseStep.Success) step).parsed();

        Set<String> mergedHosts = mergeAllowedHosts(extraAllowedHosts);
        if (mergedHosts.isEmpty()) {
            return UrlValidationResult.rejected("HOST_NOT_ALLOWED", "No allowlisted hosts configured");
        }
        if (!mergedHosts.contains(parsed.normalizedHost())) {
            return UrlValidationResult.rejected("HOST_NOT_ALLOWED", "Host is not allowlisted");
        }

        return validatePortsAndResolvedAddresses(parsed);
    }

    /**
     * Validates outbound URLs for tenant-controlled destinations (for example webhooks).
     *
     * <p>This mode does not use a host allowlist, but still enforces scheme rules, port allowlists,
     * DNS resolution, and disallowed address ranges to reduce SSRF to internal networks.
     */
    public UrlValidationResult validatePublicEgressHttpUrl(String rawUrl) {
        ParseStep step = parseUriBasics(rawUrl);
        if (step instanceof ParseStep.Failure failure) {
            return failure.result();
        }
        ParsedOutboundUrl parsed = ((ParseStep.Success) step).parsed();

        if (properties.isWebhookRequireHttps() && !"https".equalsIgnoreCase(parsed.scheme())) {
            return UrlValidationResult.rejected("HTTPS_REQUIRED", "Only https webhook URLs are permitted");
        }

        return validatePortsAndResolvedAddresses(parsed);
    }

    private ParseStep parseUriBasics(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return new ParseStep.Failure(UrlValidationResult.rejected("EMPTY_URL", "URL is blank"));
        }

        final URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (Exception e) {
            return new ParseStep.Failure(UrlValidationResult.rejected("INVALID_URL", "URL is not a valid URI"));
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            return new ParseStep.Failure(
                    UrlValidationResult.rejected("UNSUPPORTED_SCHEME", "Only http and https URLs are supported"));
        }

        if (StringUtils.hasText(uri.getRawUserInfo())) {
            return new ParseStep.Failure(
                    UrlValidationResult.rejected("USERINFO_NOT_ALLOWED", "URLs with userinfo are not allowed"));
        }

        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            return new ParseStep.Failure(UrlValidationResult.rejected("MISSING_HOST", "URL must include a host"));
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return new ParseStep.Success(new ParsedOutboundUrl(uri, scheme, host, normalizedHost));
    }

    private UrlValidationResult validatePortsAndResolvedAddresses(ParsedOutboundUrl parsed) {
        int declaredPort = parsed.uri().getPort();
        int effectivePort = declaredPort == -1
                ? ("https".equalsIgnoreCase(parsed.scheme()) ? 443 : 80)
                : declaredPort;

        if (!isPortAllowed(effectivePort)) {
            return UrlValidationResult.rejected("PORT_NOT_ALLOWED", "Port is not allowlisted");
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(parsed.host());
        } catch (UnknownHostException e) {
            return UrlValidationResult.rejected("DNS_RESOLUTION_FAILED", "Host could not be resolved");
        }

        if (addresses == null || addresses.length == 0) {
            return UrlValidationResult.rejected("DNS_RESOLUTION_EMPTY", "Host resolved to no addresses");
        }

        for (InetAddress address : addresses) {
            UrlValidationResult addressCheck = validateResolvedAddress(address);
            if (!addressCheck.allowed()) {
                return addressCheck;
            }
        }

        return UrlValidationResult.ok();
    }

    private Set<String> mergeAllowedHosts(Iterable<String> extraAllowedHosts) {
        Set<String> allowed = new HashSet<>();
        for (String h : properties.getAllowedHosts()) {
            if (StringUtils.hasText(h)) {
                allowed.add(h.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (extraAllowedHosts != null) {
            for (String h : extraAllowedHosts) {
                if (StringUtils.hasText(h)) {
                    allowed.add(h.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return allowed;
    }

    private record ParsedOutboundUrl(URI uri, String scheme, String host, String normalizedHost) {
    }

    private sealed interface ParseStep permits ParseStep.Success, ParseStep.Failure {
        record Success(ParsedOutboundUrl parsed) implements ParseStep {
        }

        record Failure(UrlValidationResult result) implements ParseStep {
        }
    }

    private boolean isPortAllowed(int port) {
        for (int p : properties.getAllowedPorts()) {
            if (p == port) {
                return true;
            }
        }
        return false;
    }

    private UrlValidationResult validateResolvedAddress(InetAddress address) {
        if (isAlwaysBlockedMetadataEndpoint(address)) {
            return UrlValidationResult.rejected("BLOCKED_METADATA_ENDPOINT", "Blocked cloud metadata endpoint");
        }

        if (!properties.isAllowPrivateAddresses() && isDisallowedNetworkAddress(address)) {
            return UrlValidationResult.rejected("DISALLOWED_ADDRESS", "Resolved address is not permitted");
        }

        return UrlValidationResult.ok();
    }

    private static boolean isAlwaysBlockedMetadataEndpoint(InetAddress address) {
        if (address instanceof Inet4Address) {
            byte[] b = address.getAddress();
            // AWS / Azure style link-local metadata IPv4
            return b.length == 4 && (b[0] & 0xFF) == 169 && (b[1] & 0xFF) == 254 && (b[2] & 0xFF) == 169 && (b[3] & 0xFF) == 254;
        }
        return false;
    }

    private static boolean isDisallowedNetworkAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        if (address instanceof Inet4Address v4) {
            return isCarrierGradeNat(v4) || isDocumentationOrBenchmarkNet(v4);
        }

        if (address instanceof Inet6Address v6) {
            return isIpv6UniqueLocal(v6) || isIpv6Documentation(v6);
        }

        return false;
    }

    private static boolean isCarrierGradeNat(Inet4Address address) {
        byte[] b = address.getAddress();
        if (b.length != 4) {
            return false;
        }
        int first = b[0] & 0xFF;
        int second = b[1] & 0xFF;
        return first == 100 && second >= 64 && second <= 127;
    }

    /**
     * Blocks TEST-NET and benchmarking ranges that should not be used for production egress.
     */
    private static boolean isDocumentationOrBenchmarkNet(Inet4Address address) {
        byte[] b = address.getAddress();
        if (b.length != 4) {
            return false;
        }
        int a = b[0] & 0xFF;
        int c = b[2] & 0xFF;
        int d = b[3] & 0xFF;

        if (a == 192 && (b[1] & 0xFF) == 0 && (b[2] & 0xFF) == 2) {
            return true; // 192.0.2.0/24
        }
        if (a == 198 && (b[1] & 0xFF) == 51 && c == 100) {
            return true; // 198.51.100.0/24
        }
        if (a == 203 && (b[1] & 0xFF) == 0 && c == 113) {
            return true; // 203.0.113.0/24
        }
        if (a == 198 && (b[1] & 0xFF) == 18) {
            return true; // 198.18.0.0/15 (benchmarking)
        }
        if (a == 127 && (b[1] & 0xFF) == 0 && c == 0 && d == 1) {
            return true; // common alternate loopback
        }
        return false;
    }

    private static boolean isIpv6UniqueLocal(Inet6Address address) {
        byte[] b = address.getAddress();
        if (b.length != 16) {
            return false;
        }
        return (b[0] & 0xfe) == 0xfc;
    }

    private static boolean isIpv6Documentation(Inet6Address address) {
        byte[] b = address.getAddress();
        if (b.length != 16) {
            return false;
        }
        // 2001:db8::/32
        return b[0] == 0x20 && b[1] == 0x01 && b[2] == 0x0d && (b[3] & 0xf0) == (byte) 0xb0;
    }
}

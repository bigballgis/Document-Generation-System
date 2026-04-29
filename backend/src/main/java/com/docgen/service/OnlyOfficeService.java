package com.docgen.service;

import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.TemplateRepository;
import com.docgen.security.url.OutboundUrlPolicy;
import com.docgen.security.url.UrlValidationResult;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Service for OnlyOffice Document Server integration.
 *
 * <p>Generates presigned MinIO URLs so that OnlyOffice can download template files
 * directly from MinIO without going through the authenticated backend API.
 *
 * <p>Handles OnlyOffice save callbacks by downloading the edited document from
 * the URL provided by OnlyOffice and updating the template file in MinIO.
 */
@Service
public class OnlyOfficeService {

    private static final Logger log = LoggerFactory.getLogger(OnlyOfficeService.class);

    /** Presigned URL validity duration in minutes. */
    private static final int PRESIGNED_URL_EXPIRY_MINUTES = 60;

    private final TemplateRepository templateRepository;
    private final MinioClient minioClient;
    private final CallbackDocumentDownloadHelper callbackDocumentDownloadHelper;
    private final OutboundUrlPolicy outboundUrlPolicy;
    private final SecretKey onlyOfficeSigningKey;

    @Value("${minio.bucket-name:docgen}")
    private String bucketName;

    @Value("${minio.external-endpoint:${minio.endpoint:http://localhost:9000}}")
    private String minioExternalEndpoint;

    @Value("${onlyoffice.url:http://localhost}")
    private String onlyOfficeUrl;

    @Value("${onlyoffice.callback.require-jwt:true}")
    private boolean callbackRequireJwt;

    @Value("${onlyoffice.callback.allowed-hosts:}")
    private List<String> callbackAllowedHosts;

    @Value("${onlyoffice.callback.max-download-bytes:20971520}")
    private long callbackMaxDownloadBytes;

    public OnlyOfficeService(TemplateRepository templateRepository,
                             MinioClient minioClient,
                             CallbackDocumentDownloadHelper callbackDocumentDownloadHelper,
                             OutboundUrlPolicy outboundUrlPolicy,
                             @Value("${onlyoffice.jwt-secret:my_jwt_secret}") String onlyOfficeJwtSecret) {
        this.templateRepository = templateRepository;
        this.minioClient = minioClient;
        this.callbackDocumentDownloadHelper = callbackDocumentDownloadHelper;
        this.outboundUrlPolicy = outboundUrlPolicy;
        this.onlyOfficeSigningKey = Keys.hmacShaKeyFor(
                onlyOfficeJwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * OnlyOffice often sends {@code localhost} / loopback in callback {@code url} when the editor was opened via
     * a browser host such as {@code localhost}. The Spring backend resolves {@code localhost} to itself, not to
     * Document Server — so we rewrite loopback hosts to {@link #onlyOfficeUrl} before outbound policy and GET.
     *
     * @return URL string to use for {@link OutboundUrlPolicy} validation and {@link CallbackDocumentDownloadHelper}.
     */
    public String resolveCallbackDownloadFetchUrl(String rawCallbackUrl) {
        if (rawCallbackUrl == null || rawCallbackUrl.isBlank()) {
            return rawCallbackUrl != null ? rawCallbackUrl.trim() : null;
        }
        String trimmed = rawCallbackUrl.strip();
        try {
            URI documentServerBase = URI.create(onlyOfficeUrl.strip());
            String canonicalHost = documentServerBase.getHost();
            if (canonicalHost == null) {
                return trimmed;
            }

            URI u = URI.create(trimmed);
            String fetchHost = u.getHost();
            if (fetchHost == null || !InetAddress.getByName(fetchHost.strip()).isLoopbackAddress()) {
                return trimmed;
            }

            String scheme = documentServerBase.getScheme();
            if (scheme == null || scheme.isBlank()) {
                scheme = u.getScheme();
            }

            URI rebuilt = new URI(
                    scheme,
                    documentServerBase.getRawUserInfo(),
                    canonicalHost,
                    documentServerBase.getPort(),
                    u.getRawPath(),
                    u.getRawQuery(),
                    u.getRawFragment());

            log.debug(
                    "OnlyOffice fetch URL loopback '{}:{}' rewrote to '{}' for backend outbound fetch",
                    fetchHost,
                    u.getPort() >= 0 ? u.getPort() : -1,
                    rebuilt.toASCIIString());
            return rebuilt.toASCIIString();
        } catch (UnknownHostException e) {
            return trimmed;
        } catch (URISyntaxException e) {
            log.warn("Could not normalize OnlyOffice callback download URL syntax: {}", e.getMessage());
            return trimmed;
        } catch (Exception e) {
            log.warn("Could not normalize OnlyOffice callback download URL: {}", e.toString());
            return trimmed;
        }
    }

    /**
     * Generate a presigned GET URL for the template file in MinIO.
     * OnlyOffice Document Server will use this URL to download the document.
     */
    public String generatePresignedUrl(Long templateId) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND,
                        "模板不存在: " + templateId, HttpStatus.NOT_FOUND));

        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(template.getTemplateFilePath())
                            .expiry(PRESIGNED_URL_EXPIRY_MINUTES, TimeUnit.MINUTES)
                            .build());
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for template {}: {}",
                    templateId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.ONLYOFFICE_URL_FAILED,
                    "生成文档访问链接失败", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * Generate a JWT token for the OnlyOffice editor config.
     * The token payload must mirror the full editor config that the frontend sends.
     */
    public String generateEditorToken(Map<String, Object> editorConfig) {
        Date now = new Date();
        // Token valid for the same duration as the presigned URL
        Date expiry = new Date(now.getTime() + PRESIGNED_URL_EXPIRY_MINUTES * 60 * 1000L);

        return Jwts.builder()
                .claims(editorConfig)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(onlyOfficeSigningKey)
                .compact();
    }

    /**
     * Handle OnlyOffice Document Server callback.
     *
     * <p>Status codes from OnlyOffice:
     * <ul>
     *   <li>1 — document is being edited</li>
     *   <li>2 — document is ready for saving (all editors closed)</li>
     *   <li>4 — document closed with no changes</li>
     *   <li>6 — document is being edited, but the current state is saved (forcesave)</li>
     *   <li>7 — error has occurred while force saving the document</li>
     * </ul>
     *
     * @return OnlyOffice callback {@code error} field: {@code 0} means the request was accepted (including
     *         benign no-ops), {@code 1} means the save was not performed due to validation or download issues.
     */
    public int handleCallback(Long templateId, Map<String, Object> body) {
        return handleCallback(templateId, body, null);
    }

    /**
     * Handle OnlyOffice Document Server callback with an optional authentication header.
     *
     * <p>This endpoint is intentionally reachable without application JWT, because Document Server
     * cannot attach the application's user auth token. Instead, we validate the OnlyOffice JWT
     * (if required) and enforce a strict download URL allowlist to reduce SSRF exposure.
     *
     * @return OnlyOffice callback {@code error} code ({@code 0} success / acknowledged, {@code 1} rejected).
     */
    public int handleCallback(Long templateId, Map<String, Object> body, String authorizationHeader) {
        int status = body.get("status") instanceof Number
                ? ((Number) body.get("status")).intValue() : 0;

        // Status 2 (ready for saving) or 6 (forcesave) — download and update
        if (status == 2 || status == 6) {
            String rawUrl = (String) body.get("url");
            if (rawUrl == null || rawUrl.isBlank()) {
                log.warn("OnlyOffice callback for template {} has no download URL", templateId);
                return 0;
            }

            String downloadUrl = resolveCallbackDownloadFetchUrl(rawUrl);
            if (!rawUrl.strip().equals(downloadUrl)) {
                log.debug("OnlyOffice callback rewrote Document Server fetch URL from {} to {}", rawUrl, downloadUrl);
            }

            String callbackToken = extractOnlyOfficeToken(body, authorizationHeader);
            if (callbackRequireJwt && (callbackToken == null || !isValidOnlyOfficeJwt(callbackToken))) {
                log.warn("OnlyOffice callback JWT validation failed for template {}", templateId);
                return 1;
            }

            if (!isAllowedCallbackDownloadUrl(downloadUrl)) {
                log.warn("OnlyOffice callback download URL is not allowed for template {}", templateId);
                return 1;
            }
            return saveEditedDocument(templateId, downloadUrl) ? 0 : 1;
        }
        return 0;
    }

    /**
     * @return {@code true} if the edited document was written to MinIO, {@code false} if the template
     *         was missing, the download was empty, or the download was rejected by policy limits.
     */
    private boolean saveEditedDocument(Long templateId, String downloadUrl) {
        return templateRepository.findById(templateId)
                .map(template -> saveEditedDocumentForTemplate(template, templateId, downloadUrl))
                .orElseGet(() -> {
                    log.warn("OnlyOffice callback for missing template {}", templateId);
                    return false;
                });
    }

    private boolean saveEditedDocumentForTemplate(Template template, Long templateId, String downloadUrl) {
        try {
            // Download the edited document from OnlyOffice with basic resource limits.
            byte[] editedContent = callbackDocumentDownloadHelper.downloadOnlyOfficeDocx(
                    downloadUrl, callbackMaxDownloadBytes);
            if (editedContent == null || editedContent.length == 0) {
                log.warn("Downloaded empty content from OnlyOffice for template {}", templateId);
                return false;
            }

            // Overwrite the template file in MinIO
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(template.getTemplateFilePath())
                    .stream(new ByteArrayInputStream(editedContent), editedContent.length, -1)
                    .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    .build());

            log.info("Saved edited document from OnlyOffice for template {}, size={} bytes",
                    templateId, editedContent.length);
            return true;
        } catch (BusinessException e) {
            if (e.getHttpStatus() == HttpStatus.BAD_REQUEST || e.getHttpStatus() == HttpStatus.PAYLOAD_TOO_LARGE) {
                log.warn("OnlyOffice callback download rejected for template {}: {} ({})",
                        templateId, e.getErrorCode(), e.getMessage());
                return false;
            }
            throw e;
        } catch (Exception e) {
            log.error("Failed to save OnlyOffice edited document for template {}: {}",
                    templateId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.ONLYOFFICE_CALLBACK_FAILED,
                    "保存编辑文档失败", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    public boolean isAllowedCallbackDownloadUrl(String downloadUrl) {
        List<String> extraAllowedHosts = buildCallbackDownloadExtraHosts();
        UrlValidationResult first = outboundUrlPolicy.validateHttpUrlWithMergedHosts(downloadUrl, extraAllowedHosts);
        if (first.allowed()) {
            return true;
        }
        /*
         * Document Server may use another resolvable hostname for the same container (for example the
         * Compose-scoped DNS name) while {@code onlyoffice.url} uses the short service name. If both
         * resolve to the same addresses, treat the download as targeting the configured Document Server.
         */
        try {
            URI downloadUri = URI.create(downloadUrl.trim());
            String downloadHost = downloadUri.getHost();
            URI onlyOfficeUri = URI.create(onlyOfficeUrl.trim());
            String canonicalHost = onlyOfficeUri.getHost();
            if (downloadHost == null || canonicalHost == null) {
                log.warn("OnlyOffice callback download URL rejected (missing host): initialReason={}: {}",
                        first.reasonCode(), first.message());
                return false;
            }
            if (!resolvedAddressesOverlap(downloadHost, canonicalHost)) {
                log.warn("OnlyOffice callback download URL rejected: urlHost={} initialReason={}: {} "
                                + "(fallback: address set does not overlap with Document Server '{}')",
                        downloadHost, first.reasonCode(), first.message(), canonicalHost);
                return false;
            }
            String normalizedUrl = rewriteUriHost(downloadUri, canonicalHost);
            UrlValidationResult second =
                    outboundUrlPolicy.validateHttpUrlWithMergedHosts(normalizedUrl, extraAllowedHosts);
            if (second.allowed()) {
                return true;
            }
            log.warn("OnlyOffice callback download URL rejected after same-host normalization: urlHost={} "
                            + "initialReason={}: {} fallbackReason={}: {}",
                    downloadHost,
                    first.reasonCode(), first.message(),
                    second.reasonCode(), second.message());
            return false;
        } catch (Exception e) {
            log.warn("OnlyOffice callback download URL fallback failed initialReason={}: {} ({})",
                    first.reasonCode(), first.message(), e.toString());
            return false;
        }
    }

    private List<String> buildCallbackDownloadExtraHosts() {
        List<String> extraAllowedHosts = new ArrayList<>();
        try {
            URI onlyOfficeUri = URI.create(onlyOfficeUrl);
            if (onlyOfficeUri.getHost() != null) {
                addHostnameAndResolvedIps(onlyOfficeUri.getHost(), extraAllowedHosts);
            }
        } catch (Exception ignored) {
            // Ignore invalid onlyoffice.url here; merged allowlist may still be populated via callback hosts.
        }

        if (callbackAllowedHosts != null) {
            for (String h : callbackAllowedHosts) {
                if (h != null && !h.isBlank()) {
                    addHostnameAndResolvedIps(h.trim(), extraAllowedHosts);
                }
            }
        }
        return extraAllowedHosts;
    }

    private static boolean resolvedAddressesOverlap(String hostA, String hostB) throws UnknownHostException {
        Set<String> ipsB = resolvedIpStrings(hostB);
        for (InetAddress a : InetAddress.getAllByName(hostA)) {
            if (ipsB.contains(a.getHostAddress())) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> resolvedIpStrings(String host) throws UnknownHostException {
        Set<String> out = new HashSet<>();
        for (InetAddress a : InetAddress.getAllByName(host)) {
            out.add(a.getHostAddress());
        }
        return out;
    }

    private static String rewriteUriHost(URI downloadUri, String newHost) throws URISyntaxException {
        return new URI(
                downloadUri.getScheme(),
                downloadUri.getRawUserInfo(),
                newHost,
                downloadUri.getPort(),
                downloadUri.getRawPath(),
                downloadUri.getRawQuery(),
                downloadUri.getRawFragment()
        ).toASCIIString();
    }

    /**
     * Document Server often emits callback download URLs with a literal IP host even when the editor
     * was opened against a Docker DNS name (e.g. {@code onlyoffice}). The host allowlist must then
     * include those resolved addresses, while {@link OutboundUrlPolicy} still enforces private-range rules
     * unless {@code url-policy.allow-private-addresses} is enabled for local deployments.
     */
    private void addHostnameAndResolvedIps(String host, List<String> targets) {
        if (host == null || host.isBlank()) {
            return;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        if (!targets.contains(normalized)) {
            targets.add(normalized);
        }
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                String ip = addr.getHostAddress();
                if (!targets.contains(ip)) {
                    targets.add(ip);
                }
            }
        } catch (UnknownHostException e) {
            log.debug("Could not resolve OnlyOffice host '{}' for callback URL allowlist: {}", host, e.getMessage());
        }
    }

    private String extractOnlyOfficeToken(Map<String, Object> body, String authorizationHeader) {
        Object tokenInBody = body.get("token");
        if (tokenInBody instanceof String s && !s.isBlank()) {
            return s;
        }

        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }

        String value = authorizationHeader.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            return value.substring("Bearer ".length()).trim();
        }
        return value;
    }

    public boolean isValidOnlyOfficeJwt(String token) {
        try {
            Jwts.parser()
                    .verifyWith(onlyOfficeSigningKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isCallbackJwtRequired() {
        return callbackRequireJwt;
    }

}

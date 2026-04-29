package com.docgen.service;

import com.docgen.entity.Template;
import com.docgen.repository.TemplateRepository;
import com.docgen.security.url.OutboundUrlPolicy;
import com.docgen.security.url.UrlPolicyProperties;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OnlyOfficeServiceTest {

    @Mock
    private TemplateRepository templateRepository;

    @Mock
    private MinioClient minioClient;

    @Mock
    private CallbackDocumentDownloadHelper callbackDocumentDownloadHelper;

    private OnlyOfficeService service;

    @BeforeEach
    void setUp() throws Exception {
        UrlPolicyProperties urlPolicyProperties = new UrlPolicyProperties();
        urlPolicyProperties.setAllowPrivateAddresses(true);
        urlPolicyProperties.setAllowedHosts(List.of());
        urlPolicyProperties.setAllowedPorts(List.of(80, 443));
        OutboundUrlPolicy outboundUrlPolicy = new OutboundUrlPolicy(urlPolicyProperties);

        // Keys.hmacShaKeyFor requires a sufficiently long key.
        service = new OnlyOfficeService(templateRepository, minioClient, callbackDocumentDownloadHelper,
                outboundUrlPolicy, "0123456789abcdef0123456789abcdef");

        // @Value fields are not injected in plain unit tests.
        Field bucketField = OnlyOfficeService.class.getDeclaredField("bucketName");
        bucketField.setAccessible(true);
        bucketField.set(service, "docgen");

        Field onlyOfficeUrlField = OnlyOfficeService.class.getDeclaredField("onlyOfficeUrl");
        onlyOfficeUrlField.setAccessible(true);
        onlyOfficeUrlField.set(service, "http://127.0.0.1");

        Field allowedHostsField = OnlyOfficeService.class.getDeclaredField("callbackAllowedHosts");
        allowedHostsField.setAccessible(true);
        allowedHostsField.set(service, List.of());

        Field requireJwtField = OnlyOfficeService.class.getDeclaredField("callbackRequireJwt");
        requireJwtField.setAccessible(true);
        requireJwtField.set(service, true);
    }

    @Test
    void handleCallback_status1_doesNothing() {
        assertEquals(0, service.handleCallback(1L, Map.of("status", 1, "url", "http://onlyoffice/download")));

        verifyNoInteractions(templateRepository, minioClient, callbackDocumentDownloadHelper);
    }

    @Test
    void handleCallback_status4_doesNothing() {
        assertEquals(0, service.handleCallback(1L, Map.of("status", 4, "url", "http://onlyoffice/download")));

        verifyNoInteractions(templateRepository, minioClient, callbackDocumentDownloadHelper);
    }

    @Test
    void handleCallback_status2_withoutUrl_returnsEarly() {
        assertEquals(0, service.handleCallback(1L, Map.of("status", 2)));

        verifyNoInteractions(templateRepository, minioClient, callbackDocumentDownloadHelper);
    }

    @Test
    void handleCallback_status6_withBlankUrl_returnsEarly() {
        assertEquals(0, service.handleCallback(1L, Map.of("status", 6, "url", "   ")));

        verifyNoInteractions(templateRepository, minioClient, callbackDocumentDownloadHelper);
    }

    @Test
    void handleCallback_status2_withUrl_downloadsAndWritesToMinio() throws Exception {
        Template template = new Template();
        template.setId(1L);
        template.setTenantId(1L);
        template.setName("t");
        template.setTemplateFilePath("templates/1.docx");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        String jwt = service.generateEditorToken(Map.of("k", "v"));
        when(callbackDocumentDownloadHelper.downloadOnlyOfficeDocx(eq("http://127.0.0.1/download"), anyLong()))
                .thenReturn("docx-bytes".getBytes());

        assertEquals(0, service.handleCallback(1L, Map.of("status", 2, "url", "http://127.0.0.1/download", "token", jwt)));

        verify(templateRepository).findById(1L);
        verify(callbackDocumentDownloadHelper).downloadOnlyOfficeDocx(eq("http://127.0.0.1/download"), anyLong());
        verify(minioClient).putObject(any());
    }

    @Test
    void handleCallback_status6_withUrl_downloadsEmptyContent_doesNotWriteToMinio() throws Exception {
        Template template = new Template();
        template.setId(1L);
        template.setTenantId(1L);
        template.setName("t");
        template.setTemplateFilePath("templates/1.docx");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        String jwt = service.generateEditorToken(Map.of("k", "v"));
        when(callbackDocumentDownloadHelper.downloadOnlyOfficeDocx(eq("http://127.0.0.1/download"), anyLong()))
                .thenReturn(new byte[0]);

        assertEquals(1, service.handleCallback(1L, Map.of("status", 6, "url", "http://127.0.0.1/download", "token", jwt)));

        verify(templateRepository).findById(1L);
        verify(callbackDocumentDownloadHelper).downloadOnlyOfficeDocx(eq("http://127.0.0.1/download"), anyLong());
        verify(minioClient, never()).putObject(any());
    }

    @Test
    void handleCallback_status2_missingJwt_doesNotDownload() {
        assertEquals(1, service.handleCallback(1L, Map.of("status", 2, "url", "http://127.0.0.1/download")));

        verifyNoInteractions(templateRepository, minioClient, callbackDocumentDownloadHelper);
    }

    @Test
    void handleCallback_status2_invalidUrlHost_doesNotDownload() {
        String jwt = service.generateEditorToken(Map.of("k", "v"));
        assertEquals(1, service.handleCallback(1L, Map.of("status", 2, "url", "http://evil.local/download", "token", jwt)));

        verifyNoInteractions(templateRepository, minioClient, callbackDocumentDownloadHelper);
    }

    @Test
    void handleCallback_status2_missingTemplate_returnsError() {
        when(templateRepository.findById(99L)).thenReturn(Optional.empty());
        String jwt = service.generateEditorToken(Map.of("k", "v"));

        assertEquals(1, service.handleCallback(99L, Map.of("status", 2, "url", "http://127.0.0.1/download", "token", jwt)));

        verify(templateRepository).findById(99L);
        verifyNoInteractions(minioClient, callbackDocumentDownloadHelper);
    }

    /**
     * Document Server may return a literal loopback IP in the callback URL while {@code onlyoffice.url}
     * uses {@code localhost}; resolved addresses must be allowlisted together with the hostname.
     */
    @Test
    void isAllowedCallbackDownloadUrl_acceptsIpLiteralWhenHostnameResolvesToThatIp() throws Exception {
        Field onlyOfficeUrlField = OnlyOfficeService.class.getDeclaredField("onlyOfficeUrl");
        onlyOfficeUrlField.setAccessible(true);
        onlyOfficeUrlField.set(service, "http://localhost");

        assertTrue(service.isAllowedCallbackDownloadUrl("http://127.0.0.1/cache/files/save.docx"));
    }

    /**
     * When Document Server uses an alternate hostname that resolves to the same addresses as
     * {@code onlyoffice.url}, the same-instance fallback must accept the callback download URL.
     */
    @Test
    void isAllowedCallbackDownloadUrl_acceptsAlternateHostnameViaSameAddressFallback() throws Exception {
        Field onlyOfficeUrlField = OnlyOfficeService.class.getDeclaredField("onlyOfficeUrl");
        onlyOfficeUrlField.setAccessible(true);
        onlyOfficeUrlField.set(service, "http://127.0.0.1");

        assertTrue(service.isAllowedCallbackDownloadUrl("http://localhost/cache/files/save.docx"));
    }

    @Test
    void resolveCallbackDownloadFetchUrl_rewritesLoopbackToConfiguredDocumentServerHostname() throws Exception {
        Field onlyOfficeUrlField = OnlyOfficeService.class.getDeclaredField("onlyOfficeUrl");
        onlyOfficeUrlField.setAccessible(true);
        onlyOfficeUrlField.set(service, "http://onlyoffice");

        assertEquals(
                "http://onlyoffice/cache/key.docx",
                service.resolveCallbackDownloadFetchUrl("http://localhost/cache/key.docx"));
    }
}


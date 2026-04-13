package com.docgen.service;

import com.docgen.dto.*;
import com.docgen.entity.WebhookConfig;
import com.docgen.entity.WebhookLog;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.WebhookConfigRepository;
import com.docgen.repository.WebhookLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    private WebhookConfigRepository configRepository;

    @Mock
    private WebhookLogRepository logRepository;

    @Mock
    private RestTemplate restTemplate;

    private WebhookService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new WebhookService(configRepository, logRepository, restTemplate, objectMapper);
    }

    // ── createWebhook ──

    @Test
    void createWebhook_success() {
        when(configRepository.save(any(WebhookConfig.class))).thenAnswer(inv -> {
            WebhookConfig c = inv.getArgument(0);
            c.setId(1L);
            c.setCreatedAt(Instant.now());
            c.setUpdatedAt(Instant.now());
            return c;
        });

        CreateWebhookRequest request = new CreateWebhookRequest();
        request.setUrl("https://example.com/hook");
        request.setSecret("my-secret");
        request.setPayloadTemplate("{\"event\": \"{{event}}\"}");

        WebhookConfigDTO result = service.createWebhook(100L, request);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(100L, result.getTemplateId());
        assertEquals("https://example.com/hook", result.getUrl());
        assertTrue(result.isEnabled());
        assertEquals("{\"event\": \"{{event}}\"}", result.getPayloadTemplate());

        ArgumentCaptor<WebhookConfig> captor = ArgumentCaptor.forClass(WebhookConfig.class);
        verify(configRepository).save(captor.capture());
        WebhookConfig saved = captor.getValue();
        assertEquals(100L, saved.getTemplateId());
        assertEquals("my-secret", saved.getSecret());
    }

    // ── listWebhooks ──

    @Test
    void listWebhooks_success() {
        WebhookConfig c1 = createSampleConfig(1L, 100L);
        WebhookConfig c2 = createSampleConfig(2L, 100L);
        c2.setUrl("https://other.com/hook");
        when(configRepository.findByTemplateIdOrderByCreatedAtDesc(100L))
                .thenReturn(List.of(c1, c2));

        List<WebhookConfigDTO> result = service.listWebhooks(100L);

        assertEquals(2, result.size());
        assertEquals("https://example.com/hook", result.get(0).getUrl());
        assertEquals("https://other.com/hook", result.get(1).getUrl());
    }

    // ── updateWebhook ──

    @Test
    void updateWebhook_success() {
        WebhookConfig config = createSampleConfig(1L, 100L);
        when(configRepository.findById(1L)).thenReturn(Optional.of(config));
        when(configRepository.save(any(WebhookConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateWebhookRequest request = new UpdateWebhookRequest();
        request.setUrl("https://new-url.com/hook");
        request.setEnabled(false);

        WebhookConfigDTO result = service.updateWebhook(1L, request);

        assertEquals("https://new-url.com/hook", result.getUrl());
        assertFalse(result.isEnabled());
    }

    @Test
    void updateWebhook_partialUpdate_onlyUrl() {
        WebhookConfig config = createSampleConfig(1L, 100L);
        when(configRepository.findById(1L)).thenReturn(Optional.of(config));
        when(configRepository.save(any(WebhookConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateWebhookRequest request = new UpdateWebhookRequest();
        request.setUrl("https://updated.com/hook");

        WebhookConfigDTO result = service.updateWebhook(1L, request);

        assertEquals("https://updated.com/hook", result.getUrl());
        assertTrue(result.isEnabled()); // unchanged
    }

    @Test
    void updateWebhook_notFound_throws() {
        when(configRepository.findById(999L)).thenReturn(Optional.empty());
        UpdateWebhookRequest request = new UpdateWebhookRequest();
        assertThrows(ResourceNotFoundException.class, () -> service.updateWebhook(999L, request));
    }

    // ── deleteWebhook ──

    @Test
    void deleteWebhook_success() {
        WebhookConfig config = createSampleConfig(1L, 100L);
        when(configRepository.findById(1L)).thenReturn(Optional.of(config));

        service.deleteWebhook(1L);

        verify(configRepository).delete(config);
    }

    @Test
    void deleteWebhook_notFound_throws() {
        when(configRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.deleteWebhook(999L));
    }

    // ── getWebhookLogs ──

    @Test
    void getWebhookLogs_success() {
        WebhookConfig config = createSampleConfig(1L, 100L);
        when(configRepository.findById(1L)).thenReturn(Optional.of(config));

        WebhookLog log1 = createSampleLog(10L, 1L, "DOCUMENT_GENERATED", 200);
        WebhookLog log2 = createSampleLog(11L, 1L, "DOCUMENT_FAILED", null);
        Pageable pageable = PageRequest.of(0, 10);
        when(logRepository.findByWebhookConfigIdOrderBySentAtDesc(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(log1, log2)));

        Page<WebhookLogDTO> result = service.getWebhookLogs(1L, pageable);

        assertEquals(2, result.getTotalElements());
        assertEquals("DOCUMENT_GENERATED", result.getContent().get(0).getEventType());
        assertEquals(200, result.getContent().get(0).getResponseStatus());
    }

    @Test
    void getWebhookLogs_webhookNotFound_throws() {
        when(configRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.getWebhookLogs(999L, PageRequest.of(0, 10)));
    }

    // ── HMAC-SHA256 signature ──

    @Test
    void computeHmacSha256_producesValidSignature() {
        String payload = "{\"event\":\"test\"}";
        String secret = "test-secret";

        String signature = service.computeHmacSha256(payload, secret);

        assertNotNull(signature);
        assertTrue(signature.startsWith("sha256="));
        // Signature should be deterministic
        assertEquals(signature, service.computeHmacSha256(payload, secret));
    }

    @Test
    void computeHmacSha256_differentSecrets_produceDifferentSignatures() {
        String payload = "{\"event\":\"test\"}";

        String sig1 = service.computeHmacSha256(payload, "secret-1");
        String sig2 = service.computeHmacSha256(payload, "secret-2");

        assertNotEquals(sig1, sig2);
    }

    // ── sendWithRetry ──

    @Test
    void sendWithRetry_successOnFirstAttempt() {
        WebhookConfig config = createSampleConfig(1L, 100L);
        Map<String, Object> payload = Map.of("event", "DOCUMENT_GENERATED", "documentId", "doc-123");

        ResponseEntity<String> response = new ResponseEntity<>("OK", HttpStatus.OK);
        when(restTemplate.exchange(eq(config.getUrl()), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);
        when(logRepository.save(any(WebhookLog.class))).thenAnswer(inv -> inv.getArgument(0));

        service.sendWithRetry(config, "DOCUMENT_GENERATED", payload);

        // Should only call once (no retries needed)
        verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
        // Should record one log entry
        verify(logRepository, times(1)).save(any(WebhookLog.class));
    }

    @Test
    void sendWithRetry_includesSignatureHeader() {
        WebhookConfig config = createSampleConfig(1L, 100L);
        Map<String, Object> payload = Map.of("event", "test");

        ResponseEntity<String> response = new ResponseEntity<>("OK", HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);
        when(logRepository.save(any(WebhookLog.class))).thenAnswer(inv -> inv.getArgument(0));

        service.sendWithRetry(config, "DOCUMENT_GENERATED", payload);

        ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));

        HttpEntity<String> sentEntity = entityCaptor.getValue();
        String signatureHeader = sentEntity.getHeaders().getFirst("X-Webhook-Signature");
        assertNotNull(signatureHeader);
        assertTrue(signatureHeader.startsWith("sha256="));
    }

    @Test
    void sendWithRetry_retriesOnFailure_maxThreeAttempts() {
        WebhookConfig config = createSampleConfig(1L, 100L);
        Map<String, Object> payload = Map.of("event", "test");

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("Connection refused"));
        when(logRepository.save(any(WebhookLog.class))).thenAnswer(inv -> inv.getArgument(0));

        service.sendWithRetry(config, "DOCUMENT_FAILED", payload);

        // Should attempt 3 times
        verify(restTemplate, times(3)).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
        // Should record failure log after all retries exhausted
        verify(logRepository, atLeastOnce()).save(any(WebhookLog.class));
    }

    @Test
    void sendWithRetry_recordsLogOnSuccess() {
        WebhookConfig config = createSampleConfig(1L, 100L);
        Map<String, Object> payload = Map.of("status", "completed");

        ResponseEntity<String> response = new ResponseEntity<>("{\"ok\":true}", HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);
        when(logRepository.save(any(WebhookLog.class))).thenAnswer(inv -> inv.getArgument(0));

        service.sendWithRetry(config, "BATCH_COMPLETED", payload);

        ArgumentCaptor<WebhookLog> logCaptor = ArgumentCaptor.forClass(WebhookLog.class);
        verify(logRepository).save(logCaptor.capture());
        WebhookLog savedLog = logCaptor.getValue();
        assertEquals(1L, savedLog.getWebhookConfigId());
        assertEquals("BATCH_COMPLETED", savedLog.getEventType());
        assertEquals(200, savedLog.getResponseStatus());
        assertEquals("{\"ok\":true}", savedLog.getResponseBody());
    }

    // ── sendNotifications ──

    @Test
    void sendNotifications_sendsToAllEnabledWebhooks() {
        WebhookConfig c1 = createSampleConfig(1L, 100L);
        WebhookConfig c2 = createSampleConfig(2L, 100L);
        c2.setUrl("https://other.com/hook");
        when(configRepository.findByTemplateIdAndEnabledTrue(100L))
                .thenReturn(List.of(c1, c2));

        ResponseEntity<String> response = new ResponseEntity<>("OK", HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);
        when(logRepository.save(any(WebhookLog.class))).thenAnswer(inv -> inv.getArgument(0));

        service.sendNotifications(100L, "DOCUMENT_GENERATED", Map.of("docId", "123"));

        // Should call both webhooks
        verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    // ── Helpers ──

    private WebhookConfig createSampleConfig(Long id, Long templateId) {
        WebhookConfig config = new WebhookConfig();
        config.setId(id);
        config.setTemplateId(templateId);
        config.setUrl("https://example.com/hook");
        config.setSecret("test-secret-key");
        config.setPayloadTemplate(null);
        config.setEnabled(true);
        config.setCreatedAt(Instant.now());
        config.setUpdatedAt(Instant.now());
        return config;
    }

    private WebhookLog createSampleLog(Long id, Long configId, String eventType, Integer status) {
        WebhookLog log = new WebhookLog();
        log.setId(id);
        log.setWebhookConfigId(configId);
        log.setEventType(eventType);
        log.setPayload("{\"event\":\"" + eventType + "\"}");
        log.setResponseStatus(status);
        log.setResponseBody(status != null ? "OK" : "Connection refused");
        log.setSentAt(Instant.now());
        return log;
    }
}

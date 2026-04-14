package com.docgen.service;

import com.docgen.dto.*;
import com.docgen.entity.WebhookConfig;
import com.docgen.entity.WebhookLog;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.WebhookConfigRepository;
import com.docgen.repository.WebhookLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Service for managing webhook configurations and sending webhook notifications.
 * <p>
 * Notifications are sent as HTTP POST requests with an HMAC-SHA256 signature header.
 * Failed deliveries are retried with exponential backoff (max 3 attempts: 1s, 2s, 4s).
 * All delivery attempts are recorded in the webhook_logs table.
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_HEADER = "X-Webhook-Signature";
    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS_MS = {1000L, 2000L, 4000L};

    private final WebhookConfigRepository configRepository;
    private final WebhookLogRepository logRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public WebhookService(WebhookConfigRepository configRepository,
                          WebhookLogRepository logRepository,
                          RestTemplate restTemplate,
                          ObjectMapper objectMapper) {
        this.configRepository = configRepository;
        this.logRepository = logRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    // ── CRUD operations ──

    @Transactional
    public WebhookConfigDTO createWebhook(Long templateId, CreateWebhookRequest request) {
        WebhookConfig config = new WebhookConfig();
        config.setTemplateId(templateId);
        config.setUrl(request.getUrl());
        config.setSecret(request.getSecret());
        config.setPayloadTemplate(request.getPayloadTemplate());
        config.setEnabled(true);
        config = configRepository.save(config);
        return toDTO(config);
    }

    @Transactional(readOnly = true)
    public List<WebhookConfigDTO> listWebhooks(Long templateId) {
        return configRepository.findByTemplateIdOrderByCreatedAtDesc(templateId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public WebhookConfigDTO updateWebhook(Long webhookId, UpdateWebhookRequest request) {
        WebhookConfig config = findConfigOrThrow(webhookId);
        if (request.getUrl() != null) {
            config.setUrl(request.getUrl());
        }
        if (request.getSecret() != null) {
            config.setSecret(request.getSecret());
        }
        if (request.getPayloadTemplate() != null) {
            config.setPayloadTemplate(request.getPayloadTemplate());
        }
        if (request.getEnabled() != null) {
            config.setEnabled(request.getEnabled());
        }
        config = configRepository.save(config);
        return toDTO(config);
    }

    @Transactional
    public void deleteWebhook(Long webhookId) {
        WebhookConfig config = findConfigOrThrow(webhookId);
        configRepository.delete(config);
    }

    @Transactional(readOnly = true)
    public Page<WebhookLogDTO> getWebhookLogs(Long webhookId, Pageable pageable) {
        // Verify webhook exists
        findConfigOrThrow(webhookId);
        return logRepository.findByWebhookConfigIdOrderBySentAtDesc(webhookId, pageable)
                .map(this::toLogDTO);
    }

    // ── Notification sending ──

    /**
     * Send notifications to all enabled webhooks for the given template.
     * Called when a document generation completes or fails, or when a batch task completes.
     */
    @Async
    public void sendNotifications(Long templateId, String eventType, Map<String, Object> payload) {
        List<WebhookConfig> configs = configRepository.findByTemplateIdAndEnabledTrue(templateId);
        for (WebhookConfig config : configs) {
            sendWithRetry(config, eventType, payload);
        }
    }

    /**
     * Send notifications for composite template document generation events.
     * Enriches the payload with per-segment render status and timing information.
     *
     * @param templateId   the composite template ID
     * @param eventType    the event type (e.g. "GENERATE_COMPLETED", "GENERATE_FAILED")
     * @param basePayload  the base payload with standard generation info
     * @param segmentStats per-segment render statistics from the assembly result
     */
    @Async
    public void sendCompositeNotifications(Long templateId, String eventType,
                                           Map<String, Object> basePayload,
                                           List<SegmentRenderStat> segmentStats) {
        Map<String, Object> enrichedPayload = new LinkedHashMap<>(basePayload);
        enrichedPayload.put("templateType", "COMPOSITE");

        if (segmentStats != null && !segmentStats.isEmpty()) {
            List<Map<String, Object>> segmentDetails = new ArrayList<>();
            for (SegmentRenderStat stat : segmentStats) {
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("segmentName", stat.getSegmentName());
                detail.put("status", stat.isSuccess() ? "SUCCESS" : "FAILED");
                detail.put("renderTimeMs", stat.getRenderTimeMs());
                if (stat.getErrorMessage() != null) {
                    detail.put("errorMessage", stat.getErrorMessage());
                }
                segmentDetails.add(detail);
            }
            enrichedPayload.put("segmentResults", segmentDetails);

            long totalRenderTime = segmentStats.stream()
                    .mapToLong(SegmentRenderStat::getRenderTimeMs)
                    .sum();
            enrichedPayload.put("totalSegmentRenderTimeMs", totalRenderTime);
        }

        List<WebhookConfig> configs = configRepository.findByTemplateIdAndEnabledTrue(templateId);
        for (WebhookConfig config : configs) {
            sendWithRetry(config, eventType, enrichedPayload);
        }
    }

    /**
     * Send a single webhook notification with exponential backoff retry.
     */
    void sendWithRetry(WebhookConfig config, String eventType, Map<String, Object> payload) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize webhook payload for config {}: {}", config.getId(), e.getMessage());
            recordLog(config.getId(), eventType, "{}", null, "Payload serialization failed: " + e.getMessage());
            return;
        }

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    Thread.sleep(RETRY_DELAYS_MS[attempt - 1]);
                }

                String signature = computeHmacSha256(payloadJson, config.getSecret());

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set(SIGNATURE_HEADER, signature);

                HttpEntity<String> entity = new HttpEntity<>(payloadJson, headers);
                ResponseEntity<String> response = restTemplate.exchange(
                        config.getUrl(), HttpMethod.POST, entity, String.class);

                int statusCode = response.getStatusCode().value();
                String body = response.getBody();
                recordLog(config.getId(), eventType, payloadJson, statusCode, body);

                if (response.getStatusCode().is2xxSuccessful()) {
                    log.debug("Webhook sent successfully to {} (config {})", config.getUrl(), config.getId());
                    return;
                }
                log.warn("Webhook to {} returned non-2xx status {} (attempt {}/{})",
                        config.getUrl(), statusCode, attempt + 1, MAX_RETRIES);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                recordLog(config.getId(), eventType, payloadJson, null, "Interrupted during retry");
                return;
            } catch (Exception e) {
                log.warn("Webhook delivery to {} failed (attempt {}/{}): {}",
                        config.getUrl(), attempt + 1, MAX_RETRIES, e.getMessage());
                if (attempt == MAX_RETRIES - 1) {
                    recordLog(config.getId(), eventType, payloadJson, null, "Failed after " + MAX_RETRIES + " attempts: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Compute HMAC-SHA256 signature for the given payload using the webhook secret.
     */
    String computeHmacSha256(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC-SHA256 signature", e);
        }
    }

    // ── Private helpers ──

    private void recordLog(Long configId, String eventType, String payload,
                           Integer responseStatus, String responseBody) {
        try {
            WebhookLog logEntry = new WebhookLog();
            logEntry.setWebhookConfigId(configId);
            logEntry.setEventType(eventType);
            logEntry.setPayload(payload);
            logEntry.setResponseStatus(responseStatus);
            logEntry.setResponseBody(responseBody);
            logRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to record webhook log for config {}: {}", configId, e.getMessage());
        }
    }

    private WebhookConfig findConfigOrThrow(Long webhookId) {
        return configRepository.findById(webhookId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.WEBHOOK_NOT_FOUND,
                        "Webhook configuration not found: " + webhookId));
    }

    private WebhookConfigDTO toDTO(WebhookConfig entity) {
        WebhookConfigDTO dto = new WebhookConfigDTO();
        dto.setId(entity.getId());
        dto.setTemplateId(entity.getTemplateId());
        dto.setUrl(entity.getUrl());
        dto.setPayloadTemplate(entity.getPayloadTemplate());
        dto.setEnabled(entity.isEnabled());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private WebhookLogDTO toLogDTO(WebhookLog entity) {
        WebhookLogDTO dto = new WebhookLogDTO();
        dto.setId(entity.getId());
        dto.setWebhookConfigId(entity.getWebhookConfigId());
        dto.setEventType(entity.getEventType());
        dto.setPayload(entity.getPayload());
        dto.setResponseStatus(entity.getResponseStatus());
        dto.setResponseBody(entity.getResponseBody());
        dto.setSentAt(entity.getSentAt());
        return dto;
    }
}

package com.docgen.service;

import com.docgen.dto.CreateApiKeyRequest;
import com.docgen.entity.ApiKey;
import com.docgen.entity.Template;
import com.docgen.entity.TemplateState;
import com.docgen.repository.ApiKeyRepository;
import com.docgen.repository.TemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Service that automatically activates a template after all reviews are approved.
 * Performs a two-step state transition (PENDING_REVIEW → REVIEWED → ACTIVE)
 * and auto-creates an API Key if none exists for the tenant.
 *
 * All failures are caught and logged at WARN level — this service never throws
 * exceptions to the caller, so the review approval flow is never blocked.
 */
@Service
public class AutoActivationService {

    private static final Logger log = LoggerFactory.getLogger(AutoActivationService.class);

    private final TemplateStateMachineService stateMachineService;
    private final ApiKeyService apiKeyService;
    private final ApiKeyRepository apiKeyRepository;
    private final TemplateRepository templateRepository;

    public AutoActivationService(TemplateStateMachineService stateMachineService,
                                 ApiKeyService apiKeyService,
                                 ApiKeyRepository apiKeyRepository,
                                 TemplateRepository templateRepository) {
        this.stateMachineService = stateMachineService;
        this.apiKeyService = apiKeyService;
        this.apiKeyRepository = apiKeyRepository;
        this.templateRepository = templateRepository;
    }

    /**
     * Attempt to auto-activate a template after all reviews pass.
     * <p>
     * Step 1: PENDING_REVIEW → REVIEWED<br>
     * Step 2: REVIEWED → ACTIVE<br>
     * Step 3: Create an API Key for the tenant if none exists.
     * <p>
     * Each step catches exceptions independently so a failure in one step
     * does not prevent the caller (review approval) from completing.
     *
     * @param templateId the template to activate
     */
    @Transactional
    public void tryAutoActivate(Long templateId) {
        // Step 1: PENDING_REVIEW → REVIEWED
        try {
            stateMachineService.transition(templateId, TemplateState.REVIEWED);
            log.info("Auto-activation Step 1 succeeded: templateId={} transitioned to REVIEWED", templateId);
        } catch (Exception e) {
            log.warn("Auto-activation Step 1 failed for templateId={}: {}", templateId, e.getMessage());
            return;
        }

        // Step 2: REVIEWED → ACTIVE
        try {
            stateMachineService.transition(templateId, TemplateState.ACTIVE);
            log.info("Auto-activation Step 2 succeeded: templateId={} transitioned to ACTIVE", templateId);
        } catch (Exception e) {
            log.warn("Auto-activation Step 2 failed for templateId={}: {}", templateId, e.getMessage());
            return;
        }

        // Step 3: Ensure an active API Key exists for the tenant
        try {
            Template template = templateRepository.findById(templateId).orElse(null);
            if (template == null) {
                log.warn("Auto-activation Step 3: template not found for templateId={}", templateId);
                return;
            }

            Long tenantId = template.getTenantId();
            List<ApiKey> existingKeys = apiKeyRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
            boolean hasActiveKey = existingKeys.stream()
                    .anyMatch(k -> k.isEnabled()
                            && (k.getExpiresAt() == null || k.getExpiresAt().isAfter(Instant.now())));

            if (!hasActiveKey) {
                String sanitizedName = template.getName().replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5_-]", "_");
                String keyName = "auto-" + sanitizedName + "-" + Instant.now().toEpochMilli();

                CreateApiKeyRequest request = new CreateApiKeyRequest();
                request.setName(keyName);

                apiKeyService.createApiKey(tenantId, template.getCreatedBy(), request);
                log.info("Auto-activation Step 3: created API Key '{}' for tenantId={}", keyName, tenantId);
            } else {
                log.info("Auto-activation Step 3: active API Key already exists for tenantId={}", tenantId);
            }
        } catch (Exception e) {
            log.warn("Auto-activation Step 3 failed for templateId={}: {}", templateId, e.getMessage());
        }
    }
}

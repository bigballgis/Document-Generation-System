package com.docgen.property;

import com.docgen.dto.ApiKeyDTO;
import com.docgen.dto.CreateApiKeyRequest;
import com.docgen.entity.ApiKey;
import com.docgen.entity.Template;
import com.docgen.entity.TemplateState;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.ApiKeyRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.service.ApiKeyService;
import com.docgen.service.AutoActivationService;
import com.docgen.service.TemplateStateMachineService;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for AutoActivationService.
 *
 * <p><b>Validates: Requirements 7.2, 7.3, 7.4, 7.5, 7.6</b></p>
 */
@Tag("Feature: workspace-test-publish, Property 3 & 4: AutoActivationService")
class AutoActivationPropertyTest {

    // ── Helper: create a Template entity ──

    private Template createTemplate(Long id, Long tenantId, String name) {
        Template t = new Template();
        t.setId(id);
        t.setTenantId(tenantId);
        t.setName(name);
        t.setCreatedBy(1L);
        t.setTemplateFilePath("templates/test.docx");
        t.setStatus("PENDING_REVIEW");
        return t;
    }

    // ── Helper: create an ApiKey entity ──

    private ApiKey createApiKey(boolean enabled, Instant expiresAt) {
        ApiKey key = new ApiKey();
        key.setEnabled(enabled);
        key.setExpiresAt(expiresAt);
        return key;
    }

    /**
     * Property 3: AutoActivationService two-step transition.
     *
     * For any templateId, when both transitions succeed, the service calls
     * transition twice (REVIEWED then ACTIVE) and does not throw.
     * When Step 1 fails, Step 2 is never called and no exception is thrown.
     * When Step 2 fails, Step 1 was called and no exception is thrown.
     */
    @Property(tries = 100)
    void twoStepTransitionProperty(
            @ForAll("templateIds") Long templateId,
            @ForAll("transitionScenarios") TransitionScenario scenario
    ) {
        // Arrange
        TemplateStateMachineService stateMachineService = mock(TemplateStateMachineService.class);
        ApiKeyService apiKeyService = mock(ApiKeyService.class);
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        TemplateRepository templateRepository = mock(TemplateRepository.class);

        AutoActivationService service = new AutoActivationService(
                stateMachineService, apiKeyService, apiKeyRepository, templateRepository);

        Template template = createTemplate(templateId, 10L, "Template-" + templateId);

        switch (scenario) {
            case BOTH_SUCCEED:
                when(stateMachineService.transition(templateId, TemplateState.REVIEWED)).thenReturn(template);
                when(stateMachineService.transition(templateId, TemplateState.ACTIVE)).thenReturn(template);
                when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
                // Provide an active key so Step 3 doesn't create one (isolate transition testing)
                ApiKey activeKey = createApiKey(true, null);
                when(apiKeyRepository.findByTenantIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(activeKey));
                break;

            case STEP1_FAILS:
                when(stateMachineService.transition(templateId, TemplateState.REVIEWED))
                        .thenThrow(new BusinessException(ErrorCode.TEMPLATE_INVALID_STATE_TRANSITION,
                                "Invalid transition", HttpStatus.BAD_REQUEST));
                break;

            case STEP2_FAILS:
                when(stateMachineService.transition(templateId, TemplateState.REVIEWED)).thenReturn(template);
                when(stateMachineService.transition(templateId, TemplateState.ACTIVE))
                        .thenThrow(new BusinessException(ErrorCode.TEMPLATE_INVALID_STATE_TRANSITION,
                                "Invalid transition", HttpStatus.BAD_REQUEST));
                break;
        }

        // Act: should never throw
        assertDoesNotThrow(() -> service.tryAutoActivate(templateId));

        // Assert
        switch (scenario) {
            case BOTH_SUCCEED:
                verify(stateMachineService).transition(templateId, TemplateState.REVIEWED);
                verify(stateMachineService).transition(templateId, TemplateState.ACTIVE);
                break;

            case STEP1_FAILS:
                verify(stateMachineService).transition(templateId, TemplateState.REVIEWED);
                verify(stateMachineService, never()).transition(templateId, TemplateState.ACTIVE);
                verify(apiKeyService, never()).createApiKey(anyLong(), anyLong(), any());
                break;

            case STEP2_FAILS:
                verify(stateMachineService).transition(templateId, TemplateState.REVIEWED);
                verify(stateMachineService).transition(templateId, TemplateState.ACTIVE);
                verify(apiKeyService, never()).createApiKey(anyLong(), anyLong(), any());
                break;
        }
    }

    /**
     * Property 4: AutoActivationService API Key auto-creation.
     *
     * When both transitions succeed and the tenant has no active key,
     * exactly one API Key is created with name matching "auto-*-*".
     * When an active key exists, no new key is created.
     * When key creation fails, no exception is thrown.
     */
    @Property(tries = 100)
    void apiKeyAutoCreationProperty(
            @ForAll("templateIds") Long templateId,
            @ForAll("tenantIds") Long tenantId,
            @ForAll("existingKeyLists") List<ApiKeyConfig> existingKeyConfigs,
            @ForAll("apiKeyCreationOutcomes") boolean apiKeyCreationSucceeds
    ) {
        // Arrange
        TemplateStateMachineService stateMachineService = mock(TemplateStateMachineService.class);
        ApiKeyService apiKeyService = mock(ApiKeyService.class);
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        TemplateRepository templateRepository = mock(TemplateRepository.class);

        AutoActivationService service = new AutoActivationService(
                stateMachineService, apiKeyService, apiKeyRepository, templateRepository);

        Template template = createTemplate(templateId, tenantId, "TestTemplate");

        // Both transitions succeed
        when(stateMachineService.transition(templateId, TemplateState.REVIEWED)).thenReturn(template);
        when(stateMachineService.transition(templateId, TemplateState.ACTIVE)).thenReturn(template);
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));

        // Build existing keys
        List<ApiKey> existingKeys = new ArrayList<>();
        boolean hasActiveKey = false;
        for (ApiKeyConfig cfg : existingKeyConfigs) {
            ApiKey key = createApiKey(cfg.enabled, cfg.expiresAt);
            existingKeys.add(key);
            if (cfg.enabled && (cfg.expiresAt == null || cfg.expiresAt.isAfter(Instant.now()))) {
                hasActiveKey = true;
            }
        }
        when(apiKeyRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(existingKeys);

        if (!hasActiveKey) {
            if (apiKeyCreationSucceeds) {
                when(apiKeyService.createApiKey(eq(tenantId), eq(1L), any(CreateApiKeyRequest.class)))
                        .thenReturn(new ApiKeyDTO());
            } else {
                when(apiKeyService.createApiKey(eq(tenantId), eq(1L), any(CreateApiKeyRequest.class)))
                        .thenThrow(new RuntimeException("DB error"));
            }
        }

        // Act: should never throw
        assertDoesNotThrow(() -> service.tryAutoActivate(templateId));

        // Assert
        if (hasActiveKey) {
            // No new key should be created
            verify(apiKeyService, never()).createApiKey(anyLong(), anyLong(), any());
        } else {
            // Key creation should be attempted exactly once
            ArgumentCaptor<CreateApiKeyRequest> captor = ArgumentCaptor.forClass(CreateApiKeyRequest.class);
            verify(apiKeyService, times(1)).createApiKey(eq(tenantId), eq(1L), captor.capture());
            String keyName = captor.getValue().getName();
            assertTrue(keyName.matches("auto-.*-\\d+"),
                    "Key name should match auto-{name}-{timestamp} pattern: " + keyName);
        }
    }

    // ── Generators ──

    @Provide
    Arbitrary<Long> templateIds() {
        return Arbitraries.longs().between(1L, 10000L);
    }

    @Provide
    Arbitrary<Long> tenantIds() {
        return Arbitraries.longs().between(1L, 1000L);
    }

    @Provide
    Arbitrary<TransitionScenario> transitionScenarios() {
        return Arbitraries.of(TransitionScenario.values());
    }

    @Provide
    Arbitrary<Boolean> apiKeyCreationOutcomes() {
        return Arbitraries.of(true, false);
    }

    @Provide
    Arbitrary<List<ApiKeyConfig>> existingKeyLists() {
        Arbitrary<ApiKeyConfig> singleKey = Combinators.combine(
                Arbitraries.of(true, false), // enabled
                Arbitraries.of(
                        null,                                          // no expiry
                        Instant.now().plusSeconds(86400),               // future (active)
                        Instant.now().minusSeconds(86400)              // past (expired)
                )
        ).as(ApiKeyConfig::new);

        return singleKey.list().ofMinSize(0).ofMaxSize(5);
    }

    // ── Helper types ──

    enum TransitionScenario {
        BOTH_SUCCEED,
        STEP1_FAILS,
        STEP2_FAILS
    }

    static class ApiKeyConfig {
        final boolean enabled;
        final Instant expiresAt;

        ApiKeyConfig(boolean enabled, Instant expiresAt) {
            this.enabled = enabled;
            this.expiresAt = expiresAt;
        }

        @Override
        public String toString() {
            return "ApiKeyConfig{enabled=" + enabled + ", expiresAt=" + expiresAt + "}";
        }
    }
}

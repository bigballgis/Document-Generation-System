package com.docgen.service;

import com.docgen.dto.ApiKeyDTO;
import com.docgen.dto.CreateApiKeyRequest;
import com.docgen.entity.ApiKey;
import com.docgen.entity.Template;
import com.docgen.entity.TemplateState;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.ApiKeyRepository;
import com.docgen.repository.TemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoActivationServiceTest {

    @Mock
    private TemplateStateMachineService stateMachineService;

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private TemplateRepository templateRepository;

    private AutoActivationService autoActivationService;

    @BeforeEach
    void setUp() {
        autoActivationService = new AutoActivationService(
                stateMachineService, apiKeyService, apiKeyRepository, templateRepository);
    }


    @Test
    void tryAutoActivate_successfulTransitions_noActiveKey_createsApiKey() {
        Template template = createTemplate(1L, 10L, "Invoice Template");
        when(stateMachineService.transition(1L, TemplateState.REVIEWED)).thenReturn(template);
        when(stateMachineService.transition(1L, TemplateState.ACTIVE)).thenReturn(template);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(apiKeyRepository.findByTenantIdOrderByCreatedAtDesc(10L)).thenReturn(Collections.emptyList());
        when(apiKeyService.createApiKey(eq(10L), eq(1L), any(CreateApiKeyRequest.class)))
                .thenReturn(new ApiKeyDTO());

        autoActivationService.tryAutoActivate(1L);

        verify(stateMachineService).transition(1L, TemplateState.REVIEWED);
        verify(stateMachineService).transition(1L, TemplateState.ACTIVE);

        ArgumentCaptor<CreateApiKeyRequest> captor = ArgumentCaptor.forClass(CreateApiKeyRequest.class);
        verify(apiKeyService).createApiKey(eq(10L), eq(1L), captor.capture());
        String keyName = captor.getValue().getName();
        assertTrue(keyName.startsWith("auto-"), "Key name should start with 'auto-': " + keyName);
        assertTrue(keyName.contains("Invoice"), "Key name should contain sanitized template name: " + keyName);
    }


    @Test
    void tryAutoActivate_successfulTransitions_activeKeyExists_doesNotCreateKey() {
        Template template = createTemplate(1L, 10L, "Invoice Template");
        when(stateMachineService.transition(1L, TemplateState.REVIEWED)).thenReturn(template);
        when(stateMachineService.transition(1L, TemplateState.ACTIVE)).thenReturn(template);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        ApiKey activeKey = new ApiKey();
        activeKey.setEnabled(true);
        activeKey.setExpiresAt(null); // no expiry = active
        when(apiKeyRepository.findByTenantIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(activeKey));

        autoActivationService.tryAutoActivate(1L);

        verify(stateMachineService).transition(1L, TemplateState.REVIEWED);
        verify(stateMachineService).transition(1L, TemplateState.ACTIVE);
        verify(apiKeyService, never()).createApiKey(anyLong(), anyLong(), any());
    }


    @Test
    void tryAutoActivate_step1Fails_doesNotCallStep2_noException() {
        when(stateMachineService.transition(1L, TemplateState.REVIEWED))
                .thenThrow(new BusinessException(ErrorCode.TEMPLATE_INVALID_STATE_TRANSITION,
                        "Invalid transition", HttpStatus.BAD_REQUEST));

        assertDoesNotThrow(() -> autoActivationService.tryAutoActivate(1L));

        verify(stateMachineService).transition(1L, TemplateState.REVIEWED);
        verify(stateMachineService, never()).transition(1L, TemplateState.ACTIVE);
        verify(apiKeyService, never()).createApiKey(anyLong(), anyLong(), any());
    }


    @Test
    void tryAutoActivate_step2Fails_templateStaysReviewed_noException() {
        Template template = createTemplate(1L, 10L, "Test");
        when(stateMachineService.transition(1L, TemplateState.REVIEWED)).thenReturn(template);
        when(stateMachineService.transition(1L, TemplateState.ACTIVE))
                .thenThrow(new BusinessException(ErrorCode.TEMPLATE_INVALID_STATE_TRANSITION,
                        "Invalid transition", HttpStatus.BAD_REQUEST));

        assertDoesNotThrow(() -> autoActivationService.tryAutoActivate(1L));

        verify(stateMachineService).transition(1L, TemplateState.REVIEWED);
        verify(stateMachineService).transition(1L, TemplateState.ACTIVE);
        verify(apiKeyService, never()).createApiKey(anyLong(), anyLong(), any());
    }


    @Test
    void tryAutoActivate_apiKeyCreationFails_templateStaysActive_noException() {
        Template template = createTemplate(1L, 10L, "Test");
        when(stateMachineService.transition(1L, TemplateState.REVIEWED)).thenReturn(template);
        when(stateMachineService.transition(1L, TemplateState.ACTIVE)).thenReturn(template);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(apiKeyRepository.findByTenantIdOrderByCreatedAtDesc(10L)).thenReturn(Collections.emptyList());
        when(apiKeyService.createApiKey(eq(10L), eq(1L), any(CreateApiKeyRequest.class)))
                .thenThrow(new RuntimeException("DB error"));

        assertDoesNotThrow(() -> autoActivationService.tryAutoActivate(1L));

        verify(stateMachineService).transition(1L, TemplateState.REVIEWED);
        verify(stateMachineService).transition(1L, TemplateState.ACTIVE);
        verify(apiKeyService).createApiKey(eq(10L), eq(1L), any());
    }


    @Test
    void tryAutoActivate_apiKeyNameFormat_matchesPattern() {
        Template template = createTemplate(1L, 10L, "My Template!@#$%");
        when(stateMachineService.transition(1L, TemplateState.REVIEWED)).thenReturn(template);
        when(stateMachineService.transition(1L, TemplateState.ACTIVE)).thenReturn(template);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(apiKeyRepository.findByTenantIdOrderByCreatedAtDesc(10L)).thenReturn(Collections.emptyList());
        when(apiKeyService.createApiKey(eq(10L), eq(1L), any(CreateApiKeyRequest.class)))
                .thenReturn(new ApiKeyDTO());

        autoActivationService.tryAutoActivate(1L);

        ArgumentCaptor<CreateApiKeyRequest> captor = ArgumentCaptor.forClass(CreateApiKeyRequest.class);
        verify(apiKeyService).createApiKey(eq(10L), eq(1L), captor.capture());
        String keyName = captor.getValue().getName();
        // Format: auto-{sanitizedName}-{timestamp}
        assertTrue(keyName.matches("auto-My_Template_____-\\d+"),
                "Key name should match auto-{sanitized}-{timestamp} pattern: " + keyName);
    }


    @Test
    void tryAutoActivate_disabledKeyOnly_createsNewKey() {
        Template template = createTemplate(1L, 10L, "Test");
        when(stateMachineService.transition(1L, TemplateState.REVIEWED)).thenReturn(template);
        when(stateMachineService.transition(1L, TemplateState.ACTIVE)).thenReturn(template);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        ApiKey disabledKey = new ApiKey();
        disabledKey.setEnabled(false);
        when(apiKeyRepository.findByTenantIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(disabledKey));
        when(apiKeyService.createApiKey(eq(10L), eq(1L), any(CreateApiKeyRequest.class)))
                .thenReturn(new ApiKeyDTO());

        autoActivationService.tryAutoActivate(1L);

        verify(apiKeyService).createApiKey(eq(10L), eq(1L), any());
    }


    @Test
    void tryAutoActivate_expiredKeyOnly_createsNewKey() {
        Template template = createTemplate(1L, 10L, "Test");
        when(stateMachineService.transition(1L, TemplateState.REVIEWED)).thenReturn(template);
        when(stateMachineService.transition(1L, TemplateState.ACTIVE)).thenReturn(template);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        ApiKey expiredKey = new ApiKey();
        expiredKey.setEnabled(true);
        expiredKey.setExpiresAt(Instant.now().minusSeconds(3600)); // expired 1 hour ago
        when(apiKeyRepository.findByTenantIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(expiredKey));
        when(apiKeyService.createApiKey(eq(10L), eq(1L), any(CreateApiKeyRequest.class)))
                .thenReturn(new ApiKeyDTO());

        autoActivationService.tryAutoActivate(1L);

        verify(apiKeyService).createApiKey(eq(10L), eq(1L), any());
    }


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
}


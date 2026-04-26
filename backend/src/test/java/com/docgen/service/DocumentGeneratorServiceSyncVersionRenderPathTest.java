package com.docgen.service;

import com.docgen.dto.GenerateDocumentRequest;
import com.docgen.dto.GenerateDocumentResponse;
import com.docgen.entity.Template;
import com.docgen.repository.TemplateRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WS-05-T05: even when the sync API passes a validated {@code template_versions} number, SINGLE render
 * must still use {@link Template#getTemplateFilePath()} (WS-05-T04 contract).
 */
@ExtendWith(MockitoExtension.class)
class DocumentGeneratorServiceSyncVersionRenderPathTest {

    @Mock
    private TemplateRepository templateRepository;
    @Mock
    private ParameterValidationService parameterValidationService;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private DocumentStorageService documentStorageService;
    @Mock
    private CompositeGeneratorService compositeGeneratorService;

    private final CircuitBreaker circuitBreaker = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
            .circuitBreaker("test-sync-version-render");
    private final TemplateGenerationEligibilityService eligibility = new TemplateGenerationEligibilityService();

    @Test
    void generateDocument_withSyncValidatedVersion_stillRendersCurrentTemplateFilePath() {
        Template template = new Template();
        template.setId(40L);
        template.setStatus(TemplateGenerationEligibilityService.ACTIVE_TEMPLATE_STATUS);
        template.setTemplateType("SINGLE");
        template.setTemplateFilePath("templates/current-only.docx");
        template.setOutputFormat("WORD");
        template.setStorageStrategy("TEMP");
        when(templateRepository.findById(40L)).thenReturn(Optional.of(template));
        when(parameterValidationService.validateAndBuildContext(eq(40L), any())).thenReturn(Map.of("k", "v"));

        byte[] rendered = new byte[]{1, 2, 3};
        when(restTemplate.exchange(contains("/render"), eq(HttpMethod.POST), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(rendered, HttpStatus.OK));
        when(documentStorageService.store(any(), any(), anyString(), anyString()))
                .thenAnswer(inv -> {
                    GenerateDocumentResponse r = new GenerateDocumentResponse();
                    r.setDocumentId(1L);
                    return r;
                });

        DocumentGeneratorService svc = new DocumentGeneratorService(
                templateRepository,
                eligibility,
                parameterValidationService,
                restTemplate,
                documentStorageService,
                circuitBreaker,
                compositeGeneratorService);

        svc.generateDocument(40L, new GenerateDocumentRequest(), 999);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Map<String, Object>>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(contains("/render"), eq(HttpMethod.POST), entityCaptor.capture(), eq(byte[].class));
        Map<String, Object> body = entityCaptor.getValue().getBody();
        assertEquals("templates/current-only.docx", body.get("templatePath"));
    }
}

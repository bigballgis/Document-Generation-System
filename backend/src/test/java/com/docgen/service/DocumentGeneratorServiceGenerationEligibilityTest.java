package com.docgen.service;

import com.docgen.dto.GenerateDocumentRequest;
import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.TemplateRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentGeneratorServiceGenerationEligibilityTest {

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
            .circuitBreaker("test-docgen-eligibility");
    private final TemplateGenerationEligibilityService eligibility = new TemplateGenerationEligibilityService();

    @Test
    void generateDocument_rejectsNonActiveSingleTemplate() {
        Template template = new Template();
        template.setId(10L);
        template.setStatus("DRAFT");
        template.setTemplateType("SINGLE");
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));

        DocumentGeneratorService svc = new DocumentGeneratorService(
                templateRepository,
                eligibility,
                parameterValidationService,
                restTemplate,
                documentStorageService,
                circuitBreaker,
                compositeGeneratorService);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.generateDocument(10L, new GenerateDocumentRequest()));
        assertEquals(ErrorCode.TEMPLATE_NOT_FOUND, ex.getErrorCode());
        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
        verifyNoInteractions(compositeGeneratorService);
        verifyNoInteractions(parameterValidationService);
    }

    @Test
    void generateDocument_rejectsNonActiveCompositeBeforeRouting() {
        Template template = new Template();
        template.setId(11L);
        template.setStatus("REVIEWED");
        template.setTemplateType("COMPOSITE");
        when(templateRepository.findById(11L)).thenReturn(Optional.of(template));

        DocumentGeneratorService svc = new DocumentGeneratorService(
                templateRepository,
                eligibility,
                parameterValidationService,
                restTemplate,
                documentStorageService,
                circuitBreaker,
                compositeGeneratorService);

        assertThrows(BusinessException.class, () -> svc.generateDocument(11L, new GenerateDocumentRequest()));
        verifyNoInteractions(compositeGeneratorService);
    }
}

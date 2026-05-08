package com.docgen.service;

import com.docgen.dto.GenerateDocumentRequest;
import com.docgen.dto.GenerateDocumentResponse;
import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TemplateVersionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * WS-05-T01: characterizes {@link DynamicApiService#generateViaApi} eligibility (ACTIVE-only)
 * before version resolution and {@link DocumentGeneratorService} delegation.
 */
@ExtendWith(MockitoExtension.class)
class DynamicApiServiceGenerationEligibilityTest {

    @Mock
    private TemplateRepository templateRepository;
    @Mock
    private TemplateVersionRepository templateVersionRepository;
    @Mock
    private DocumentGeneratorService documentGeneratorService;

    @Test
    void generateViaApi_rejectsNonActiveBeforeVersionLookupAndGeneration() {
        Template template = new Template();
        template.setId(99L);
        template.setStatus("DRAFT");
        when(templateRepository.findById(99L)).thenReturn(Optional.of(template));

        DynamicApiService service = new DynamicApiService(
                templateRepository,
                templateVersionRepository,
                new TemplateGenerationEligibilityService(),
                documentGeneratorService);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateViaApi(99L, null, new GenerateDocumentRequest()));
        assertEquals(ErrorCode.TEMPLATE_NOT_FOUND, ex.getErrorCode());
        verifyNoInteractions(templateVersionRepository);
        verifyNoInteractions(documentGeneratorService);
    }

    @Test
    void generateViaApi_allowsActiveThenDelegatesWithNullVersion() {
        Template t = new Template();
        t.setId(1L);
        t.setStatus(TemplateGenerationEligibilityService.ACTIVE_TEMPLATE_STATUS);
        t.setAllowHistoryVersions(false);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(t));
        when(documentGeneratorService.generateDocument(eq(1L), any(), isNull()))
                .thenReturn(new GenerateDocumentResponse());

        DynamicApiService service = new DynamicApiService(
                templateRepository,
                templateVersionRepository,
                new TemplateGenerationEligibilityService(),
                documentGeneratorService);

        service.generateViaApi(1L, null, new GenerateDocumentRequest());

        verify(documentGeneratorService).generateDocument(eq(1L), any(), isNull());
        verifyNoInteractions(templateVersionRepository);
    }
}

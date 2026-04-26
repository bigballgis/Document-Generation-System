package com.docgen.service;

import com.docgen.dto.GenerateDocumentRequest;
import com.docgen.dto.GenerateDocumentResponse;
import com.docgen.entity.Template;
import com.docgen.entity.TemplateVersion;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TemplateVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * WS-05-T03: characterizes {@link DynamicApiService#generateViaApi} {@code version} query handling.
 * See audit note {@code docs/audits/full-project-review-2026-04-26/18-generate-api-version-parameter-behavior.md}.
 * {@link DocumentGeneratorService#generateDocument} is asserted indirectly: the API layer never forwards
 * {@code version} into the generation request.
 */
@ExtendWith(MockitoExtension.class)
class DynamicApiServiceVersionParameterTest {

    @Mock
    private TemplateRepository templateRepository;
    @Mock
    private TemplateVersionRepository templateVersionRepository;
    @Mock
    private TemplateGenerationEligibilityService templateGenerationEligibilityService;
    @Mock
    private DocumentGeneratorService documentGeneratorService;

    private DynamicApiService service;

    @BeforeEach
    void setUp() {
        service = new DynamicApiService(templateRepository, templateVersionRepository,
                templateGenerationEligibilityService, documentGeneratorService);
    }

    private static Template activeTemplate(long id) {
        Template t = new Template();
        t.setId(id);
        t.setStatus(TemplateGenerationEligibilityService.ACTIVE_TEMPLATE_STATUS);
        t.setAllowHistoryVersions(false);
        return t;
    }

    @Test
    void characterization_nullVersion_skipsTemplateVersionRepository() {
        Template t = activeTemplate(10L);
        when(templateRepository.findById(10L)).thenReturn(Optional.of(t));
        doNothing().when(templateGenerationEligibilityService).requireActiveForDocumentGeneration(any(), anyLong());
        when(documentGeneratorService.generateDocument(eq(10L), any(), isNull())).thenReturn(new GenerateDocumentResponse());

        service.generateViaApi(10L, null, new GenerateDocumentRequest());

        verifyNoInteractions(templateVersionRepository);
        verify(documentGeneratorService).generateDocument(eq(10L), any(), isNull());
    }

    @Test
    void generateViaApi_whenHistoryDisallowedAndRequestNotLatest_throwsBeforeGeneration() {
        Template t = activeTemplate(20L);
        t.setAllowHistoryVersions(false);
        when(templateRepository.findById(20L)).thenReturn(Optional.of(t));
        doNothing().when(templateGenerationEligibilityService).requireActiveForDocumentGeneration(any(), anyLong());
        when(templateVersionRepository.findMaxVersionNumber(20L)).thenReturn(Optional.of(5));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateViaApi(20L, 3, new GenerateDocumentRequest()));

        assertEquals(ErrorCode.GENERATE_VERSION_NOT_ALLOWED, ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        verify(documentGeneratorService, never()).generateDocument(anyLong(), any(), any());
        verify(templateVersionRepository, never()).findByTemplateIdOrderByVersionNumberDesc(anyLong());
    }

    @Test
    void generateViaApi_whenHistoryDisallowedButRequestIsLatest_proceeds() {
        Template t = activeTemplate(21L);
        t.setAllowHistoryVersions(false);
        when(templateRepository.findById(21L)).thenReturn(Optional.of(t));
        doNothing().when(templateGenerationEligibilityService).requireActiveForDocumentGeneration(any(), anyLong());
        when(templateVersionRepository.findMaxVersionNumber(21L)).thenReturn(Optional.of(5));
        TemplateVersion v5 = new TemplateVersion();
        v5.setVersionNumber(5);
        when(templateVersionRepository.findByTemplateIdOrderByVersionNumberDesc(21L)).thenReturn(List.of(v5));
        when(documentGeneratorService.generateDocument(eq(21L), any(), eq(5))).thenReturn(new GenerateDocumentResponse());

        service.generateViaApi(21L, 5, new GenerateDocumentRequest());

        verify(documentGeneratorService).generateDocument(eq(21L), any(), eq(5));
    }

    @Test
    void generateViaApi_whenHistoryAllowedAndVersionExists_proceeds() {
        Template t = activeTemplate(22L);
        t.setAllowHistoryVersions(true);
        when(templateRepository.findById(22L)).thenReturn(Optional.of(t));
        doNothing().when(templateGenerationEligibilityService).requireActiveForDocumentGeneration(any(), anyLong());
        when(templateVersionRepository.findMaxVersionNumber(22L)).thenReturn(Optional.of(4));
        TemplateVersion v4 = new TemplateVersion();
        v4.setVersionNumber(4);
        TemplateVersion v3 = new TemplateVersion();
        v3.setVersionNumber(3);
        when(templateVersionRepository.findByTemplateIdOrderByVersionNumberDesc(22L)).thenReturn(List.of(v4, v3));
        when(documentGeneratorService.generateDocument(eq(22L), any(), eq(3))).thenReturn(new GenerateDocumentResponse());

        service.generateViaApi(22L, 3, new GenerateDocumentRequest());

        verify(documentGeneratorService).generateDocument(eq(22L), any(), eq(3));
    }

    @Test
    void generateViaApi_whenVersionNotInRepository_throwsNotFound() {
        Template t = activeTemplate(23L);
        t.setAllowHistoryVersions(true);
        when(templateRepository.findById(23L)).thenReturn(Optional.of(t));
        doNothing().when(templateGenerationEligibilityService).requireActiveForDocumentGeneration(any(), anyLong());
        when(templateVersionRepository.findMaxVersionNumber(23L)).thenReturn(Optional.of(2));
        TemplateVersion v2 = new TemplateVersion();
        v2.setVersionNumber(2);
        when(templateVersionRepository.findByTemplateIdOrderByVersionNumberDesc(23L)).thenReturn(List.of(v2));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateViaApi(23L, 99, new GenerateDocumentRequest()));

        assertEquals(ErrorCode.TEMPLATE_VERSION_NOT_FOUND, ex.getErrorCode());
        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
        verify(documentGeneratorService, never()).generateDocument(anyLong(), any(), any());
    }

    @Test
    void characterization_versionDoesNotFlowIntoGenerateDocumentRequest() {
        Template t = activeTemplate(30L);
        t.setAllowHistoryVersions(true);
        when(templateRepository.findById(30L)).thenReturn(Optional.of(t));
        doNothing().when(templateGenerationEligibilityService).requireActiveForDocumentGeneration(any(), anyLong());
        when(templateVersionRepository.findMaxVersionNumber(30L)).thenReturn(Optional.of(7));
        TemplateVersion v7 = new TemplateVersion();
        v7.setVersionNumber(7);
        v7.setTemplateFilePath("version-snapshots/7/other.docx");
        when(templateVersionRepository.findByTemplateIdOrderByVersionNumberDesc(30L)).thenReturn(List.of(v7));

        GenerateDocumentRequest req = new GenerateDocumentRequest();
        when(documentGeneratorService.generateDocument(eq(30L), any(), eq(7))).thenReturn(new GenerateDocumentResponse());

        service.generateViaApi(30L, 7, req);

        ArgumentCaptor<GenerateDocumentRequest> captor = ArgumentCaptor.forClass(GenerateDocumentRequest.class);
        verify(documentGeneratorService).generateDocument(eq(30L), captor.capture(), eq(7));
        assertSame(req, captor.getValue());
    }
}

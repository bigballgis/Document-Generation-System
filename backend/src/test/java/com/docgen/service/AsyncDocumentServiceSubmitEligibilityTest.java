package com.docgen.service;

import com.docgen.dto.GenerateDocumentRequest;
import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.docgen.repository.AsyncTaskRepository;
import com.docgen.repository.TemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsyncDocumentServiceSubmitEligibilityTest {

    @Mock
    private AsyncTaskRepository asyncTaskRepository;
    @Mock
    private TemplateRepository templateRepository;
    @Mock
    private DocumentGeneratorService documentGeneratorService;
    @Mock
    private DocumentStorageService documentStorageService;

    @Test
    void submitAsyncGeneration_rejectsNonActiveBeforeTaskCreation() {
        Template template = new Template();
        template.setId(3L);
        template.setTenantId(1L);
        template.setStatus("DRAFT");
        when(templateRepository.findById(3L)).thenReturn(Optional.of(template));

        AsyncDocumentService svc = new AsyncDocumentService(
                asyncTaskRepository,
                templateRepository,
                new TemplateGenerationEligibilityService(),
                documentGeneratorService,
                documentStorageService);

        assertThrows(BusinessException.class,
                () -> svc.submitAsyncGeneration(3L, new GenerateDocumentRequest()));

        verify(asyncTaskRepository, never()).save(any());
        verifyNoInteractions(documentGeneratorService);
    }
}

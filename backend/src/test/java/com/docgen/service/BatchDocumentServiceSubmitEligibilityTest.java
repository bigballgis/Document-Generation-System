package com.docgen.service;

import com.docgen.dto.BatchGenerateRequest;
import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.docgen.repository.AsyncTaskRepository;
import com.docgen.repository.GeneratedDocumentRepository;
import com.docgen.repository.TemplateRepository;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchDocumentServiceSubmitEligibilityTest {

    @Mock
    private AsyncTaskRepository asyncTaskRepository;
    @Mock
    private TemplateRepository templateRepository;
    @Mock
    private GeneratedDocumentRepository documentRepository;
    @Mock
    private DocumentGeneratorService documentGeneratorService;
    @Mock
    private DocumentStorageService documentStorageService;
    @Mock
    private MinioClient minioClient;

    @Test
    void submitBatchGeneration_rejectsNonActiveBeforeTaskCreation() {
        Template template = new Template();
        template.setId(4L);
        template.setTenantId(1L);
        template.setStatus("DRAFT");
        when(templateRepository.findById(4L)).thenReturn(Optional.of(template));

        BatchDocumentService svc = new BatchDocumentService(
                asyncTaskRepository,
                templateRepository,
                new TemplateGenerationEligibilityService(),
                documentRepository,
                documentGeneratorService,
                documentStorageService,
                minioClient);

        BatchGenerateRequest request = new BatchGenerateRequest();
        request.setDataSets(List.of(Map.of("k", "v")));

        assertThrows(BusinessException.class, () -> svc.submitBatchGeneration(4L, request));

        verify(asyncTaskRepository, never()).save(any());
        verifyNoInteractions(documentGeneratorService);
    }
}

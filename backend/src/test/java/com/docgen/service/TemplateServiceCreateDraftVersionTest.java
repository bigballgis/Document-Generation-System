package com.docgen.service;

import com.docgen.dto.TemplateDTO;
import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TemplateTagMappingRepository;
import com.docgen.repository.TemplateVersionRepository;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemplateServiceCreateDraftVersionTest {

    @Mock
    private TemplateRepository templateRepository;
    @Mock
    private TemplateVersionRepository templateVersionRepository;
    @Mock
    private TemplateTagMappingRepository tagMappingRepository;
    @Mock
    private MinioClient minioClient;

    @InjectMocks
    private TemplateService templateService;

    private Template activeTemplate;

    @BeforeEach
    void setUp() {
        activeTemplate = new Template();
        activeTemplate.setId(1L);
        activeTemplate.setTenantId(100L);
        activeTemplate.setName("Test Template");
        activeTemplate.setStatus("ACTIVE");
        activeTemplate.setTemplateType("COMPOSITE");
        activeTemplate.setCreatedBy(10L);
    }

    @Test
    void createDraftVersion_activeTemplate_shouldSucceed() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(activeTemplate));
        when(templateVersionRepository.findMaxVersionNumber(1L)).thenReturn(Optional.of(3));
        when(templateRepository.save(any(Template.class))).thenReturn(activeTemplate);

        TemplateDTO result = templateService.createDraftVersion(1L, 10L);

        assertThat(result).isNotNull();
        assertThat(activeTemplate.getStatus()).isEqualTo("DRAFT");
        verify(templateVersionRepository).save(any());
        verify(templateRepository).save(activeTemplate);
    }

    @Test
    void createDraftVersion_draftTemplate_shouldThrow400() {
        activeTemplate.setStatus("DRAFT");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(activeTemplate));

        assertThatThrownBy(() -> templateService.createDraftVersion(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ACTIVE");
    }

    @Test
    void createDraftVersion_pendingReviewTemplate_shouldThrow400() {
        activeTemplate.setStatus("PENDING_REVIEW");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(activeTemplate));

        assertThatThrownBy(() -> templateService.createDraftVersion(1L, 10L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void createDraftVersion_reviewedTemplate_shouldThrow400() {
        activeTemplate.setStatus("REVIEWED");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(activeTemplate));

        assertThatThrownBy(() -> templateService.createDraftVersion(1L, 10L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void createDraftVersion_archivedTemplate_shouldThrow400() {
        activeTemplate.setStatus("ARCHIVED");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(activeTemplate));

        assertThatThrownBy(() -> templateService.createDraftVersion(1L, 10L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void createDraftVersion_nonExistentTemplate_shouldThrow404() {
        when(templateRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> templateService.createDraftVersion(999L, 10L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

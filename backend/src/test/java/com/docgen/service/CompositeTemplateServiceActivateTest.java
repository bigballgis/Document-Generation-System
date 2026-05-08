package com.docgen.service;

import com.docgen.dto.AssemblyConfigDTO;
import com.docgen.dto.AssemblySegmentEntry;
import com.docgen.dto.TemplateDTO;
import com.docgen.entity.Template;
import com.docgen.entity.TemplateState;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.TemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * WS-05-T06: composite activation must follow {@link TemplateStateMachineService} rules.
 */
@ExtendWith(MockitoExtension.class)
class CompositeTemplateServiceActivateTest {

    @Mock
    private TemplateRepository templateRepository;
    @Mock
    private AssemblyConfigService assemblyConfigService;
    @Mock
    private TemplateStateMachineService templateStateMachineService;

    private CompositeTemplateService service;

    @BeforeEach
    void setUp() {
        service = new CompositeTemplateService(templateRepository, assemblyConfigService, templateStateMachineService);
    }

    private static Template composite(long id, String status, boolean reviewRequired) {
        Template t = new Template();
        t.setId(id);
        t.setTenantId(1L);
        t.setTemplateType("COMPOSITE");
        t.setStatus(status);
        t.setReviewRequired(reviewRequired);
        t.setName("c");
        t.setCreatedAt(Instant.now());
        t.setUpdatedAt(Instant.now());
        return t;
    }

    private static AssemblyConfigDTO oneSegmentConfig() {
        AssemblySegmentEntry seg = new AssemblySegmentEntry();
        seg.setName("s1");
        seg.setEnabled(true);
        seg.setFilePath("segments/1/a.docx");
        AssemblyConfigDTO dto = new AssemblyConfigDTO();
        dto.setSegments(List.of(seg));
        return dto;
    }

    @Test
    void activate_fromDraftWithoutReview_callsStateMachine() {
        Template t = composite(10L, "DRAFT", false);
        t.setAssemblyConfig("{}");
        when(templateRepository.findById(10L)).thenReturn(Optional.of(t));
        when(assemblyConfigService.deserialize("{}")).thenReturn(oneSegmentConfig());
        Template active = composite(10L, "ACTIVE", false);
        active.setAssemblyConfig("{}");
        when(templateStateMachineService.transition(10L, TemplateState.ACTIVE)).thenReturn(active);

        TemplateDTO dto = service.activateCompositeTemplate(10L);

        assertEquals("ACTIVE", dto.getStatus());
        verify(templateStateMachineService).transition(10L, TemplateState.ACTIVE);
        verify(templateRepository, never()).save(any());
    }

    @Test
    void activate_fromReviewed_callsStateMachine() {
        Template t = composite(11L, "REVIEWED", true);
        t.setAssemblyConfig("{}");
        when(templateRepository.findById(11L)).thenReturn(Optional.of(t));
        when(assemblyConfigService.deserialize("{}")).thenReturn(oneSegmentConfig());
        Template active = composite(11L, "ACTIVE", true);
        when(templateStateMachineService.transition(11L, TemplateState.ACTIVE)).thenReturn(active);

        TemplateDTO dto = service.activateCompositeTemplate(11L);

        assertEquals("ACTIVE", dto.getStatus());
        verify(templateStateMachineService).transition(11L, TemplateState.ACTIVE);
    }

    @Test
    void activate_whenAlreadyActive_skipsStateMachine() {
        Template t = composite(12L, "ACTIVE", false);
        t.setAssemblyConfig("{}");
        when(templateRepository.findById(12L)).thenReturn(Optional.of(t));
        when(assemblyConfigService.deserialize("{}")).thenReturn(oneSegmentConfig());

        TemplateDTO dto = service.activateCompositeTemplate(12L);

        assertEquals("ACTIVE", dto.getStatus());
        verify(templateStateMachineService, never()).transition(anyLong(), any());
    }

    @Test
    void activate_whenDraftReviewRequired_propagatesStateMachineError() {
        Template t = composite(13L, "DRAFT", true);
        t.setAssemblyConfig("{}");
        when(templateRepository.findById(13L)).thenReturn(Optional.of(t));
        when(assemblyConfigService.deserialize("{}")).thenReturn(oneSegmentConfig());
        when(templateStateMachineService.transition(13L, TemplateState.ACTIVE))
                .thenThrow(new BusinessException(ErrorCode.TEMPLATE_REVIEW_REQUIRED,
                        "Review required", HttpStatus.BAD_REQUEST));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.activateCompositeTemplate(13L));
        assertEquals(ErrorCode.TEMPLATE_REVIEW_REQUIRED, ex.getErrorCode());
    }

    @Test
    void activate_whenNoSegments_doesNotCallStateMachine() {
        Template t = composite(14L, "DRAFT", false);
        t.setAssemblyConfig("{}");
        when(templateRepository.findById(14L)).thenReturn(Optional.of(t));
        AssemblyConfigDTO empty = new AssemblyConfigDTO();
        empty.setSegments(List.of());
        when(assemblyConfigService.deserialize("{}")).thenReturn(empty);

        assertThrows(BusinessException.class, () -> service.activateCompositeTemplate(14L));
        verify(templateStateMachineService, never()).transition(anyLong(), any());
    }
}

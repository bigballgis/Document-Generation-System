package com.docgen.service;

import com.docgen.entity.Template;
import com.docgen.entity.TemplateState;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.TemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemplateStateMachineServiceTest {

    @Mock
    private TemplateRepository templateRepository;

    private TemplateStateMachineService stateMachineService;

    @BeforeEach
    void setUp() {
        stateMachineService = new TemplateStateMachineService(templateRepository);
    }


    @Test
    void transition_draftToInTest_success() {
        Template template = createTemplate("DRAFT", true);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> inv.getArgument(0));

        Template result = stateMachineService.transition(1L, TemplateState.IN_TEST);

        assertEquals("IN_TEST", result.getStatus());
        verify(templateRepository).save(template);
    }

    @Test
    void transition_pendingReviewToReviewed_success() {
        Template template = createTemplate("PENDING_REVIEW", true);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> inv.getArgument(0));

        Template result = stateMachineService.transition(1L, TemplateState.REVIEWED);

        assertEquals("REVIEWED", result.getStatus());
    }

    @Test
    void transition_pendingReviewToInTest_success() {
        Template template = createTemplate("PENDING_REVIEW", true);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> inv.getArgument(0));

        Template result = stateMachineService.transition(1L, TemplateState.IN_TEST);

        assertEquals("IN_TEST", result.getStatus());
    }

    @Test
    void transition_inTestToPendingReview_success() {
        Template template = createTemplate("IN_TEST", true);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> inv.getArgument(0));

        Template result = stateMachineService.transition(1L, TemplateState.PENDING_REVIEW);

        assertEquals("PENDING_REVIEW", result.getStatus());
    }

    @Test
    void transition_inTestToDraft_success() {
        Template template = createTemplate("IN_TEST", true);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> inv.getArgument(0));

        Template result = stateMachineService.transition(1L, TemplateState.DRAFT);

        assertEquals("DRAFT", result.getStatus());
    }

    @Test
    void transition_reviewedToActive_success() {
        Template template = createTemplate("REVIEWED", true);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> inv.getArgument(0));

        Template result = stateMachineService.transition(1L, TemplateState.ACTIVE);

        assertEquals("ACTIVE", result.getStatus());
    }

    @Test
    void transition_activeToArchived_success() {
        Template template = createTemplate("ACTIVE", false);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> inv.getArgument(0));

        Template result = stateMachineService.transition(1L, TemplateState.ARCHIVED);

        assertEquals("ARCHIVED", result.getStatus());
    }

    @Test
    void transition_archivedToDraft_success() {
        Template template = createTemplate("ARCHIVED", false);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> inv.getArgument(0));

        Template result = stateMachineService.transition(1L, TemplateState.DRAFT);

        assertEquals("DRAFT", result.getStatus());
    }


    @Test
    void transition_draftToActive_noReviewRequired_success() {
        Template template = createTemplate("DRAFT", false);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> inv.getArgument(0));

        Template result = stateMachineService.transition(1L, TemplateState.ACTIVE);

        assertEquals("ACTIVE", result.getStatus());
    }


    @Test
    void transition_draftToActive_reviewRequired_throws() {
        Template template = createTemplate("DRAFT", true);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> stateMachineService.transition(1L, TemplateState.ACTIVE));

        assertTrue(ex.getMessage().contains("DRAFT"));
        assertTrue(ex.getMessage().contains("ACTIVE"));
    }


    @Test
    void transition_draftToArchived_throws() {
        Template template = createTemplate("DRAFT", false);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> stateMachineService.transition(1L, TemplateState.ARCHIVED));

        assertTrue(ex.getMessage().contains("DRAFT"));
        assertTrue(ex.getMessage().contains("ARCHIVED"));
    }

    @Test
    void transition_activeToReviewed_throws() {
        Template template = createTemplate("ACTIVE", false);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> stateMachineService.transition(1L, TemplateState.REVIEWED));

        assertTrue(ex.getMessage().contains("ACTIVE"));
        assertTrue(ex.getMessage().contains("REVIEWED"));
    }

    @Test
    void transition_archivedToActive_throws() {
        Template template = createTemplate("ARCHIVED", false);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> stateMachineService.transition(1L, TemplateState.ACTIVE));

        assertTrue(ex.getMessage().contains("ARCHIVED"));
        assertTrue(ex.getMessage().contains("ACTIVE"));
    }

    @Test
    void transition_reviewedToDraft_throws() {
        Template template = createTemplate("REVIEWED", false);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> stateMachineService.transition(1L, TemplateState.DRAFT));

        assertTrue(ex.getMessage().contains("REVIEWED"));
        assertTrue(ex.getMessage().contains("DRAFT"));
    }


    @Test
    void transition_templateNotFound_throws() {
        when(templateRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> stateMachineService.transition(99L, TemplateState.ACTIVE));
    }


    @Test
    void getAvailableTransitions_draft_reviewRequired() {
        Template template = createTemplate("DRAFT", true);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        List<TemplateState> transitions = stateMachineService.getAvailableTransitions(1L);

        assertTrue(transitions.contains(TemplateState.IN_TEST));
        assertFalse(transitions.contains(TemplateState.ACTIVE));
        assertFalse(transitions.contains(TemplateState.PENDING_REVIEW));
    }

    @Test
    void getAvailableTransitions_draft_noReviewRequired() {
        Template template = createTemplate("DRAFT", false);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        List<TemplateState> transitions = stateMachineService.getAvailableTransitions(1L);

        assertTrue(transitions.contains(TemplateState.IN_TEST));
        assertTrue(transitions.contains(TemplateState.ACTIVE));
    }

    @Test
    void getAvailableTransitions_inTest() {
        Template template = createTemplate("IN_TEST", true);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        List<TemplateState> transitions = stateMachineService.getAvailableTransitions(1L);

        assertTrue(transitions.contains(TemplateState.DRAFT));
        assertTrue(transitions.contains(TemplateState.PENDING_REVIEW));
        assertEquals(2, transitions.size());
    }

    @Test
    void getAvailableTransitions_pendingReview() {
        Template template = createTemplate("PENDING_REVIEW", true);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        List<TemplateState> transitions = stateMachineService.getAvailableTransitions(1L);

        assertTrue(transitions.contains(TemplateState.REVIEWED));
        assertTrue(transitions.contains(TemplateState.IN_TEST));
        assertEquals(2, transitions.size());
    }

    @Test
    void getAvailableTransitions_reviewed() {
        Template template = createTemplate("REVIEWED", true);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        List<TemplateState> transitions = stateMachineService.getAvailableTransitions(1L);

        assertEquals(1, transitions.size());
        assertTrue(transitions.contains(TemplateState.ACTIVE));
    }

    @Test
    void getAvailableTransitions_active() {
        Template template = createTemplate("ACTIVE", false);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        List<TemplateState> transitions = stateMachineService.getAvailableTransitions(1L);

        assertEquals(1, transitions.size());
        assertTrue(transitions.contains(TemplateState.ARCHIVED));
    }

    @Test
    void getAvailableTransitions_archived() {
        Template template = createTemplate("ARCHIVED", false);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        List<TemplateState> transitions = stateMachineService.getAvailableTransitions(1L);

        assertEquals(1, transitions.size());
        assertTrue(transitions.contains(TemplateState.DRAFT));
    }

    @Test
    void getAvailableTransitions_templateNotFound_throws() {
        when(templateRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> stateMachineService.getAvailableTransitions(99L));
    }


    private Template createTemplate(String status, boolean reviewRequired) {
        Template template = new Template();
        template.setId(1L);
        template.setTenantId(1L);
        template.setName("Test Template");
        template.setDescription("A test template");
        template.setTemplateFilePath("templates/1/uuid_test.docx");
        template.setOutputFormat("WORD");
        template.setStorageStrategy("TEMP");
        template.setCreatedBy(10L);
        template.setReviewRequired(reviewRequired);
        template.setStatus(status);
        template.setCreatedAt(Instant.now());
        template.setUpdatedAt(Instant.now());
        return template;
    }
}


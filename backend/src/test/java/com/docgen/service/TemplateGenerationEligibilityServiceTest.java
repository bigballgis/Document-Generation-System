package com.docgen.service;

import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class TemplateGenerationEligibilityServiceTest {

    private final TemplateGenerationEligibilityService service = new TemplateGenerationEligibilityService();

    @Test
    void requireActive_throwsForDraft() {
        Template t = new Template();
        t.setId(1L);
        t.setStatus("DRAFT");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.requireActiveForDocumentGeneration(t, 1L));
        assertEquals(ErrorCode.TEMPLATE_NOT_FOUND, ex.getErrorCode());
        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("ACTIVE"));
    }

    @Test
    void requireActive_passesForActive() {
        Template t = new Template();
        t.setId(2L);
        t.setStatus(TemplateGenerationEligibilityService.ACTIVE_TEMPLATE_STATUS);
        assertDoesNotThrow(() -> service.requireActiveForDocumentGeneration(t, 2L));
    }
}

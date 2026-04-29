package com.docgen.service;

import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DocumentGeneratorService#resolveSingleDocumentOutputFormat}
 * and {@link DocumentGeneratorService#rejectExplicitBothOutputFormat}.
 */
class DocumentGeneratorServiceOutputFormatResolutionTest {

    private static final long TEMPLATE_ID = 42L;

    @Test
    void explicitBothInRequestThrows() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> DocumentGeneratorService.resolveSingleDocumentOutputFormat(
                        "BOTH", "WORD", TEMPLATE_ID));
        assertEquals(ErrorCode.GENERATE_BOTH_NOT_SUPPORTED, ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    void explicitBothTrimmedThrows() {
        assertThrows(BusinessException.class,
                () -> DocumentGeneratorService.resolveSingleDocumentOutputFormat(
                        "  both  ", "PDF", TEMPLATE_ID));
    }

    @Test
    void explicitWordHonoredRegardlessOfTemplate() {
        assertEquals("WORD",
                DocumentGeneratorService.resolveSingleDocumentOutputFormat("WORD", "PDF", TEMPLATE_ID));
    }

    @Test
    void explicitPdfHonoredRegardlessOfTemplate() {
        assertEquals("PDF",
                DocumentGeneratorService.resolveSingleDocumentOutputFormat("PDF", "WORD", TEMPLATE_ID));
    }

    @Test
    void legacyTemplateBothDefaultsToWordWhenRequestOmitsFormat() {
        assertEquals("WORD",
                DocumentGeneratorService.resolveSingleDocumentOutputFormat(null, "BOTH", TEMPLATE_ID));
    }

    @Test
    void legacyTemplateBothDefaultsWhenRequestBlank() {
        assertEquals("WORD",
                DocumentGeneratorService.resolveSingleDocumentOutputFormat("   ", "both", TEMPLATE_ID));
    }

    @Test
    void templatePdfUsedWhenRequestOmitsFormat() {
        assertEquals("PDF",
                DocumentGeneratorService.resolveSingleDocumentOutputFormat(null, "PDF", TEMPLATE_ID));
    }

    @Test
    void templateWordUsedWhenRequestOmitsFormat() {
        assertEquals("WORD",
                DocumentGeneratorService.resolveSingleDocumentOutputFormat(null, "WORD", TEMPLATE_ID));
    }

    @Test
    void defaultsToWordWhenTemplateFormatMissing() {
        assertEquals("WORD",
                DocumentGeneratorService.resolveSingleDocumentOutputFormat(null, null, TEMPLATE_ID));
    }

    @Test
    void defaultsToWordWhenTemplateFormatBlank() {
        assertEquals("WORD",
                DocumentGeneratorService.resolveSingleDocumentOutputFormat(null, "   ", TEMPLATE_ID));
    }

    @Test
    void rejectExplicitBothStandaloneThrows() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> DocumentGeneratorService.rejectExplicitBothOutputFormat("BOTH"));
        assertEquals(ErrorCode.GENERATE_BOTH_NOT_SUPPORTED, ex.getErrorCode());
    }
}

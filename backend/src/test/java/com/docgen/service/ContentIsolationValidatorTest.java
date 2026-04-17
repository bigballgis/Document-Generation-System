package com.docgen.service;

import com.docgen.exception.BusinessException;
import com.docgen.util.BlankDocxGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ContentIsolationValidator}.
 * Uses {@link BlankDocxGenerator} to create test .docx files and verifies
 * that content isolation rules are correctly enforced.
 */
class ContentIsolationValidatorTest {

    private ContentIsolationValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ContentIsolationValidator();
    }

    // ── Passing cases: blank docx files pass their respective validations ──

    @Test
    void blankBody_passesBodyValidation() throws IOException {
        byte[] docx = BlankDocxGenerator.generateBlankBody();
        assertDoesNotThrow(() -> validator.validate(docx, "body"));
    }

    @Test
    void blankHeader_passesHeaderValidation() throws IOException {
        byte[] docx = BlankDocxGenerator.generateBlankHeader();
        assertDoesNotThrow(() -> validator.validate(docx, "header"));
    }

    @Test
    void blankFooter_passesFooterValidation() throws IOException {
        byte[] docx = BlankDocxGenerator.generateBlankFooter();
        assertDoesNotThrow(() -> validator.validate(docx, "footer"));
    }

    // ── Failing cases: docx with out-of-scope content fails validation ──

    @Test
    void bodyDocx_withHeaderContent_failsBodyValidation() throws IOException {
        byte[] docx = createBodyDocxWithHeaderContent();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.validate(docx, "body"));
        assertEquals("ONLYOFFICE_CONTENT_ISOLATION_VIOLATION", ex.getErrorCode());
    }

    @Test
    void headerDocx_withBodyContent_failsHeaderValidation() throws IOException {
        byte[] docx = createHeaderDocxWithBodyContent();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.validate(docx, "header"));
        assertEquals("ONLYOFFICE_CONTENT_ISOLATION_VIOLATION", ex.getErrorCode());
    }

    @Test
    void footerDocx_withBodyContent_failsFooterValidation() throws IOException {
        byte[] docx = createFooterDocxWithBodyContent();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.validate(docx, "footer"));
        assertEquals("ONLYOFFICE_CONTENT_ISOLATION_VIOLATION", ex.getErrorCode());
    }

    // ── Helpers: create .docx files with out-of-scope content ──

    /**
     * Creates a body .docx that also has non-empty header content (violation).
     */
    private byte[] createBodyDocxWithHeaderContent() throws IOException {
        String bodyXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + "<w:body><w:p><w:r><w:t>Body content</w:t></w:r></w:p></w:body>"
                + "</w:document>";
        String headerXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<w:hdr xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + "<w:p><w:r><w:t>Illegal header text</w:t></w:r></w:p>"
                + "</w:hdr>";

        return buildDocx(
                new DocxPart("word/document.xml", bodyXml),
                new DocxPart("word/header1.xml", headerXml)
        );
    }

    /**
     * Creates a header .docx where document.xml body has non-empty text (violation).
     */
    private byte[] createHeaderDocxWithBodyContent() throws IOException {
        String bodyXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + "<w:body><w:p><w:r><w:t>Illegal body text</w:t></w:r></w:p></w:body>"
                + "</w:document>";
        String headerXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<w:hdr xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + "<w:p><w:r><w:t>Header content</w:t></w:r></w:p>"
                + "</w:hdr>";

        return buildDocx(
                new DocxPart("word/document.xml", bodyXml),
                new DocxPart("word/header1.xml", headerXml)
        );
    }

    /**
     * Creates a footer .docx where document.xml body has non-empty text (violation).
     */
    private byte[] createFooterDocxWithBodyContent() throws IOException {
        String bodyXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + "<w:body><w:p><w:r><w:t>Illegal body text</w:t></w:r></w:p></w:body>"
                + "</w:document>";
        String footerXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<w:ftr xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + "<w:p><w:r><w:t>Footer content</w:t></w:r></w:p>"
                + "</w:ftr>";

        return buildDocx(
                new DocxPart("word/document.xml", bodyXml),
                new DocxPart("word/footer1.xml", footerXml)
        );
    }

    private record DocxPart(String entryName, String content) {}

    private byte[] buildDocx(DocxPart... parts) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (DocxPart part : parts) {
                zos.putNextEntry(new ZipEntry(part.entryName()));
                zos.write(part.content().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
}

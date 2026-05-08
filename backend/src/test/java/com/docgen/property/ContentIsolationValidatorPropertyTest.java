package com.docgen.property;

import com.docgen.exception.BusinessException;
import com.docgen.service.ContentIsolationValidator;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test for ContentIsolationValidator — Property 9: Content isolation validation completeness.
 *
 * <p><b>Feature: design-stage-layout, Property 9: content isolation validation completeness</b></p>
 * <p><b>Validates: Requirements 6.4, 6.5, 6.6</b></p>
 *
 * <p>For any .docx file and expected content type (body/header/footer),
 * the validator passes iff non-expected areas contain no text content.</p>
 */
@Tag("feature-design-stage-layout-property-9-content-isolation-validation-completeness")
class ContentIsolationValidatorPropertyTest {

    private final ContentIsolationValidator validator = new ContentIsolationValidator();


    private static final String EMPTY_BODY_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
            + "<w:body><w:p><w:r><w:t></w:t></w:r></w:p></w:body>"
            + "</w:document>";

    private static final String CONTENT_BODY_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
            + "<w:body><w:p><w:r><w:t>Some body content</w:t></w:r></w:p></w:body>"
            + "</w:document>";

    private static final String EMPTY_HEADER_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<w:hdr xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
            + "<w:p><w:r><w:t></w:t></w:r></w:p>"
            + "</w:hdr>";

    private static final String CONTENT_HEADER_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<w:hdr xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
            + "<w:p><w:r><w:t>Header text</w:t></w:r></w:p>"
            + "</w:hdr>";

    private static final String EMPTY_FOOTER_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<w:ftr xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
            + "<w:p><w:r><w:t></w:t></w:r></w:p>"
            + "</w:ftr>";

    private static final String CONTENT_FOOTER_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<w:ftr xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
            + "<w:p><w:r><w:t>Footer text</w:t></w:r></w:p>"
            + "</w:ftr>";

    private static final String CONTENT_TYPES_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
            + "</Types>";

    private static final String RELS_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
            + "</Relationships>";


    /**
     * Property 9: For expectedContentType="body", validation passes iff header and footer are empty.
     */
    @Property(tries = 100)
    void bodyTypePassesOnlyWhenHeaderAndFooterEmpty(
            @ForAll boolean hasBodyContent,
            @ForAll boolean hasHeaderContent,
            @ForAll boolean hasFooterContent,
            @ForAll @IntRange(min = 1, max = 3) int headerCount,
            @ForAll @IntRange(min = 1, max = 3) int footerCount
    ) throws IOException {
        byte[] docx = buildDocx(hasBodyContent, hasHeaderContent, hasFooterContent, headerCount, footerCount);
        boolean shouldPass = !hasHeaderContent && !hasFooterContent;

        if (shouldPass) {
            assertDoesNotThrow(() -> validator.validate(docx, "body"),
                    "body type should pass when header/footer are empty");
        } else {
            assertThrows(BusinessException.class, () -> validator.validate(docx, "body"),
                    "body type should reject when header or footer has content");
        }
    }

    /**
     * Property 9: For expectedContentType="header", validation passes iff body and footer are empty.
     */
    @Property(tries = 100)
    void headerTypePassesOnlyWhenBodyAndFooterEmpty(
            @ForAll boolean hasBodyContent,
            @ForAll boolean hasHeaderContent,
            @ForAll boolean hasFooterContent,
            @ForAll @IntRange(min = 1, max = 3) int headerCount,
            @ForAll @IntRange(min = 1, max = 3) int footerCount
    ) throws IOException {
        byte[] docx = buildDocx(hasBodyContent, hasHeaderContent, hasFooterContent, headerCount, footerCount);
        boolean shouldPass = !hasBodyContent && !hasFooterContent;

        if (shouldPass) {
            assertDoesNotThrow(() -> validator.validate(docx, "header"),
                    "header type should pass when body/footer are empty");
        } else {
            assertThrows(BusinessException.class, () -> validator.validate(docx, "header"),
                    "header type should reject when body or footer has content");
        }
    }

    /**
     * Property 9: For expectedContentType="footer", validation passes iff body and header are empty.
     */
    @Property(tries = 100)
    void footerTypePassesOnlyWhenBodyAndHeaderEmpty(
            @ForAll boolean hasBodyContent,
            @ForAll boolean hasHeaderContent,
            @ForAll boolean hasFooterContent,
            @ForAll @IntRange(min = 1, max = 3) int headerCount,
            @ForAll @IntRange(min = 1, max = 3) int footerCount
    ) throws IOException {
        byte[] docx = buildDocx(hasBodyContent, hasHeaderContent, hasFooterContent, headerCount, footerCount);
        boolean shouldPass = !hasBodyContent && !hasHeaderContent;

        if (shouldPass) {
            assertDoesNotThrow(() -> validator.validate(docx, "footer"),
                    "footer type should pass when body/header are empty");
        } else {
            assertThrows(BusinessException.class, () -> validator.validate(docx, "footer"),
                    "footer type should reject when body or header has content");
        }
    }

    /**
     * Property 9: Validation is idempotent — same result on repeated calls.
     */
    @Property(tries = 100)
    void validationIsIdempotent(
            @ForAll boolean hasBodyContent,
            @ForAll boolean hasHeaderContent,
            @ForAll boolean hasFooterContent,
            @ForAll("contentType") String contentType
    ) throws IOException {
        byte[] docx = buildDocx(hasBodyContent, hasHeaderContent, hasFooterContent, 1, 1);

        // Run twice, expect same outcome
        Throwable first = null;
        Throwable second = null;
        try { validator.validate(docx, contentType); } catch (Throwable t) { first = t; }
        try { validator.validate(docx, contentType); } catch (Throwable t) { second = t; }

        assertEquals(first == null, second == null,
                "Validation should be idempotent: both calls should pass or both should fail");
    }


    @Provide
    Arbitrary<String> contentType() {
        return Arbitraries.of("body", "header", "footer");
    }


    private byte[] buildDocx(
            boolean hasBodyContent,
            boolean hasHeaderContent,
            boolean hasFooterContent,
            int headerCount,
            int footerCount
    ) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            addEntry(zos, "[Content_Types].xml", CONTENT_TYPES_XML);
            addEntry(zos, "_rels/.rels", RELS_XML);
            addEntry(zos, "word/document.xml", hasBodyContent ? CONTENT_BODY_XML : EMPTY_BODY_XML);

            for (int i = 1; i <= headerCount; i++) {
                addEntry(zos, "word/header" + i + ".xml",
                        hasHeaderContent ? CONTENT_HEADER_XML : EMPTY_HEADER_XML);
            }
            for (int i = 1; i <= footerCount; i++) {
                addEntry(zos, "word/footer" + i + ".xml",
                        hasFooterContent ? CONTENT_FOOTER_XML : EMPTY_FOOTER_XML);
            }
        }
        return baos.toByteArray();
    }

    private void addEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }
}

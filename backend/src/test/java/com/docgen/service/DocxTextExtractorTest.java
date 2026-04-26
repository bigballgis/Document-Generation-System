package com.docgen.service;

import com.docgen.dto.ExtractedText;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import okhttp3.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DocxTextExtractor} using in-memory .docx ZIP payloads (WS-04-T02, WS-04-T05).
 */
@ExtendWith(MockitoExtension.class)
class DocxTextExtractorTest {

    private static final String NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    @Mock
    private MinioClient minioClient;

    private DocxTextExtractor extractor;

    @BeforeEach
    void setUp() throws Exception {
        extractor = new DocxTextExtractor(minioClient);
        Field bucketField = DocxTextExtractor.class.getDeclaredField("bucketName");
        bucketField.setAccessible(true);
        bucketField.set(extractor, "docgen-test");
    }

    @Test
    void extractText_validSingleParagraph_returnsPlainText() throws Exception {
        byte[] docx = minimalDocx(body(singleParagraph("Hello")));

        ExtractedText out = extractor.extractText(new ByteArrayInputStream(docx), 512_000);

        assertEquals("Hello", out.text());
        assertFalse(out.truncated());
    }

    @Test
    void extractText_multipleParagraphs_joinsWithNewlines() throws Exception {
        String innerBody = singleParagraph("First") + singleParagraph("Second");
        byte[] docx = minimalDocx(body(innerBody));

        ExtractedText out = extractor.extractText(new ByteArrayInputStream(docx), 512_000);

        assertEquals("First\nSecond", out.text());
    }

    @Test
    void extractText_tabInsideParagraph_preservesTab() throws Exception {
        String p = "<w:p><w:r><w:t>Left</w:t></w:r><w:r><w:tab/></w:r><w:r><w:t>Right</w:t></w:r></w:p>";
        byte[] docx = minimalDocx(body(p));

        ExtractedText out = extractor.extractText(new ByteArrayInputStream(docx), 512_000);

        assertEquals("Left\tRight", out.text());
    }

    @Test
    void extractText_missingWordDocumentXml_throwsBusinessException() throws Exception {
        byte[] docx = zipWithSingleEntry("word/styles.xml", "<xml/>".getBytes(StandardCharsets.UTF_8));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> extractor.extractText(new ByteArrayInputStream(docx), 512_000));

        assertEquals(ErrorCode.CONTENT_DIFF_EXTRACTION_FAILED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("word/document.xml"));
    }

    @Test
    void extractText_invalidZipInput_throwsBusinessException() {
        byte[] noise = new byte[]{0x00, 0x01, 0x02, 0x03};

        BusinessException ex = assertThrows(BusinessException.class,
                () -> extractor.extractText(new ByteArrayInputStream(noise), 512_000));

        assertEquals(ErrorCode.CONTENT_DIFF_EXTRACTION_FAILED, ex.getErrorCode());
    }

    @Test
    void extractText_truncatesWhenMaxBytesSmall_setsTruncatedFlag() throws Exception {
        String longWord = "x".repeat(10_000);
        byte[] docx = minimalDocx(body(singleParagraph(longWord)));

        ExtractedText out = extractor.extractText(new ByteArrayInputStream(docx), 256);

        assertTrue(out.truncated());
        assertTrue(out.text().endsWith("[... content truncated ...]"));
        assertTrue(out.text().getBytes(StandardCharsets.UTF_8).length <= 256 + "[... content truncated ...]".getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void extractText_corruptWordDocumentXml_throwsBusinessException() throws Exception {
        byte[] docx = minimalDocx("<<<not-xml");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> extractor.extractText(new ByteArrayInputStream(docx), 512_000));

        assertEquals(ErrorCode.CONTENT_DIFF_EXTRACTION_FAILED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("word/document.xml"));
    }

    /** DOCTYPE is rejected (XXE hardening); main part must parse or extraction fails. */
    @Test
    void extractText_documentXmlWithDoctype_throwsBusinessException() throws Exception {
        String withDoctype = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><!DOCTYPE root [<!ELEMENT root ANY>]>"
                + body("");
        byte[] docx = minimalDocx(withDoctype);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> extractor.extractText(new ByteArrayInputStream(docx), 512_000));

        assertEquals(ErrorCode.CONTENT_DIFF_EXTRACTION_FAILED, ex.getErrorCode());
    }

    @Test
    void extractText_corruptHeaderXml_documentStillExtracted() throws Exception {
        byte[] docx = zipDocumentAndExtra(
                body(singleParagraph("BodyOnly")),
                "word/header1.xml",
                "<<<invalid".getBytes(StandardCharsets.UTF_8));

        ExtractedText out = extractor.extractText(new ByteArrayInputStream(docx), 512_000);

        assertEquals("BodyOnly", out.text());
        assertFalse(out.truncated());
    }

    @Test
    void extractTextFromMinio_delegatesToGetObject() throws Exception {
        byte[] docx = minimalDocx(body(singleParagraph("FromMinio")));
        GetObjectResponse response = new GetObjectResponse(
                Headers.of(), "docgen-test", "", "",
                new ByteArrayInputStream(docx));
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(response);

        ExtractedText out = extractor.extractTextFromMinio("segments/1/a.docx");

        assertEquals("FromMinio", out.text());
        verify(minioClient).getObject(any(GetObjectArgs.class));
    }

    // ── helpers ──

    private static String singleParagraph(String text) {
        // Namespace bound once on w:document; avoid repeated xmlns:w on sibling w:p (some SAX stacks reject it).
        return "<w:p><w:r><w:t>" + text + "</w:t></w:r></w:p>";
    }

    private static String body(String innerParagraphs) {
        return "<w:document xmlns:w=\"" + NS + "\"><w:body>" + innerParagraphs + "</w:body></w:document>";
    }

    private static byte[] minimalDocx(String documentXml) throws Exception {
        return zipWithSingleEntry("word/document.xml", documentXml.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] zipWithSingleEntry(String path, byte[] content) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(path));
            zos.write(content);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    /** Two ZIP entries: {@code word/document.xml} then an extra part (e.g. header). */
    private static byte[] zipDocumentAndExtra(String documentXmlUtf8, String extraPath, byte[] extraContent)
            throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("word/document.xml"));
            zos.write(documentXmlUtf8.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry(extraPath));
            zos.write(extraContent);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}

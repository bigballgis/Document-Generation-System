package com.docgen.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Generates minimal valid .docx files for blank segments, headers, and footers.
 * A .docx file is a ZIP archive containing XML files following the Open XML format.
 */
public final class BlankDocxGenerator {

    private BlankDocxGenerator() {}

    private static final String CONTENT_TYPES_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
            + "</Types>";

    private static final String RELS_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
            + "</Relationships>";

    private static final String DOCUMENT_RELS_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "</Relationships>";

    private static final String BLANK_BODY_DOCUMENT_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
            + "<w:body><w:p><w:r><w:t></w:t></w:r></w:p></w:body>"
            + "</w:document>";

    private static final String HEADER_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<w:hdr xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
            + "<w:p><w:r><w:t></w:t></w:r></w:p>"
            + "</w:hdr>";

    private static final String FOOTER_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<w:ftr xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
            + "<w:p><w:r><w:t></w:t></w:r></w:p>"
            + "</w:ftr>";

    private static final String CONTENT_TYPES_WITH_HEADER_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
            + "<Override PartName=\"/word/header1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml\"/>"
            + "</Types>";

    private static final String CONTENT_TYPES_WITH_FOOTER_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
            + "<Override PartName=\"/word/footer1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml\"/>"
            + "</Types>";

    private static final String DOCUMENT_RELS_WITH_HEADER_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/header\" Target=\"header1.xml\"/>"
            + "</Relationships>";

    private static final String DOCUMENT_RELS_WITH_FOOTER_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer\" Target=\"footer1.xml\"/>"
            + "</Relationships>";

    private static final String EMPTY_BODY_WITH_HEADER_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
            + "<w:body><w:sectPr>"
            + "<w:headerReference w:type=\"default\" r:id=\"rId1\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"/>"
            + "</w:sectPr></w:body>"
            + "</w:document>";

    private static final String EMPTY_BODY_WITH_FOOTER_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
            + "<w:body><w:sectPr>"
            + "<w:footerReference w:type=\"default\" r:id=\"rId1\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"/>"
            + "</w:sectPr></w:body>"
            + "</w:document>";

    /**
     * Generate a blank body-only .docx file (no header/footer areas).
     * Used as the template for new content segments.
     */
    public static byte[] generateBlankBody() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            addEntry(zos, "[Content_Types].xml", CONTENT_TYPES_XML);
            addEntry(zos, "_rels/.rels", RELS_XML);
            addEntry(zos, "word/_rels/document.xml.rels", DOCUMENT_RELS_XML);
            addEntry(zos, "word/document.xml", BLANK_BODY_DOCUMENT_XML);
        }
        return baos.toByteArray();
    }

    /**
     * Generate a blank header-only .docx file (empty body, header area present).
     * Used as the template for header control nodes.
     */
    public static byte[] generateBlankHeader() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            addEntry(zos, "[Content_Types].xml", CONTENT_TYPES_WITH_HEADER_XML);
            addEntry(zos, "_rels/.rels", RELS_XML);
            addEntry(zos, "word/_rels/document.xml.rels", DOCUMENT_RELS_WITH_HEADER_XML);
            addEntry(zos, "word/document.xml", EMPTY_BODY_WITH_HEADER_XML);
            addEntry(zos, "word/header1.xml", HEADER_XML);
        }
        return baos.toByteArray();
    }

    /**
     * Generate a blank footer-only .docx file (empty body, footer area present).
     * Used as the template for footer control nodes.
     */
    public static byte[] generateBlankFooter() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            addEntry(zos, "[Content_Types].xml", CONTENT_TYPES_WITH_FOOTER_XML);
            addEntry(zos, "_rels/.rels", RELS_XML);
            addEntry(zos, "word/_rels/document.xml.rels", DOCUMENT_RELS_WITH_FOOTER_XML);
            addEntry(zos, "word/document.xml", EMPTY_BODY_WITH_FOOTER_XML);
            addEntry(zos, "word/footer1.xml", FOOTER_XML);
        }
        return baos.toByteArray();
    }

    private static void addEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }
}

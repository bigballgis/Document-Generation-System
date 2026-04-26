package com.docgen.service;

import com.docgen.dto.ExtractedText;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts plain text from .docx files using JDK ZipInputStream + SAXParser.
 * No Apache POI dependency required.
 */
@Component
public class DocxTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(DocxTextExtractor.class);
    private static final String TRUNCATION_MARKER = "[... content truncated ...]";
    private static final int DEFAULT_MAX_BYTES = 512_000; // 500KB

    private final MinioClient minioClient;

    @Value("${minio.bucket-name:docgen}")
    private String bucketName;

    public DocxTextExtractor(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    /**
     * Extract plain text from a .docx input stream.
     *
     * @param docxStream the .docx file input stream
     * @param maxBytes   maximum bytes to extract before truncation
     * @return extracted text result
     */
    public ExtractedText extractText(InputStream docxStream, int maxBytes) {
        if (maxBytes <= 0) {
            maxBytes = DEFAULT_MAX_BYTES;
        }

        StringBuilder result = new StringBuilder();
        boolean truncated = false;
        boolean foundDocumentXml = false;
        int estimatedBytes = 0;

        try (ZipInputStream zis = new ZipInputStream(docxStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (isRelevantEntry(name)) {
                    if ("word/document.xml".equals(name)) {
                        foundDocumentXml = true;
                    }
                    byte[] xmlBytes = readAllBytes(zis);
                    String partText = parseXmlText(xmlBytes);
                    if (!partText.isEmpty()) {
                        if (result.length() > 0 && result.charAt(result.length() - 1) != '\n') {
                            result.append('\n');
                            estimatedBytes++;
                        }
                        result.append(partText);
                        // Estimate UTF-8 byte length incrementally (avoid O(n²) toString+getBytes)
                        estimatedBytes += partText.getBytes(StandardCharsets.UTF_8).length;
                    }

                    if (estimatedBytes > maxBytes) {
                        truncated = true;
                        zis.closeEntry();
                        break;
                    }
                }
                zis.closeEntry();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.CONTENT_DIFF_EXTRACTION_FAILED,
                    "Failed to extract text from .docx file: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR, e);
        }

        if (!foundDocumentXml) {
            throw new BusinessException(ErrorCode.CONTENT_DIFF_EXTRACTION_FAILED,
                    "Invalid .docx file: word/document.xml not found",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        String text = result.toString();
        if (truncated) {
            // Truncate to maxBytes boundary, ensuring we don't cut a multi-byte UTF-8 character
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > maxBytes) {
                // Walk back from maxBytes to find a valid UTF-8 boundary
                int truncateAt = maxBytes;
                while (truncateAt > 0 && (bytes[truncateAt] & 0xC0) == 0x80) {
                    truncateAt--;
                }
                text = new String(bytes, 0, truncateAt, StandardCharsets.UTF_8);
            }
            text = text + TRUNCATION_MARKER;
        }

        return new ExtractedText(text, truncated);
    }

    /**
     * Extract plain text from a .docx file stored in MinIO.
     *
     * @param filePath the file path in MinIO
     * @return extracted text result
     */
    public ExtractedText extractTextFromMinio(String filePath) {
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(filePath)
                        .build())) {
            return extractText(stream, DEFAULT_MAX_BYTES);
        } catch (BusinessException e) {
            throw e;
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                throw new BusinessException(ErrorCode.SEGMENT_FILE_NOT_FOUND,
                        "File not found in MinIO: " + filePath,
                        HttpStatus.NOT_FOUND, e);
            }
            throw new BusinessException(ErrorCode.CONTENT_DIFF_EXTRACTION_FAILED,
                    "Failed to read file from MinIO: " + filePath,
                    HttpStatus.INTERNAL_SERVER_ERROR, e);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.CONTENT_DIFF_EXTRACTION_FAILED,
                    "Failed to read file from MinIO: " + filePath,
                    HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    private boolean isRelevantEntry(String name) {
        return "word/document.xml".equals(name)
                || (name.startsWith("word/header") && name.endsWith(".xml"))
                || (name.startsWith("word/footer") && name.endsWith(".xml"));
    }

    /** Maximum size for a single XML entry to prevent ZIP bomb attacks */
    private static final int MAX_XML_ENTRY_BYTES = 10 * 1024 * 1024; // 10MB

    private byte[] readAllBytes(InputStream is) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        int totalRead = 0;
        while ((len = is.read(buffer)) != -1) {
            totalRead += len;
            if (totalRead > MAX_XML_ENTRY_BYTES) {
                log.warn("ZIP entry exceeds {}MB limit, truncating", MAX_XML_ENTRY_BYTES / 1024 / 1024);
                break;
            }
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }

    private String parseXmlText(byte[] xmlBytes) {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            // XXE protection
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            SAXParser parser = factory.newSAXParser();
            DocxTextHandler handler = new DocxTextHandler();
            parser.parse(new ByteArrayInputStream(xmlBytes), handler);
            return handler.getText();
        } catch (Exception e) {
            log.warn("Failed to parse XML content: {}", e.getMessage());
            return "";
        }
    }

    /**
     * SAX handler that extracts text from OpenXML w:p/w:t elements.
     * Handles w:tab (tab character) and w:br (line break) elements.
     * Uses depth counter for w:p to handle edge cases safely.
     */
    private static class DocxTextHandler extends DefaultHandler {
        private final StringBuilder result = new StringBuilder();
        private final StringBuilder paragraphBuffer = new StringBuilder();
        private int paragraphDepth = 0;
        private boolean inText = false;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if ("w:p".equals(qName)) {
                if (paragraphDepth == 0) {
                    paragraphBuffer.setLength(0);
                }
                paragraphDepth++;
            } else if ("w:t".equals(qName) && paragraphDepth > 0) {
                inText = true;
            } else if ("w:tab".equals(qName) && paragraphDepth > 0) {
                paragraphBuffer.append('\t');
            } else if ("w:br".equals(qName) && paragraphDepth > 0) {
                // Flush current paragraph and start a new one for line breaks within a paragraph
                if (paragraphBuffer.length() > 0) {
                    if (result.length() > 0) {
                        result.append('\n');
                    }
                    result.append(paragraphBuffer);
                    paragraphBuffer.setLength(0);
                }
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inText && paragraphDepth > 0) {
                paragraphBuffer.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if ("w:t".equals(qName)) {
                inText = false;
            } else if ("w:p".equals(qName)) {
                paragraphDepth--;
                if (paragraphDepth == 0 && paragraphBuffer.length() > 0) {
                    if (result.length() > 0) {
                        result.append('\n');
                    }
                    result.append(paragraphBuffer);
                    paragraphBuffer.setLength(0);
                }
            }
        }

        public String getText() {
            return result.toString();
        }
    }
}
